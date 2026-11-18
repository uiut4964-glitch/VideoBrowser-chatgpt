package android.media

/** JVM-only stub used to compile Formats.kt tests without Android SDK. */
class MediaMetadataRetriever {
    fun setDataSource(url: String, headers: Map<String, String>) {}
    fun extractMetadata(key: Int): String? = null
    fun release() {}
    companion object {
        const val METADATA_KEY_DURATION = 9
        const val METADATA_KEY_VIDEO_WIDTH = 18
        const val METADATA_KEY_VIDEO_HEIGHT = 19
    }
}
