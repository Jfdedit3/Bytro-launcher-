package com.jfdedit3.bytrolauncher

import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local-only HTTP API. No token is stored or included in the app.
 * GET /health
 * GET /v1/posts/oembed?url=https://x.com/user/status/123
 * GET /v1/posts/{id}    Authorization: Bearer <official X API token>
 */
class LocalXApiServer(private val port: Int = 8787) {
    private val active = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(4)
    private var socket: ServerSocket? = null

    val isRunning: Boolean get() = active.get()

    @Synchronized
    fun start() {
        if (active.get()) return
        val server = ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))
        socket = server
        active.set(true)
        Thread({
            while (active.get()) {
                try {
                    val client = server.accept()
                    pool.execute { handle(client) }
                } catch (_: Exception) {
                    if (!active.get()) break
                }
            }
        }, "x-api-listener").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        active.set(false)
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        pool.shutdownNow()
    }

    private fun handle(client: Socket) {
        client.use { connection ->
            try {
                connection.soTimeout = 15000
                val reader = BufferedReader(InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size != 3) {
                    reply(connection, 400, error("Malformed request"))
                    return
                }
                val headers = mutableMapOf<String, String>()
                var count = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (++count > 64 || line.length > 8192) {
                        reply(connection, 400, error("Headers too large"))
                        return
                    }
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).lowercase()] = line.substring(colon + 1).trim()
                }
                if (parts[0] != "GET") {
                    reply(connection, 405, error("Only GET supported"))
                    return
                }
                val uri = Uri.parse(parts[1])
                val path = uri.path.orEmpty()
                when {
                    path == "/health" -> reply(connection, 200, JSONObject()
                        .put("ok", true).put("service", "X Local API").put("version", "1.0").toString())
                    path == "/v1/posts/oembed" -> {
                        val postUrl = uri.getQueryParameter("url")
                        val normalized = normalizePostUrl(postUrl)
                        if (normalized == null) reply(connection, 400, error("Valid public X status URL required"))
                        else {
                            val upstream = "https://publish.x.com/oembed?omit_script=true&url=" +
                                URLEncoder.encode(normalized, "UTF-8")
                            val result = fetch(upstream, null)
                            reply(connection, result.first, result.second)
                        }
                    }
                    path.matches(Regex("^/v1/posts/[0-9]{1,20}$")) -> {
                        val bearer = headers["authorization"]
                        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length <= 7) {
                            reply(connection, 401, error("Official X API Bearer token required"))
                        } else {
                            val id = path.substringAfterLast('/')
                            val endpoint = "https://api.x.com/2/tweets/$id?tweet.fields=created_at,author_id,public_metrics,attachments&expansions=attachments.media_keys&media.fields=url,preview_image_url,type"
                            val result = fetch(endpoint, bearer)
                            reply(connection, result.first, result.second)
                        }
                    }
                    else -> reply(connection, 404, error("Unknown endpoint"))
                }
            } catch (_: Exception) {
                runCatching { reply(connection, 500, error("Request processing failed")) }
            }
        }
    }

    private fun normalizePostUrl(input: String?): String? {
        if (input.isNullOrBlank() || input.length > 2048) return null
        return try {
            val uri = Uri.parse(input.trim())
            if (uri.scheme?.lowercase() != "https") return null
            val host = uri.host?.lowercase() ?: return null
            if (host !in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")) return null
            val match = Regex("^/([A-Za-z0-9_]{1,15})/status/([0-9]{1,20})/?$").matchEntire(uri.path.orEmpty())
                ?: return null
            "https://x.com/${match.groupValues[1]}/status/${match.groupValues[2]}"
        } catch (_: Exception) { null }
    }

    private fun fetch(url: String, bearer: String?): Pair<Int, String> {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "XLocalApi/1.0")
            if (bearer != null) connection.setRequestProperty("Authorization", bearer)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val bytes = input.readNBytes(1024 * 1024 + 1)
                if (bytes.size > 1024 * 1024) throw IllegalStateException("Response too large")
                String(bytes, StandardCharsets.UTF_8)
            }.orEmpty()
            val json = runCatching { JSONObject(body).toString() }.getOrElse {
                error("Upstream returned non-JSON content")
            }
            Pair(if (status in 100..599) status else 502, json)
        } catch (_: Exception) {
            Pair(502, error("Unable to contact X; check connectivity and API permissions"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun error(message: String): String = JSONObject().put("error", message).toString()

    private fun reply(client: Socket, status: Int, body: String) {
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
            404 -> "Not Found"; 405 -> "Method Not Allowed"; 429 -> "Too Many Requests"
            502 -> "Bad Gateway"; 500 -> "Internal Server Error"; else -> "Upstream Response"
        }
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${payload.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        val out = client.getOutputStream()
        out.write(head.toByteArray(StandardCharsets.UTF_8))
        out.write(payload)
        out.flush()
    }
}
