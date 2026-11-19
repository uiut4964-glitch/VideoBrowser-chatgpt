package com.example.videobrowser

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

fun main() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/range-only") { exchange ->
        val range = exchange.requestHeaders.getFirst("Range")
        when {
            range != null -> exchange.sendResponseHeaders(410, -1)
            exchange.requestMethod == "HEAD" -> exchange.sendResponseHeaders(405, -1)
            else -> {
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                exchange.sendResponseHeaders(200, 1)
                exchange.responseBody.use { it.write(0) }
            }
        }
        exchange.close()
    }
    server.createContext("/gone") { exchange ->
        exchange.sendResponseHeaders(410, -1)
        exchange.close()
    }
    server.start()
    try {
        val base = "http://127.0.0.1:${server.address.port}"
        val available = Net.probe("$base/range-only", emptyMap())
        check(!available.rangeOk)
        check(available.mime == "video/mp4")
        check(available.total == 1L)

        val error = runCatching { Net.probe("$base/gone", emptyMap()) }.exceptionOrNull()
        check(error != null)
        check(error!!.message.orEmpty().contains("HTTP 410"))
        check(error.message.orEmpty().contains("Range"))
        check(error.message.orEmpty().contains("HEAD"))

        println("Net probe fallback test passed: Range-only 410 recovered by header-only GET; genuinely gone URL remains HTTP 410")
    } finally {
        server.stop(0)
    }
}
