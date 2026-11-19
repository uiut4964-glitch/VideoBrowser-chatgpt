package com.example.videobrowser

/** Small pure policy shared by the iframe playback bridge and unit tests. */
object FramePlaybackPolicy {
    fun shouldActivate(playing: Boolean, userGesture: Boolean, isAd: Boolean, suspectedPreroll: Boolean): Boolean =
        playing && userGesture && !isAd && !suspectedPreroll

    /**
     * A recent native frame snapshot is an alternate playback signal when the top page
     * cannot observe the iframe's currentTime or user gesture directly.
     */
    fun shouldUseFrameFallback(
        matchesVideo: Boolean,
        fresh: Boolean,
        playing: Boolean,
        userGesture: Boolean,
        isAd: Boolean,
        suspectedPreroll: Boolean
    ): Boolean = matchesVideo && fresh && shouldActivate(playing, userGesture, isAd, suspectedPreroll)
}
