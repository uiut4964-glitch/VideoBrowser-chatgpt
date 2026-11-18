package android.webkit

/** JVM-only stub used to compile Formats.kt tests without Android SDK. */
class CookieManager {
    fun getCookie(url: String): String? = null
    companion object { fun getInstance() = CookieManager() }
}
