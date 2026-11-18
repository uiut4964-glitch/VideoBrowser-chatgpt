package com.example.videobrowser

import android.media.MediaMetadataRetriever
import android.webkit.CookieManager
import java.io.IOException
import java.util.Locale

object Formats {
    private val exts = setOf("mp4", "m4v", "webm", "mkv", "mov", "flv", "3gp", "mp3", "m4a", "aac", "ogg", "wav")

    class Info(val duration: Double, val bytes: Long, val w: Int, val h: Int)

    fun isAudio(ext: String): Boolean = ext in setOf("mp3", "m4a", "aac", "ogg", "wav")

    fun isAdUrl(raw: String): Boolean {
        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        val lower = decoded.lowercase(Locale.ROOT)
        val adHosts = setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adsrvr.org", "taboola.com", "outbrain.com", "criteo.com",
            "pubmatic.com", "rubiconproject.com", "openx.net", "moatads.com",
            "zedo.com", "adnxs.com", "spotx.tv", "freewheel.tv"
        )
        val host = try { java.net.URI(decoded).host?.lowercase(Locale.ROOT).orEmpty() } catch (_: Exception) { "" }
        if (host.isNotEmpty() && adHosts.any { host == it || host.endsWith("." + it) }) return true
        if (listOf(
                "doubleclick", "googlesyndication", "googleadservices", "adservice", "adserver",
                "imasdk", "vasttag", "spotx", "freewheel", "adnxs", "adsystem", "advertising"
            ).any { lower.contains(it) }) return true
        val pathOrQuery = try {
            val uri = java.net.URI(decoded)
            (uri.rawPath.orEmpty() + "?" + uri.rawQuery.orEmpty()).lowercase(Locale.ROOT)
        } catch (_: Exception) { lower }
        val pattern = Regex("(?:^|[/_.?&=\\-])(ads?|advert|advertisement|preroll|midroll|postroll|vast|vmap)(?:[/_.?&=\\-]|$)")
        return pattern.containsMatchIn(pathOrQuery)
    }

    fun isPreviewUrl(raw: String): Boolean {
        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        val pathAndQuery = try {
            val uri = java.net.URI(decoded)
            (uri.rawPath.orEmpty() + "?" + uri.rawQuery.orEmpty()).lowercase(Locale.ROOT)
        } catch (_: Exception) { decoded.lowercase(Locale.ROOT) }
        val tokens = Regex("(?:^|[/_.?&=\\-])(preview|previews|thumbnail|thumb|thumbs|poster|trailer|trailers|teaser|teasers|storyboard|sprite)(?:[/_.?&=\\-]|$)")
        return tokens.containsMatchIn(pathAndQuery)
    }

    fun extOf(url: String, mime: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val e = path.substringAfterLast('.', "").lowercase()
        if (e in exts) return e
        return when {
            mime.contains("webm") -> "webm"
            mime == "audio/mpeg" -> "mp3"
            mime.startsWith("audio/") -> "m4a"
            else -> "mp4"
        }
    }

    /** اسم الجودة بالأسلوب المعتاد: 720P HD ... */
    fun qualityLabel(w: Int, h: Int): String {
        // اعرض الدقة الفعلية ولا ترفعها اصطناعيًا إلى 2K/4K.
        val shortSide = minOf(w, h)
        return when (shortSide) {
            in 2100..2300 -> "2160P 4K"
            in 1380..1500 -> "1440P 2K"
            in 1040..1120 -> "1080P HD"
            in 680..760 -> "720P HD"
            else -> shortSide.toString() + "P"
        }
    }

    private fun fmtEst(bytes: Long): String = if (bytes > 0) "≈ " + Util.fmtSize(bytes) else ""

    /** يقرأ قائمة المقاطع، ويقدّر الحجم من أول مقطع، ويستخرج الأبعاد إن طُلب */
    private fun mediaInfo(url: String, h: Map<String, String>, wantRes: Boolean): Info {
        val m = Hls.parseMedia(Net.fetchText(url, h), url)
        if (m.segs.isEmpty()) throw IOException("empty")
        val s0 = m.segs[0]
        var data = if (s0.rangeStart != null && s0.rangeLength != null)
            Net.readRangeBytes(s0.url, h, s0.rangeStart, s0.rangeLength)
        else Net.readBytes(s0.url, h)
        val size0 = data.size
        var w = 0
        var hh = 0
        if (wantRes) {
            try {
                val k = s0.keyUri
                if (k != null) data = Hls.decrypt(data, Net.readBytes(k, h), s0.iv, s0.seq)
                val init = m.init
                var initData = if (init != null) {
                    if (init.rangeStart != null && init.rangeLength != null)
                        Net.readRangeBytes(init.url, h, init.rangeStart, init.rangeLength)
                    else Net.readBytes(init.url, h)
                } else null
                if (initData != null && init?.keyUri != null) {
                    initData = Hls.decrypt(initData!!, Net.readBytes(init.keyUri, h), init.iv, 0L)
                }
                val r = if (initData != null) Sps.fromAvcC(initData) else Sps.fromTs(data)
                if (r != null) { w = r[0]; hh = r[1] }
            } catch (e: Exception) {
            }
        }
        val bytes = if (s0.dur > 0 && m.duration > 0) (size0 / s0.dur * m.duration).toLong() else -1L
        return Info(m.duration, bytes, w, hh)
    }

    /** قراءة مدة/أبعاد الملف المباشر عند الحاجة لتمييز فيديو المحتوى عن ملف إعلان قصير.
     * تُستخدم خصوصًا للمصادر الشبكية التي جُمعت أثناء preroll؛ ولا نعتمد عليها وحدها إذا فشل الخادم.
     */
    private fun remoteVideoInfo(url: String, headers: Map<String, String>): Info? {
        val retriever = MediaMetadataRetriever()
        return try {
            val requestHeaders = HashMap(headers)
            try {
                CookieManager.getInstance().getCookie(url)?.let { cookie ->
                    if (cookie.isNotBlank() && requestHeaders.keys.none { it.equals("Cookie", true) }) {
                        requestHeaders["Cookie"] = cookie
                    }
                }
            } catch (_: Exception) {}
            retriever.setDataSource(url, requestHeaders)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Info(durationMs.coerceAtLeast(0L) / 1000.0, -1L, width, height).takeIf { durationMs > 0L }
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun qualityFromUrl(url: String): Int? {
        val decoded = try { java.net.URLDecoder.decode(url, "UTF-8") } catch (_: Exception) { url }
        Regex("(?i)(\\d{3,4})x(\\d{3,4})").find(decoded)?.groupValues?.getOrNull(2)?.toIntOrNull()?.let { return it }
        Regex("(?i)(?:^|[^0-9])(\\d{3,4})\\s*p(?:[^a-z]|$)").find(decoded)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        val uri = try { java.net.URI(decoded) } catch (_: Exception) { null }
        if (uri != null) {
            val query = uri.rawQuery.orEmpty().split('&')
            for (part in query) {
                val pair = part.split('=', limit = 2)
                if (pair.size != 2) continue
                val key = try { java.net.URLDecoder.decode(pair[0], "UTF-8") } catch (_: Exception) { pair[0] }
                val value = try { java.net.URLDecoder.decode(pair[1], "UTF-8") } catch (_: Exception) { pair[1] }
                if (key.equals("quality", true) || key.equals("resolution", true) || key.equals("height", true)) {
                    Regex("(?i)(\\d{3,4})\\s*p?").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
                }
            }
        }
        return null
    }

    /**
     * Extract the actual rendition height from a human/player label.
     * Do not take the first 3-4 digit number blindly: labels such as
     * "H.264 1080p" or "AVC1.640028 / 720p" contain codec/profile numbers
     * before the resolution. Those numbers used to make every rendition share
     * one deduplication key (for example 264), hiding the other qualities.
     */
    fun qualityFromLabel(label: String): Int? {
        val text = label.lowercase(Locale.ROOT).trim()
        if (text.isEmpty()) return null

        // Explicit quality suffix wins over codec/profile identifiers.
        Regex("(?<![a-z0-9.])(\\d{3,4})\\s*p(?:\\s*\\d{1,3}\\s*fps?)?(?![a-z0-9])", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        // Resolution is conventionally width x height; use the second number.
        Regex("(?<![a-z0-9])(\\d{3,5})\\s*[x×]\\s*(\\d{3,5})(?![a-z0-9])", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(2)?.toIntOrNull()?.let { return it }

        when {
            Regex("\\b4k\\b|\\buhd\\b").containsMatchIn(text) -> return 2160
            Regex("\\b2k\\b|\\bqhd\\b").containsMatchIn(text) -> return 1440
            Regex("\\bfhd\\b|full[ -]?hd").containsMatchIn(text) -> return 1080
        }

        // Standalone numeric quality labels ("1080", "720 HD") are supported,
        // but numbers embedded in codec names (H.264, AVC1.640028) are ignored.
        Regex("(?<![a-z0-9.])(\\d{3,4})(?:\\s*(?:hd|sd))?(?![a-z0-9])", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        return if (Regex("\\bhd\\b").containsMatchIn(text)) 720 else null
    }

    /** Resolve all video renditions from an HLS master, including nested master playlists. */
    private fun resolveHlsMaster(
        masterUrl: String,
        masterText: String,
        headers: Map<String, String>,
        out: MutableList<Format>,
        visited: MutableSet<String>,
        depth: Int,
        sourceHeaders: Map<String, String>
    ) {
        if (depth > 4) throw IOException("تجاوز عمق قوائم HLS الرئيسية المتداخلة")
        if (!visited.add(masterUrl)) return
        val variants = Hls.parseMaster(masterText, masterUrl)
        if (variants.isEmpty()) throw IOException("قائمة HLS الرئيسية لا تحتوي تمثيلات فيديو")

        for (variant in variants) {
            if (isAdUrl(variant.url) || isPreviewUrl(variant.url) || variant.url in visited) continue
            val childText = try { Net.fetchText(variant.url, headers) } catch (_: Exception) { continue }
            if (Hls.isMaster(childText)) {
                resolveHlsMaster(variant.url, childText, headers, out, visited, depth + 1, sourceHeaders)
                continue
            }
            visited.add(variant.url)
            val media = try { Hls.parseMedia(childText, variant.url) } catch (_: Exception) { continue }
            if (media.segs.isEmpty()) continue
            var width = 0
            var height = 0
            val resolutionParts = variant.resolution.split('x')
            if (resolutionParts.size == 2) {
                width = resolutionParts[0].toIntOrNull() ?: 0
                height = resolutionParts[1].toIntOrNull() ?: 0
            }
            var duration = media.duration
            var estimatedBytes = -1L
            val bandwidth = if (variant.avg > 0L) variant.avg else variant.bandwidth
            if (height <= 0) {
                try {
                    val info = mediaInfo(variant.url, headers, true)
                    duration = info.duration.takeIf { it > 0.0 } ?: duration
                    estimatedBytes = info.bytes
                    width = info.w
                    height = info.h
                } catch (_: Exception) {}
            }
            if (estimatedBytes <= 0L && duration > 0.0 && bandwidth > 0L) {
                estimatedBytes = (bandwidth.toDouble() / 8.0 * duration).toLong()
            }
            val label = when {
                height > 0 -> qualityLabel(width, height)
                variant.name.isNotBlank() && Regex("(?i)(\\d{3,4}\\s*p|4k|2k|hd|sd)").containsMatchIn(variant.name) -> variant.name
                bandwidth > 0L -> "جودة " + String.format(Locale.US, "%.1f Mbps", bandwidth / 1_000_000.0)
                else -> "جودة غير معروفة"
            }
            val details = buildList {
                if (variant.resolution.isNotBlank()) add(variant.resolution)
                if (variant.frameRate > 0.0) add(String.format(Locale.US, "%.0f fps", variant.frameRate))
                if (variant.codecs.isNotBlank()) add(variant.codecs)
                if (variant.videoRange.isNotBlank() && !variant.videoRange.equals("SDR", true)) add(variant.videoRange)
                if (bandwidth > 0L) add(String.format(Locale.US, "%.2f Mbps", bandwidth / 1_000_000.0))
            }.joinToString(" • ")
            val ext = if (media.init != null) "mp4" else "ts"
            out.add(
                Format(
                    label, details, variant.url, "hls", ext, true, fmtEst(estimatedBytes), false,
                    duration, estimatedBytes, sourceHeaders, variant.codecs, variant.frameRate,
                    variant.videoRange, bandwidth, variant.name
                )
            )
        }

        for (audio in Hls.parseAudio(masterText, masterUrl).filterNot { isAdUrl(it.url) || isPreviewUrl(it.url) }.take(3)) {
            if (out.any { it.url == audio.url }) continue
            var estimated = -1L
            var audioDuration = 0.0
            try {
                val info = mediaInfo(audio.url, headers, false)
                estimated = info.bytes
                audioDuration = info.duration
            } catch (_: Exception) {}
            out.add(Format("صوت (" + audio.name + ")", "", audio.url, "hls", "ts", true, fmtEst(estimated), true, audioDuration, estimated, sourceHeaders))
        }
    }

    fun resolve(item: MediaItem, h: Map<String, String>): List<Format> {
        val out = ArrayList<Format>()
        if (isAdUrl(item.url) || isPreviewUrl(item.url)) return out
        val sourceHeaders = CaptureHeaders.merge(h, item.requestHeaders)
        val uriPath = try { java.net.URI(item.url).rawPath.orEmpty().lowercase(Locale.ROOT) } catch (_: Exception) { "" }
        val mimeHint = item.mimeHint.lowercase(Locale.ROOT)
        val pathExt = uriPath.substringAfterLast('.', "")
        val pathLooksVideo = pathExt in setOf("mp4", "m4v", "webm", "mkv", "mov", "flv", "3gp")
        val explicitHls = item.type == "hls" || mimeHint.contains("mpegurl") || mimeHint.contains("m3u8") ||
            uriPath.endsWith(".m3u8") || uriPath.contains("/hls/") || uriPath.contains("/playlist") ||
            uriPath.endsWith("/manifest")
        val explicitDash = mimeHint.contains("dash+xml") || uriPath.endsWith(".mpd")
        if (explicitDash) throw IOException("المصدر يستخدم MPEG-DASH (MPD)، واستخراج تمثيلاته وتنزيلها يحتاج مسار DASH مستقلًا")

        if (explicitHls) {
            val text = Net.fetchText(item.url, sourceHeaders)
            if (!text.trimStart().startsWith("#EXTM3U")) throw IOException("مصدر HLS لا يحتوي قائمة M3U8 صالحة")
            if (Hls.isMaster(text)) {
                resolveHlsMaster(item.url, text, sourceHeaders, out, LinkedHashSet(), 0, sourceHeaders)
            } else {
                val inf = mediaInfo(item.url, sourceHeaders, true)
                val media = Hls.parseMedia(text, item.url)
                val label = if (inf.h > 0) qualityLabel(inf.w, inf.h)
                else if (inf.bytes > 0 && inf.duration > 0)
                    "جودة " + String.format(Locale.US, "%.1f Mbps", inf.bytes * 8 / inf.duration / 1_000_000.0)
                else item.qualityHint.takeIf { it.isNotBlank() } ?: "جودة غير معروفة"
                out.add(
                    Format(
                        label, if (inf.w > 0 && inf.h > 0) "${inf.w}x${inf.h}" else "", item.url, "hls",
                        if (media.init != null) "mp4" else "ts", false, fmtEst(inf.bytes), false,
                        inf.duration, inf.bytes, sourceHeaders, item.codecHint, item.frameRateHint,
                        item.dynamicRangeHint, item.bandwidthHint, item.representationIdHint
                    )
                )
            }
            return out
        }

        // A failed Range probe must not erase a player-declared rendition. Some CDNs
        // reject bytes=0-0 or return 206 with an unknown total even though GET works.
        // We still require a strong player/URL video signal before accepting probe failure.
        val trustedVideoMetadata = item.heightHint in 144..4320 || item.widthHint > 0 ||
            item.qualityHint.isNotBlank() || mimeHint.startsWith("video/") || pathLooksVideo
        val probe = try {
            Net.probe(item.url, sourceHeaders)
        } catch (e: Exception) {
            if (!trustedVideoMetadata) throw e
            null
        }
        val probeMime = probe?.mime.orEmpty().lowercase(Locale.ROOT)
        if (probeMime.contains("dash+xml")) throw IOException("المصدر يستخدم MPEG-DASH (MPD)، واستخراج تمثيلاته يحتاج مسار DASH مستقلًا")
        if (probeMime.contains("mpegurl") || probeMime.contains("m3u8")) {
            val text = Net.fetchText(item.url, sourceHeaders)
            if (!text.trimStart().startsWith("#EXTM3U")) throw IOException("الخادم أعلن HLS لكن الاستجابة ليست قائمة M3U8")
            if (Hls.isMaster(text)) resolveHlsMaster(item.url, text, sourceHeaders, out, LinkedHashSet(), 0, sourceHeaders)
            else {
                val inf = mediaInfo(item.url, sourceHeaders, true)
                val media = Hls.parseMedia(text, item.url)
                out.add(Format(
                    item.qualityHint.ifBlank { if (inf.h > 0) qualityLabel(inf.w, inf.h) else "جودة غير معروفة" },
                    if (inf.w > 0 && inf.h > 0) "${inf.w}x${inf.h}" else "", item.url, "hls",
                    if (media.init != null) "mp4" else "ts", false, fmtEst(inf.bytes), false,
                    inf.duration, inf.bytes, sourceHeaders, item.codecHint, item.frameRateHint,
                    item.dynamicRangeHint, item.bandwidthHint, item.representationIdHint
                ))
            }
            return out
        }

        val mime = (mimeHint.takeIf { it.isNotBlank() } ?: probeMime).lowercase(Locale.ROOT)
        // Do not mistake HTML/JSON/image responses for video, even when a player object
        // contained a URL. An explicit video MIME/quality is accepted only if the probe
        // did not positively identify a non-video response.
        if (mime.startsWith("text/html") || mime.contains("json") || mime.contains("xml") || mime.startsWith("image/")) return out
        if (probeMime.startsWith("text/") || probeMime.startsWith("image/") || probeMime.contains("json") ||
            probeMime.contains("html") || probeMime.contains("xml")) return out
        val total = probe?.total ?: -1L
        if (total in 1L..30_000L && !trustedVideoMetadata) return out

        val declaredMime = item.mimeHint.takeIf { it.isNotBlank() } ?: probeMime
        val ext = extOf(item.url, declaredMime)
        val q = qualityFromUrl(item.url)
            ?: item.heightHint.takeIf { it in 144..4320 }
            ?: qualityFromLabel(item.qualityHint)
        val needRemoteInfo = item.origin == "network-pending-ad" || item.origin == "dom-preroll"
        val remoteInfo = if (needRemoteInfo) remoteVideoInfo(item.url, sourceHeaders) else null
        val audio = isAudio(ext) || mime.startsWith("audio/")
        val width = remoteInfo?.w?.takeIf { it > 0 } ?: item.widthHint
        val height = remoteInfo?.h?.takeIf { it > 0 } ?: item.heightHint.takeIf { it > 0 } ?: (q ?: 0)
        val label = when {
            audio -> ext.uppercase()
            height > 0 -> qualityLabel(width.takeIf { it > 0 } ?: (height * 16 / 9), height)
            item.qualityHint.isNotBlank() -> item.qualityHint
            q != null -> qualityLabel(q * 16 / 9, q)
            else -> "الجودة الأصلية"
        }
        val details = buildList {
            add(Util.host(item.url))
            if (width > 0 && height > 0) add("${width}x${height}")
            if (item.codecHint.isNotBlank()) add(item.codecHint)
            if (item.frameRateHint > 0.0) add(String.format(Locale.US, "%.0f fps", item.frameRateHint))
            if (item.dynamicRangeHint.isNotBlank()) add(item.dynamicRangeHint)
            if (item.bandwidthHint > 0L) add(String.format(Locale.US, "%.2f Mbps", item.bandwidthHint / 1_000_000.0))
            add(if (probe?.rangeOk == true) "يدعم التجزئة والاستئناف" else "بدون معلومات Range؛ سيتم فحصه عند التنزيل")
        }.joinToString(" • ")
        out.add(
            Format(
                label, details, item.url, "direct", ext, false, fmtEst(total), audio,
                remoteInfo?.duration ?: 0.0, total, sourceHeaders, item.codecHint,
                item.frameRateHint, item.dynamicRangeHint, item.bandwidthHint, item.representationIdHint
            )
        )
        return out
    }
}
