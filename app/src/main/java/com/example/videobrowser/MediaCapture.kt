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

    /**
     * Some players publish several signed rendition URLs on one CDN host but only
     * actually request the currently selected rendition. In that case WebView has a
     * real request context for one sibling URL, not for every URL in mediaDefinitions.
     * Share only the safe allowlisted headers with sibling renditions on the exact same
     * CDN host and only when the request came from the same player page origin.
     * Cookies and Authorization are never part of this map; CookieManager resolves
     * cookies independently for each target URL at request time.
     */
    fun matchesPreparedSourceHost(
        preparedUrl: String,
        request: CapturedMediaRequest,
        primaryFrameOrigin: String,
        topPageUrl: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = 180_000L
    ): Boolean {
        if (!request.method.equals("GET", true)) return false
        if (nowMillis - request.capturedAt !in 0L..maxAgeMillis) return false
        val preparedHost = webHost(preparedUrl) ?: return false
        val requestHost = webHost(request.url) ?: return false
        if (preparedHost != requestHost) return false
        val expectedOrigin = webOrigin(primaryFrameOrigin)
        val topOrigin = webOrigin(topPageUrl)
        val requestOrigin = webOrigin(request.pageUrl)
        if (requestOrigin != null && expectedOrigin != null && requestOrigin != expectedOrigin && requestOrigin != topOrigin) return false
        // Do not let a request with no usable safe headers count as a match.
        return safeRequestHeaders(request.headers).isNotEmpty()
    }

    private fun webHost(raw: String): String? {
        return try {
            val uri = java.net.URI(raw.trim())
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val port = if (uri.port == -1 || (scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)) "" else ":${uri.port}"
            host + port
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


/** Recognizes common signed-URL expiry parameters without exposing their values in diagnostics. */
object MediaUrlExpiry {
    private val expiryKeys = setOf("validto", "expires", "expiry", "expire", "exp", "token_exp")

    fun expiresAtEpochSeconds(rawUrl: String): Long? {
        return try {
            val uri = java.net.URI(rawUrl)
            val query = uri.rawQuery ?: return null
            query.split('&').forEach { component ->
                val parts = component.split('=', limit = 2)
                if (parts.size != 2) return@forEach
                val key = try { java.net.URLDecoder.decode(parts[0], "UTF-8") } catch (_: Exception) { parts[0] }
                if (key.lowercase() !in expiryKeys) return@forEach
                val value = try { java.net.URLDecoder.decode(parts[1], "UTF-8") } catch (_: Exception) { parts[1] }
                val numeric = value.toLongOrNull()
                if (numeric != null) {
                    return if (numeric >= 1_000_000_000_000L) numeric / 1000L else numeric
                }
                val parsed = try { java.time.Instant.parse(value).epochSecond } catch (_: Exception) { null }
                if (parsed != null) return parsed
            }
            null
        } catch (_: Exception) { null }
    }

    fun isExpired(rawUrl: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L, clockSkewSeconds: Long = 30L): Boolean {
        val expires = expiresAtEpochSeconds(rawUrl) ?: return false
        return expires < nowEpochSeconds - clockSkewSeconds
    }
}
