package com.example.videobrowser

fun main() {
    val captured = CaptureHeaders.safeRequestHeaders(
        linkedMapOf(
            "user-agent" to "Browser-UA",
            "Referer" to "https://site.example/watch/42?session=private#player",
            "Origin" to "https://site.example/private/path",
            "Accept" to "application/vnd.apple.mpegurl,*/*",
            "Accept-Language" to "ar,en;q=0.8",
            "Cookie" to "session=must-not-be-copied",
            "Authorization" to "Bearer must-not-be-copied",
            "X-Private-Token" to "must-not-be-copied",
            "Sec-Fetch-Site" to "same-origin"
        )
    )

    check(captured["User-Agent"] == "Browser-UA")
    check(captured["Referer"] == "https://site.example/watch/42")
    check(captured["Origin"] == "https://site.example")
    check(captured["Accept"] == "application/vnd.apple.mpegurl,*/*")
    check(captured["Accept-Language"] == "ar,en;q=0.8")
    check(captured.keys.none { it.equals("Cookie", true) || it.equals("Authorization", true) || it.equals("X-Private-Token", true) })
    check(captured.size == 5)

    val merged = CaptureHeaders.merge(
        mapOf("User-Agent" to "fallback", "Referer" to "https://site.example/"),
        mapOf("User-Agent" to "per-request", "Accept" to "video/*")
    )
    check(merged["User-Agent"] == "per-request")
    check(merged["Referer"] == "https://site.example/")
    check(merged["Accept"] == "video/*")
    check(merged.keys.count { it.equals("User-Agent", true) } == 1)

    val opaqueUrl = "https://cdn.example/cdn/path/v?token=opaque"
    val playerContext = CapturedMediaRequest(
        url = opaqueUrl,
        pageUrl = "https://site.example/watch/42",
        method = "GET",
        headers = captured,
        source = "webview",
        isMainFrame = false,
        hasGesture = true,
        capturedAt = 1_000L
    )
    check(CaptureHeaders.matchesPreparedSource(opaqueUrl, playerContext, "https://site.example", topPageUrl = "https://site.example/watch/42", nowMillis = 2_000L))
    val thirdPartyFrameContext = playerContext.copy(pageUrl = "https://site.example/watch/42")
    check(CaptureHeaders.matchesPreparedSource(opaqueUrl, thirdPartyFrameContext, "https://player.cdn.example", topPageUrl = "https://site.example/watch/42", nowMillis = 2_000L))
    check(!CaptureHeaders.matchesPreparedSource("https://cdn.example/other", playerContext, "https://site.example", topPageUrl = "https://site.example/watch/42", nowMillis = 2_000L))
    check(!CaptureHeaders.matchesPreparedSource(opaqueUrl, playerContext.copy(capturedDuringAd = true), "https://site.example", topPageUrl = "https://site.example/watch/42", nowMillis = 2_000L))
    check(!CaptureHeaders.matchesPreparedSource(opaqueUrl, playerContext, "https://other.example", topPageUrl = "https://other.example/watch/42", nowMillis = 2_000L))
    check(!CaptureHeaders.matchesPreparedSource(opaqueUrl, playerContext, "https://site.example", topPageUrl = "https://site.example/watch/42", nowMillis = 200_000L))

    println("Capture headers test passed: allowlist, secret exclusion, request merge, and safe opaque-source context binding")
}
