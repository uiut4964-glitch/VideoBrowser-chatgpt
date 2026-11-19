package com.example.videobrowser

/** A lightweight, in-memory snapshot of a media-related browser request. */
data class CapturedMediaRequest(
    val url: String,
    val pageUrl: String,
    val method: String,
    val headers: Map<String, String>,
    val source: String,
    val isMainFrame: Boolean,
    val hasGesture: Boolean,
    val capturedAt: Long = System.currentTimeMillis(),
    val capturedDuringAd: Boolean = false
)

/**
 * Only forwards non-secret headers that are useful for reproducing a browser request.
 * Cookies are obtained from Android CookieManager at request time. Authorization and
 * arbitrary custom headers are deliberately excluded so credentials are not written
 * into DownloadCenter's existing plaintext task store.
 */
object CaptureHeaders {
    private val allowed = mapOf(
        "user-agent" to "User-Agent",
        "referer" to "Referer",
        "origin" to "Origin",
        "accept" to "Accept",
        "accept-language" to "Accept-Language"
    )

    fun safeRequestHeaders(input: Map<String, String>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        input.forEach { (name, value) ->
            val canonical = allowed[name.lowercase()] ?: return@forEach
            val safeValue = when (canonical) {
                "Referer" -> sanitizeUrlHeader(value, keepPath = true)
                "Origin" -> sanitizeUrlHeader(value, keepPath = false)
                else -> value.trim().take(4096)
            }
            if (safeValue.isNotEmpty()) result[canonical] = safeValue
        }
        return result
    }

    private fun sanitizeUrlHeader(raw: String, keepPath: Boolean): String {
        return try {
            val uri = java.net.URI(raw.trim())
            val scheme = uri.scheme?.lowercase() ?: return ""
            val host = uri.host ?: return ""
            if (scheme != "http" && scheme != "https") return ""
            java.net.URI(
                scheme,
                null,
                host,
                uri.port,
                if (keepPath) uri.path?.ifBlank { "/" } ?: "/" else null,
                null,
                null
            ).toASCIIString().take(4096)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * A browser request context may be attached only to the exact player-declared URL,
     * while fresh and from the selected player frame. Ad-time contexts are never reused
     * for the content rendition after a preroll transition.
     */
    fun matchesPreparedSource(
        preparedUrl: String,
        request: CapturedMediaRequest,
        primaryFrameOrigin: String,
        topPageUrl: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = 180_000L
    ): Boolean {
        if (request.url != preparedUrl || !request.method.equals("GET", true) || request.capturedDuringAd) return false
        if (nowMillis - request.capturedAt !in 0L..maxAgeMillis) return false
        val expectedOrigin = webOrigin(primaryFrameOrigin)
        val topOrigin = webOrigin(topPageUrl)
        val requestOrigin = webOrigin(request.pageUrl)
        return requestOrigin == null || expectedOrigin == null || requestOrigin == expectedOrigin || requestOrigin == topOrigin
    }

    private fun webOrigin(raw: String): String? {
        return try {
            val uri = java.net.URI(raw.trim())
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val port = if (uri.port == -1 || (scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)) "" else ":${uri.port}"
            "$scheme://$host$port"
        } catch (_: Exception) { null }
    }

    /** Request-specific values override the page-level fallback values. */
    fun merge(base: Map<String, String>, request: Map<String, String>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        result.putAll(base)
        request.forEach { (name, value) ->
            val existing = result.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            if (existing != null) result.remove(existing)
            result[name] = value
        }
        return result
    }
}
