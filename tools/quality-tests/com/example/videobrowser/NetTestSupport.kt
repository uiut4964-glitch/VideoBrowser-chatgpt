package com.example.videobrowser

import java.io.IOException

/** Test-only replacement for network helpers; never included in the Android app source set. */
object Net {
    class Probe(val total: Long, val rangeOk: Boolean, val mime: String)
    val docs = mutableMapOf<String, String>()
    val probes = mutableMapOf<String, Probe>()
    fun fetchText(url: String, h: Map<String, String>): String =
        docs[url] ?: throw IOException("missing test manifest: $url")
    fun readRangeBytes(url: String, h: Map<String, String>, s: Long, l: Long): ByteArray =
        ByteArray(l.coerceAtMost(188).toInt())
    fun readBytes(url: String, h: Map<String, String>): ByteArray = ByteArray(188)
    fun probe(url: String, h: Map<String, String>): Probe =
        probes[url] ?: Probe(1_000_000L, true, "video/mp4")
}

object Util {
    fun fmtSize(bytes: Long) = "$bytes bytes"
    fun host(url: String) = "cdn.example"
}

object Sps {
    fun fromAvcC(data: ByteArray): IntArray? = null
    fun fromTs(data: ByteArray): IntArray? = null
}
