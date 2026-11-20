package com.jfdedit3.bytrolauncher

import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** FxTwitter API v2 client. No direct X syndication calls. */
object XPublicExtractor {
    data class Result(val code: Int, val json: JSONObject)

    fun resolve(raw: String?): Result {
        if (raw.isNullOrBlank() || raw.length > 2048) return fail(400, "invalid_url", "Enter an X post or profile URL.")
        val uri = try {
            val value = raw.trim()
            if (value.startsWith("http://", true)) return fail(400, "invalid_url", "HTTPS only.")
            Uri.parse(if ("://" !in value) "https://$value" else value)
        } catch (_: Exception) { return fail(400, "invalid_url", "Invalid URL.") }
        if (uri.scheme?.lowercase(Locale.ROOT) != "https" ||
            uri.host?.lowercase(Locale.ROOT) !in setOf(
                "x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com"
            )) return fail(400, "invalid_url", "Only X.com or Twitter.com HTTPS URLs accepted.")

        val path = uri.path.orEmpty().trimEnd('/')
        val tweet = Regex("^/([A-Za-z0-9_]{1,15})/status/([0-9]{2,20})(?:/(?:photo|video)/[0-9]+)?$")
            .matchEntire(path)
        if (tweet != null) return post(tweet.groupValues[2])
        val user = Regex("^/([A-Za-z0-9_]{1,15})$").matchEntire(path)
        if (user != null) return profile(user.groupValues[1])
        return fail(400, "unsupported_url", "Use x.com/user/status/123 or x.com/user.")
    }

    fun post(id: String, username: String? = null): Result {
        if (!id.matches(Regex("[0-9]{2,20}"))) return fail(400, "invalid_id", "Invalid post ID.")
        return request("https://api.fxtwitter.com/2/status/$id", "status")
    }

    fun profile(username: String): Result {
        if (!username.matches(Regex("[A-Za-z0-9_]{1,15}")))
            return fail(400, "invalid_username", "Invalid username.")
        return request("https://api.fxtwitter.com/2/profile/$username", "user")
    }

    private fun request(url: String, field: String): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 16000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "XLocalAPI-Android/2.3")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val content = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > 4_000_000)
                        return fail(502, "response_too_large", "Response exceeds size limit.")
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }.orEmpty()
            val json = try { JSONObject(content) } catch (_: Exception) {
                return fail(502, "invalid_json", "FxTwitter returned invalid JSON.")
            }
            val code = json.optInt("code", status)
            val effectiveCode = when {
                status !in 200..299 -> status
                code in 400..599 -> code
                else -> status
            }
            json.put("source", "fxtwitter_api_v2")
            if (effectiveCode in 200..299 && json.optJSONObject(field) == null) {
                return fail(502, "missing_data", "FxTwitter returned no requested data.")
            }
            Result(effectiveCode, json)
        } catch (_: Exception) {
            fail(502, "network_error", "Unable to contact api.fxtwitter.com.")
        } finally { connection?.disconnect() }
    }

    private fun fail(code: Int, kind: String, message: String): Result =
        Result(code, JSONObject().put("code", code).put("error", kind)
            .put("message", message).put("source", "fxtwitter_api_v2"))
}
