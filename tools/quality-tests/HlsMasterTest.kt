package com.example.videobrowser
fun main() {
    val manifest = """
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=350000,AVERAGE-BANDWIDTH=300000,RESOLUTION=426x240,NAME="240p",CODECS="avc1.4d4015,mp4a.40.2"
r240/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=800000,AVERAGE-BANDWIDTH=700000,RESOLUTION=854x480,NAME="480p",CODECS="avc1.4d401f,mp4a.40.2"
r480/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1800000,AVERAGE-BANDWIDTH=1600000,RESOLUTION=1280x720,NAME="720p",FRAME-RATE=30.0,CODECS="avc1.4d401f,mp4a.40.2"
r720/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=3600000,AVERAGE-BANDWIDTH=3200000,RESOLUTION=1920x1080,NAME="1080p",FRAME-RATE=60.0,CODECS="avc1.640028,mp4a.40.2"
r1080/index.m3u8
#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="angles",NAME="2160p",URI="r2160/index.m3u8",RESOLUTION="3840x2160"
""".trimIndent()
    val variants = Hls.parseMaster(manifest, "https://cdn.example/video/master.m3u8")
    check(variants.size == 5) { "expected 5 variants, got ${variants.size}" }
    check(variants.map { it.resolution }.containsAll(listOf("426x240", "854x480", "1280x720", "1920x1080")))
    check(variants.last().url == "https://cdn.example/video/r2160/index.m3u8")
    val videoOnlyMaster = """#EXTM3U
#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="video",NAME="1440p",URI="/api/video?id=1440",RESOLUTION="2560x1440"
""".trimIndent()
    check(Hls.isMaster(videoOnlyMaster)) { "TYPE=VIDEO-only master was not recognized" }
    val videoOnly = Hls.parseMaster(videoOnlyMaster, "https://cdn.example/master")
    check(videoOnly.size == 1 && videoOnly.single().resolution == "2560x1440")
    println("HLS parser test passed: ${variants.size} renditions (240/480/720/1080 plus alternate video)")
}
