package com.example.videobrowser
fun main() {
    val heights = listOf(240, 480, 720, 1080, 1440, 2160)
    val labels = heights.map { h ->
        val url = "https://cdn.example/media?id=opaque-$h&token=signed"
        val format = Formats.resolve(
            MediaItem(url, "direct", "dom-preroll", "player-1", 1,
                qualityHint = "${h}p", widthHint = h * 16 / 9, heightHint = h, mimeHint = "video/mp4"),
            emptyMap()
        ).single()
        check(format.label.contains("${h}P")) { "expected ${h}p, got ${format.label}" }
        format.label
    }
    println("Direct source quality metadata test passed: ${labels.joinToString(", ")}")

    val codecPrefixedLabels = linkedMapOf(
        "H.264 240p" to 240,
        "H.264 480p" to 480,
        "H.265 / HEVC 720p" to 720,
        "AVC1.640028 1080p" to 1080,
        "1920x1080 H.264" to 1080,
        "2560x1440 (HEVC)" to 1440,
        "2160p AV1" to 2160,
        "QHD" to 1440
    )
    codecPrefixedLabels.forEach { (label, expected) ->
        val actual = Formats.qualityFromLabel(label)
        check(actual == expected) { "quality label parser misread '$label': expected $expected, got $actual" }
    }
    val distinctRanks = listOf("H.264 240p", "H.264 480p", "H.265 720p", "AVC1.640028 1080p")
        .mapNotNull { Formats.qualityFromLabel(it) }
    check(distinctRanks == listOf(240, 480, 720, 1080)) {
        "codec-prefixed quality labels collapsed or misordered: $distinctRanks"
    }
    println("Codec-prefixed label regression test passed: $distinctRanks")

    // A player-declared signed rendition should remain selectable when the CDN rejects Range probing.
    val blockedProbeUrl = "https://cdn.example/opaque-player-file?sig=temporary"
    Net.probeFailures[blockedProbeUrl] = "HTTP 403 on Range probe"
    val fallback = Formats.resolve(
        MediaItem(blockedProbeUrl, "direct", "dom", "player-2", 1,
            qualityHint = "1080p", heightHint = 1080, mimeHint = "video/mp4"),
        emptyMap()
    ).singleOrNull() ?: error("trusted player rendition disappeared when Range probe failed")
    check(fallback.label.contains("1080P"))
    check(fallback.bytes == -1L) { "failed probe must not invent an estimated file size" }
    check(fallback.details.contains("بدون معلومات Range"))
    println("Direct resolver fallback test passed: trusted 1080p source survives a blocked Range probe")

    val tinyTrustedUrl = "https://cdn.example/short-original?quality=240p"
    Net.probes[tinyTrustedUrl] = Net.Probe(18_000L, true, "video/mp4")
    val tinyTrusted = Formats.resolve(
        MediaItem(tinyTrustedUrl, "direct", "dom", "player-3", 1,
            qualityHint = "240p", heightHint = 240, mimeHint = "video/mp4"),
        emptyMap()
    ).singleOrNull() ?: error("small but explicitly-declared original video was rejected")
    check(tinyTrusted.label.contains("240P"))
    println("Small trusted-video test passed: explicitly declared short source is retained")
}
