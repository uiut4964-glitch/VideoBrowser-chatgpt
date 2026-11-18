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
    val capturedAt: Long = System.currentTimeMillis()
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
