package com.example.videobrowser

fun main() {
    check(FramePlaybackPolicy.shouldActivate(playing = true, userGesture = true, isAd = false, suspectedPreroll = false))
    check(!FramePlaybackPolicy.shouldActivate(playing = false, userGesture = true, isAd = false, suspectedPreroll = false))
    check(!FramePlaybackPolicy.shouldActivate(playing = true, userGesture = false, isAd = false, suspectedPreroll = false))
    check(!FramePlaybackPolicy.shouldActivate(playing = true, userGesture = true, isAd = true, suspectedPreroll = false))
    check(!FramePlaybackPolicy.shouldActivate(playing = true, userGesture = true, isAd = false, suspectedPreroll = true))
    check(FramePlaybackPolicy.shouldUseFrameFallback(matchesVideo = true, fresh = true, playing = true, userGesture = true, isAd = false, suspectedPreroll = false))
    check(!FramePlaybackPolicy.shouldUseFrameFallback(matchesVideo = true, fresh = false, playing = true, userGesture = true, isAd = false, suspectedPreroll = false))
    check(!FramePlaybackPolicy.shouldUseFrameFallback(matchesVideo = false, fresh = true, playing = true, userGesture = true, isAd = false, suspectedPreroll = false))
    check(!FramePlaybackPolicy.shouldUseFrameFallback(matchesVideo = true, fresh = true, playing = true, userGesture = true, isAd = true, suspectedPreroll = false))
    println("FramePlaybackPolicy passed: gesture + playing required; ads/preroll never activate FAB")
}
