package com.example.videobrowser

import android.os.Build
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

/**
 * Service Worker interception is application-wide in Android WebView. This coordinator
 * installs one passive observer and lets the foreground browser activity consume events.
 * Returning null is intentional: the original network request/response is never replaced.
 */
object MediaCaptureCoordinator {
    @Volatile private var activeOwner: Any? = null
    @Volatile private var activeListener: ((CapturedMediaRequest) -> Unit)? = null
    @Volatile private var installed = false

    @Synchronized
    fun installServiceWorkerClient() {
        if (installed || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    try {
                        val captured = snapshot(
                            url = request.url.toString(),
                            method = request.method,
                            requestHeaders = request.requestHeaders,
                            fallbackPageUrl = "",
                            source = "service-worker",
                            isMainFrame = request.isForMainFrame,
                            hasGesture = request.hasGesture()
                        )
                        if (captured.method == "GET") activeListener?.invoke(captured)
                    } catch (_: Exception) {
                        // Capture is best-effort and must never disrupt page networking.
                    }
                    return null
                }
            })
            installed = true
        } catch (_: Exception) {
            // Keep browser startup functional on WebView implementations that reject it.
            installed = false
        }
    }

    fun attach(owner: Any, listener: (CapturedMediaRequest) -> Unit) {
        activeOwner = owner
        activeListener = listener
    }

    fun detach(owner: Any) {
        if (activeOwner === owner) {
            activeListener = null
            activeOwner = null
        }
    }

    fun snapshot(
        url: String,
        method: String,
        requestHeaders: Map<String, String>,
        fallbackPageUrl: String,
        source: String,
        isMainFrame: Boolean,
        hasGesture: Boolean
    ): CapturedMediaRequest {
        val safeHeaders = CaptureHeaders.safeRequestHeaders(requestHeaders)
        val referer = safeHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value.orEmpty()
        return CapturedMediaRequest(
            url = url,
            pageUrl = referer.ifBlank { fallbackPageUrl },
            method = method.uppercase(),
            headers = safeHeaders,
            source = source,
            isMainFrame = isMainFrame,
            hasGesture = hasGesture
        )
    }
}
