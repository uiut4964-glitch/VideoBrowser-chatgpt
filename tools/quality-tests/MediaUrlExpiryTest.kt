package com.example.videobrowser

fun main() {
    val now = 1_800_000_000L
    check(MediaUrlExpiry.expiresAtEpochSeconds("https://cdn.example/v.mp4?validto=1800000100") == 1_800_000_100L)
    check(MediaUrlExpiry.expiresAtEpochSeconds("https://cdn.example/v.mp4?expires=1800000100000") == 1_800_000_100L)
    check(!MediaUrlExpiry.isExpired("https://cdn.example/v.mp4?validto=1800000100", now))
    check(MediaUrlExpiry.isExpired("https://cdn.example/v.mp4?validto=1799999900", now))
    check(!MediaUrlExpiry.isExpired("https://cdn.example/v.mp4?quality=1080", now))
    println("MediaUrlExpiry passed: validto/expires seconds and milliseconds recognized; unrelated query retained")
}
