package com.jfdedit3.bytrolauncher

import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
 * GET /v1/resolve?url=https://x.com/user/status/123\n * GET /v1/posts/public/{id} (no token, publicly available only)
 * GET /v1/posts/{id}    Authorization: Bearer <official X API token>
 */
class LocalXApiServer(private val port: Int = 8787) {
    private val active = AtomicBoolean(false)
    private var pool = Executors.newFixedThreadPool(4)
    private var socket: ServerSocket? = null

    val isRunning: Boolean get() = active.get()

    @Synchronized
    fun start() {
        if (active.get()) return
        if (pool.isShutdown) pool = Executors.newFixedThreadPool(4)
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
                    path == "/v1/posts/oembed" || path == "/v1/resolve" -> {
                        val input = uri.getQueryParameter("url")
                        val target = parseXUrl(input)
                        if (target == null) {
                            reply(connection, 400, error("Provide an X.com profile or status URL"))
                        } else {
                            val result = when (target) {
                                is XTarget.Post -> fetchPublicPost(target)
                                is XTarget.Profile -> fetchPublicProfile(target)
                            }
                            reply(connection, result.first, result.second)
                        }
                    }
                    path.matches(Regex("^/v1/posts/public/[0-9]{1,20}$")) -> {
                        val id = path.substringAfterLast('/')
                        val result = fetch("https://api.fxtwitter.com/2/status/$id", null)
                        reply(connection, result.first, decorate(result, "fxembed", "post"))
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

    private sealed class XTarget {
        data class Post(val user: String, val id: String) : XTarget()
        data class Profile(val user: String) : XTarget()
    }

    private fun parseXUrl(input: String?): XTarget? {
        if (input.isNullOrBlank() || input.length > 2048) return null
        return try {
            val raw = input.trim()
            val value = if (raw.startsWith("https://", ignoreCase = true)) raw
                else if (raw.startsWith("http://", ignoreCase = true)) return null
                else "https://$raw"
            val uri = Uri.parse(value)
            if (uri.scheme?.lowercase() != "https") return null
            val host = uri.host?.lowercase() ?: return null
            if (host !in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")) return null
            val path = uri.path.orEmpty().trimEnd('/')
            val post = Regex("^/([A-Za-z0-9_]{1,15})/status/([0-9]{1,20})(?:/(?:photo|video)/[0-9]+)?$").matchEntire(path)
            if (post != null) return XTarget.Post(post.groupValues[1], post.groupValues[2])
            val profile = Regex("^/([A-Za-z0-9_]{1,15})$").matchEntire(path)
            if (profile != null) XTarget.Profile(profile.groupValues[1]) else null
        } catch (_: Exception) { null }
    }

    private fun fetchPublicPost(post: XTarget.Post): Pair<Int, String> {
        // FxEmbed exposes publicly available post metadata, including available media.
        // It cannot access protected/deleted posts or override X access restrictions.
        val fx = fetch("https://api.fxtwitter.com/2/status/${post.id}", null)
        if (fx.first in 200..299 && hasPost(fx.second)) {
            return fx.first to decorate(fx, "fxembed", "post")
        }
        val normalized = "https://x.com/${post.user}/status/${post.id}"
        val embed = fetch(
            "https://publish.x.com/oembed?omit_script=true&url=" + URLEncoder.encode(normalized, "UTF-8"),
            null
        )
        if (embed.first in 200..299) return embed.first to decorate(embed, "x_oembed", "embed")
        return 404 to JSONObject()
            .put("error", "Post unavailable from public sources")
            .put("message", "X or the public provider cannot serve this post. It may be restricted, deleted or unavailable.")
            .put("post_id", post.id)
            .put("fx_status", fx.first)
            .put("oembed_status", embed.first)
            .toString()
    }

    private fun fetchPublicProfile(profile: XTarget.Profile): Pair<Int, String> {
        val result = fetch("https://api.fxtwitter.com/" + profile.user, null)
        if (result.first in 200..299) return result.first to decorate(result, "fxembed", "profile")
        return 404 to JSONObject()
            .put("error", "Public profile not available")
            .put("username", profile.user)
            .put("upstream_status", result.first)
            .toString()
    }

    private fun hasPost(body: String): Boolean = runCatching {
        val json = JSONObject(body)
        json.optJSONObject("status") != null || json.optJSONObject("tweet") != null
    }.getOrDefault(false)

    private fun decorate(result: Pair<Int, String>, source: String, type: String): String {
        return runCatching {
            JSONObject(result.second).put("source", source).put("type", type).toString()
        }.getOrElse { result.second }
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
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    if (total > 1024 * 1024) throw IllegalStateException("Response too large")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
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
