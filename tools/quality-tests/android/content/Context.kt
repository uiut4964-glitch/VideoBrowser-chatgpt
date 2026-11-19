package android.content
open class Context {
    companion object { const val CONNECTIVITY_SERVICE = "connectivity" }
    open fun <T> getSystemService(clazz: Class<T>): T? = null
}
