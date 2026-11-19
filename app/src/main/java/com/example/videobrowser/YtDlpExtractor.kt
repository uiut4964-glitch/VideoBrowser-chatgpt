package com.example.videobrowser

import android.content.Context
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.net.URI
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Android adapter around the real youtubedl-android / yt-dlp engine.
 * Extraction is page-first: the page URL is passed to yt-dlp before playback,
 * with the current WebView request context. WebView sniffing remains a fallback.
 */
object YtDlpExtractor {
    data class Result(
        val formats: List<Format>,
        val title: String = "",
        val durationSec: Double = 0.0,
        val extractor: String = "",
        val version: String = "",
        val error: String = ""
    )

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "VideoBrowser-yt-dlp").apply { isDaemon = true }
    }
    @Volatile private var initialized = false
    @Volatile private var initError = ""

    fun warmUp(context: Context) {
        worker.execute {
            try {
                ensureInitialized(context.applicationContext)
            } catch (e: Exception) {
                initError = safeError(e)
            }
        }
    }

    fun extract(
        context: Context,
        pageUrl: String,
        requestHeaders: Map<String, String>,
        callback: (Result) -> Unit
    ) {
        worker.execute {
            val result = try {
                ensureInitialized(context.applicationContext)
                val request = YoutubeDLRequest(pageUrl)
                request.addOption("--no-playlist")
                request.addOption("--no-warnings")
                request.addOption("--no-cache-dir")
                request.addOption("--socket-timeout", "20")
                request.addOption("--extractor-retries", "1")

                // Pass only request context that is useful to the page extractor.
                // Cookies are read from Android CookieManager at request time and are
                // never persisted by this adapter.
                val headers = LinkedHashMap<String, String>()
                headers.putAll(requestHeaders)
                try {
                    CookieManager.getInstance().getCookie(pageUrl)?.takeIf { it.isNotBlank() }?.let {
                        headers.putIfAbsent("Cookie", it)
                    }
                } catch (_: Exception) {
                }
                val allowedHeaders = setOf(
                    "user-agent", "referer", "origin", "cookie", "accept", "accept-language",
                    "authorization", "x-requested-with"
                )
                for ((name, value) in headers) {
                    if (name.lowercase(Locale.ROOT) !in allowedHeaders) continue
                    if (name.contains('\n') || name.contains('\r') || value.contains('\n') || value.contains('\r')) continue
                    request.addOption("--add-headers", "$name: $value")
                }

                val info = YoutubeDL.getInstance().getInfo(request)
                val mapped = mapFormats(info, headers)
                Result(
                    formats = mapped,
                    title = info.title.orEmpty(),
                    durationSec = info.duration.toDouble().takeIf { it > 0.0 } ?: 0.0,
                    extractor = info.extractor.orEmpty(),
                    version = YoutubeDL.version(context.applicationContext).orEmpty(),
                    error = if (mapped.isEmpty()) "yt-dlp أعاد بيانات الصفحة دون صيغة فيديو قابلة للتنزيل ضمن مسار التطبيق الحالي" else ""
                )
            } catch (e: Exception) {
                Result(
                    formats = emptyList(),
                    version = try { YoutubeDL.version(context.applicationContext).orEmpty() } catch (_: Exception) { "" },
                    error = safeError(e)
                )
            }
            try {
                callback(result)
            } catch (_: Exception) {
            }
        }
    }

    /** Explicit update action. It is not run automatically because it downloads a new engine binary. */
    fun update(context: Context, callback: (Boolean, String) -> Unit) {
        worker.execute {
            val result = try {
                ensureInitialized(context.applicationContext)
                val status = YoutubeDL.getInstance().updateYoutubeDL(
                    context.applicationContext,
                    YoutubeDL.UpdateChannel.STABLE
                )
                val version = YoutubeDL.version(context.applicationContext).orEmpty()
                true to when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "تم تحديث محرك yt-dlp إلى $version"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "محرك yt-dlp محدث بالفعل ($version)"
                    else -> "اكتمل فحص تحديث محرك yt-dlp ($version)"
                }
            } catch (e: Exception) {
                false to "تعذر تحديث محرك yt-dlp: ${safeError(e)}"
            }
            try { callback(result.first, result.second) } catch (_: Exception) {}
        }
    }

    fun status(context: Context): String {
        val version = try { YoutubeDL.version(context.applicationContext).orEmpty() } catch (_: Exception) { "" }
        return when {
            version.isNotBlank() -> "yt-dlp $version"
            initialized -> "yt-dlp مهيأ؛ الإصدار المضمن"
            initError.isNotBlank() -> "لم تتم تهيئة yt-dlp: $initError"
            else -> "yt-dlp لم تتم تهيئته بعد"
        }
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            YoutubeDL.getInstance().init(context)
            initialized = true
            initError = ""
        }
    }

    private fun mapFormats(info: VideoInfo, baseHeaders: Map<String, String>): List<Format> {
        val out = LinkedHashMap<String, Format>()
        val safeBaseHeaders = CaptureHeaders.safeRequestHeaders(baseHeaders)
        val formats = info.formats.orEmpty()
        val hlsMasters = LinkedHashSet<String>()
        for (item in formats) {
            val url = item.url?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) } ?: continue
            if (Formats.isAdUrl(url) || Formats.isPreviewUrl(url)) continue

            val ext = item.ext?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
                ?: extension(url)
            val manifest = item.manifestUrl.orEmpty()
            val dash = extension(url) == "mpd" || extension(manifest) == "mpd"
            if (dash) continue // The current DownloadCenter has no DASH mux/download path yet.
            val hls = extension(url) == "m3u8" || extension(manifest) == "m3u8" ||
                item.formatId.orEmpty().startsWith("hls", true)
            if (item.vcodec.equals("none", true)) continue
            if (item.acodec.equals("none", true)) {
                // HLS may expose video renditions separately from its audio group.
                // Resolve the master playlist through the app's HLS parser so it can
                // retain the correct audio rendition instead of offering silent video.
                if (hls && extension(manifest) == "m3u8" &&
                    !Formats.isAdUrl(manifest) && !Formats.isPreviewUrl(manifest)) {
                    hlsMasters.add(manifest)
                }
                continue
            }
            if (!hls && ext !in VIDEO_EXTENSIONS) continue
            if (extension(url) in setOf("m4s", "ts", "vtt", "json", "mpd")) continue

            val height = item.height.takeIf { it in 144..4320 }
                ?: Formats.qualityFromLabel(item.formatNote.orEmpty())
                ?: Formats.qualityFromLabel(item.format.orEmpty())
                ?: Formats.qualityFromLabel(item.formatId.orEmpty())
                ?: 0
            val width = item.width.takeIf { it > 0 } ?: if (height > 0) height * 16 / 9 else 0
            val label = when {
                height > 0 -> Formats.qualityLabel(width, height)
                !item.formatNote.isNullOrBlank() -> item.formatNote!!
                !item.format.isNullOrBlank() -> item.format!!
                !item.formatId.isNullOrBlank() -> item.formatId!!
                else -> "الجودة الأصلية"
            }
            val codec = listOfNotNull(item.vcodec?.takeIf { it.isNotBlank() }, item.acodec?.takeIf { it.isNotBlank() })
                .joinToString(" / ")
            val bandwidth = item.tbr.toLong().takeIf { it > 0L }?.times(1000L) ?: 0L
            val bytes = item.fileSize.takeIf { it > 0L } ?: item.fileSizeApproximate.takeIf { it > 0L } ?: -1L
            val details = buildList {
                add("yt-dlp")
                item.formatId?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (width > 0 && height > 0) add("${width}x${height}")
                if (codec.isNotBlank()) add(codec)
                if (item.fps > 0) add("${item.fps} fps")
                if (bandwidth > 0) add(String.format(Locale.US, "%.2f Mbps", bandwidth / 1_000_000.0))
            }.joinToString(" • ")
            val headers = CaptureHeaders.merge(safeBaseHeaders, CaptureHeaders.safeRequestHeaders(item.httpHeaders.orEmpty()))
            val format = Format(
                label = label,
                details = details,
                url = url,
                kind = if (hls) "hls" else "direct",
                ext = if (hls) "mp4" else ext,
                fromMaster = hls,
                sizeText = if (bytes > 0L) "≈ ${Util.fmtSize(bytes)}" else "",
                isAudio = false,
                durSec = info.duration.toDouble().takeIf { it > 0.0 } ?: 0.0,
                bytes = bytes,
                requestHeaders = headers,
                codec = codec,
                frameRate = item.fps.toDouble(),
                bandwidth = bandwidth,
                representationId = item.formatId.orEmpty()
            )
            // Deduplicate exact URLs only. Keep same-height AVC/HEVC, HDR/SDR and bitrate alternatives.
            val old = out[url]
            if (old == null || formatScore(format) > formatScore(old)) out[url] = format
        }

        // When yt-dlp reports video-only HLS variants plus an audio group, resolve
        // the common master playlist once. This gives the existing app HLS resolver
        // the opportunity to pair the video variant with the correct audio rendition.
        for (masterUrl in hlsMasters) {
            try {
                val expanded = Formats.resolve(
                    MediaItem(
                        url = masterUrl,
                        type = "hls",
                        origin = "dom",
                        requestHeaders = safeBaseHeaders,
                        captureSource = "yt-dlp"
                    ),
                    safeBaseHeaders
                )
                for (format in expanded) {
                    if (format.isAudio || Formats.isAudio(format.ext) ||
                        Formats.isAdUrl(format.url) || Formats.isPreviewUrl(format.url)) continue
                    val old = out[format.url]
                    if (old == null || formatScore(format) > formatScore(old)) out[format.url] = format
                }
            } catch (_: Exception) {
            }
        }

        // Some extractors return only a single progressive URL instead of a formats array.
        if (out.isEmpty()) {
            val url = info.url?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
            if (url != null && !Formats.isAdUrl(url) && !Formats.isPreviewUrl(url)) {
                val ext = info.ext?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: extension(url)
                if (ext in VIDEO_EXTENSIONS && ext != "mpd") {
                    val height = info.height.takeIf { it in 144..4320 } ?: 0
                    val width = info.width.takeIf { it > 0 } ?: if (height > 0) height * 16 / 9 else 0
                    out[url] = Format(
                        label = if (height > 0) Formats.qualityLabel(width, height) else "الجودة الأصلية",
                        details = "yt-dlp • ${info.extractor.orEmpty()}",
                        url = url,
                        kind = "direct",
                        ext = ext,
                        bytes = info.fileSize.takeIf { it > 0L } ?: info.fileSizeApproximate.takeIf { it > 0L } ?: -1L,
                        durSec = info.duration.toDouble().takeIf { it > 0.0 } ?: 0.0,
                        requestHeaders = CaptureHeaders.merge(safeBaseHeaders, CaptureHeaders.safeRequestHeaders(info.httpHeaders.orEmpty()))
                    )
                }
            }
        }
        return out.values.sortedWith(
            compareByDescending<Format> { Formats.qualityFromLabel(it.label) ?: 0 }
                .thenByDescending { it.frameRate }
                .thenByDescending { it.bandwidth }
                .thenByDescending { it.bytes }
                .thenBy { it.codec }
        )
    }

    private fun formatScore(f: Format): Int = listOf(
        f.codec.isNotBlank(), f.frameRate > 0.0, f.bandwidth > 0L, f.bytes > 0L,
        f.requestHeaders.isNotEmpty()
    ).count { it }

    private fun extension(raw: String): String = try {
        URI(raw).path.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
    } catch (_: Exception) { "" }

    private fun safeError(error: Throwable): String = (error.message ?: error.javaClass.simpleName)
        .replace(Regex("https?://\\S+"), "[رابط مخفي]")
        .replace(Regex("(?i)(cookie|authorization|proxy-authorization)\\s*[:=]\\s*[^\\r\\n]+"), "$1: [محجوب]")
        .replace(Regex("[?&](token|sig|signature|auth|key|expires|session)=[^&\\s]+", RegexOption.IGNORE_CASE), "[معامل مخفي]")
        .take(220)
        .ifBlank { error.javaClass.simpleName }

    private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov", "flv", "3gp")
}
