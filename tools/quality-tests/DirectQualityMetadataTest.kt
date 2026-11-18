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
}
