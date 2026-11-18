package com.example.videobrowser
fun main() {
    val masterUrl = "https://cdn.example/master.m3u8"
    val heights = listOf(240, 480, 720, 1080, 1440, 2160)
    val widths = listOf(426, 854, 1280, 1920, 2560, 3840)
    val master = buildString {
        appendLine("#EXTM3U")
        heights.indices.forEach { i ->
            appendLine("#EXT-X-STREAM-INF:BANDWIDTH=${(i + 1) * 500000},AVERAGE-BANDWIDTH=${(i + 1) * 450000},RESOLUTION=${widths[i]}x${heights[i]},NAME=\"${heights[i]}p\"")
            appendLine("/api/rendition?id=${heights[i]}&signed=token")
        }
    }
    Net.docs[masterUrl] = master
    heights.forEach { h ->
        Net.docs["https://cdn.example/api/rendition?id=${h}&signed=token"] = """
            #EXTM3U
            #EXT-X-TARGETDURATION:600
            #EXTINF:600.0,
            segment.ts
            #EXT-X-ENDLIST
        """.trimIndent()
    }
    val result = Formats.resolve(
        MediaItem(masterUrl, "hls", requestHeaders = mapOf(
            "Referer" to "https://site.example/watch/42",
            "Accept" to "application/vnd.apple.mpegurl"
        )),
        mapOf("User-Agent" to "VideoBrowser-Test-UA")
    )
    val labels = result.map { it.label }
    check(result.size == 6) { "expected 6 quality variants, got ${result.size}: $labels" }
    heights.forEach { h -> check(labels.any { it.contains("${h}P") }) { "missing ${h}p in $labels" } }
    result.forEach { format ->
        check(format.requestHeaders["Referer"] == "https://site.example/watch/42")
        check(format.requestHeaders["Accept"] == "application/vnd.apple.mpegurl")
        check(format.requestHeaders["User-Agent"] == "VideoBrowser-Test-UA")
    }
    println("Formats.resolve test passed: ${labels.joinToString(", ")} with request-context headers preserved")

    // Equal-height variants with different codecs/rates must remain separate choices.
    val representationMasterUrl = "https://cdn.example/representations/master.m3u8"
    Net.docs[representationMasterUrl] = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=2500000,AVERAGE-BANDWIDTH=2200000,RESOLUTION=1920x1080,NAME="1080p AVC",FRAME-RATE=30.0,CODECS="avc1.640028,mp4a.40.2",VIDEO-RANGE=SDR
avc/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=4200000,AVERAGE-BANDWIDTH=3900000,RESOLUTION=1920x1080,NAME="1080p HEVC HDR",FRAME-RATE=60.0,CODECS="hvc1.2.4.L150.B0,mp4a.40.2",VIDEO-RANGE=PQ
hevc/index.m3u8
""".trimIndent()
    listOf("avc", "hevc").forEach { codec ->
        Net.docs["https://cdn.example/representations/$codec/index.m3u8"] = """#EXTM3U
#EXTINF:180.0,
segment.ts
#EXT-X-ENDLIST
""".trimIndent()
    }
    val sameHeightRepresentations = Formats.resolve(MediaItem(representationMasterUrl, "hls"), emptyMap())
    check(sameHeightRepresentations.size == 2) {
        "expected both 1080p representations, got ${sameHeightRepresentations.map { it.details }}"
    }
    check(sameHeightRepresentations.map { it.codec }.distinct().size == 2) {
        "same-height codec variants collapsed: ${sameHeightRepresentations.map { it.codec }}"
    }
    check(sameHeightRepresentations.any { it.frameRate == 30.0 } && sameHeightRepresentations.any { it.frameRate == 60.0 })
    check(sameHeightRepresentations.any { it.dynamicRange == "PQ" })
    println("Same-resolution representation test passed: AVC SDR 30fps and HEVC PQ 60fps both preserved")
}
