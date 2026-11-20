package com.jfdedit3.bytrolauncher

import android.net.Uri
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * First-party extractor: contacts ONLY X/Twitter-operated hosts.
 * Does not call FxTwitter, FxEmbed, Nitter or any external extraction service.
 */
object XPublicExtractor {
    data class Result(val code: Int, val json: JSONObject)
    private data class Response(val code: Int, val content: String)

    fun resolve(raw: String?): Result {
        if (raw.isNullOrBlank() || raw.length > 2048) return failure(400, "invalid_url", "Enter an X profile or post URL.")
        val uri = try {
            val value = raw.trim()
            Uri.parse(if ("://" !in value) "https://$value" else value)
        } catch (_: Exception) { return failure(400, "invalid_url", "Invalid URL.") }
        val allowedHosts = setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")
        if (uri.scheme?.lowercase(Locale.ROOT) != "https" || uri.host?.lowercase(Locale.ROOT) !in allowedHosts) {
            return failure(400, "invalid_url", "Only HTTPS x.com or twitter.com URLs are allowed.")
        }
        val route = uri.path.orEmpty().trimEnd('/')
        val post = Regex("^/([A-Za-z0-9_]{1,15})/status/([0-9]{1,20})(?:/(?:photo|video)/[0-9]+)?$").matchEntire(route)
        if (post != null) return post(post.groupValues[2], post.groupValues[1])
        val profile = Regex("^/([A-Za-z0-9_]{1,15})$").matchEntire(route)
        if (profile != null) return profile(profile.groupValues[1])
        return failure(400, "unsupported_url", "Supported formats: x.com/user and x.com/user/status/123.")
    }

    fun post(id: String, username: String? = null): Result {
        if (!id.matches(Regex("[0-9]{1,20}"))) return failure(400, "invalid_id", "Numeric post ID required.")
        // X syndication accepts a nonempty token. A fixed token avoids subtle
        // JavaScript-to-Kotlin base36 floating point conversion differences.
        val endpoint = "https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=en&token=x"
        val widget = get(endpoint)
        var syndicationKind = "no_json"
        var responseKeys = JSONArray()
        if (widget.code == 200) {
            try {
                val json = JSONObject(widget.content)
                responseKeys = JSONArray(json.keys().asSequence().take(16).toList())
                syndicationKind = json.optString("__typename", "unknown")
                // id_str is a lossless string; JSON numeric IDs can exceed 2^53.
                val actualId = json.optString("id_str").ifBlank {
                    json.opt("id")?.toString().orEmpty()
                }
                if (actualId == id && json.optJSONObject("user") != null) {
                    return Result(200, normalizePost(json, id, "x_syndication"))
                }
                if (json.length() == 0) syndicationKind = "empty_object"
                if (json.optString("__typename") == "TweetTombstone") {
                    syndicationKind = "TweetTombstone"
                }
            } catch (_: Exception) {
                syndicationKind = "invalid_json"
            }
        }
        // Fallback: parse the OG metadata of the X.com page, not a third-party service.
        if (username != null && username.matches(Regex("[A-Za-z0-9_]{1,15}"))) {
            val webUrl = "https://x.com/$username/status/$id"
            val html = get(webUrl, true)
            if (html.code == 200) {
                val title = meta(html.content, "og:title")
                val description = meta(html.content, "og:description")
                val image = meta(html.content, "og:image")
                if (!description.isNullOrBlank() && !description.contains("Log in to X", true)) {
                    val media = JSONArray()
                    if (image?.startsWith("https://pbs.twimg.com/") == true) {
                        media.put(JSONObject().put("type", "image").put("url", image))
                    }
                    return Result(200, JSONObject()
                        .put("type", "post").put("id", id).put("username", username)
                        .put("url", webUrl).put("text", description).put("title", title)
                        .put("media", media).put("source", "x_html_metadata")
                        .put("partial", true))
                }
            }
        }
        return failure(404, "post_unavailable",
            "X did not return public post data. The post may be restricted, deleted, or blocked to logged-out visitors.",
            JSONObject()
                .put("id", id)
                .put("syndication_status", widget.code)
                .put("syndication_type", syndicationKind)
                .put("syndication_keys", responseKeys)
                .put("response_size", widget.content.length))
    }

    fun profile(username: String): Result {
        if (!username.matches(Regex("[A-Za-z0-9_]{1,15}"))) {
            return failure(400, "invalid_username", "Invalid X username.")
        }
        val result = get("https://x.com/$username", true)
        if (result.code != 200) {
            return failure(502, "profile_unavailable", "X did not expose this public profile.",
                JSONObject().put("upstream_status", result.code))
        }
        val title = meta(result.content, "og:title")
        val description = meta(result.content, "og:description")
        val image = meta(result.content, "og:image")
        if (title.isNullOrBlank() && description.isNullOrBlank()) {
            return failure(404, "profile_unavailable", "No public profile metadata returned by X.")
        }
        return Result(200, JSONObject()
            .put("type", "profile").put("username", username)
            .put("url", "https://x.com/$username").put("name", title)
            .put("bio", description).put("image", image)
            .put("source", "x_html_metadata").put("partial", true))
    }

    private fun normalizePost(input: JSONObject, id: String, source: String): JSONObject {
        val user = input.optJSONObject("user")
        val media = JSONArray()
        val details = input.optJSONArray("mediaDetails")
            ?: input.optJSONObject("entities")?.optJSONArray("media")
        if (details != null) {
            for (i in 0 until details.length()) {
                val item = details.optJSONObject(i) ?: continue
                val kind = item.optString("type", "photo")
                val parsed = JSONObject().put("type", kind)
                val url = item.optString("media_url_https")
                if (url.startsWith("https://pbs.twimg.com/")) parsed.put("url", url)
                val variants = item.optJSONObject("video_info")?.optJSONArray("variants")
                if (variants != null) {
                    val videoVariants = JSONArray()
                    for (j in 0 until variants.length()) {
                        val variant = variants.optJSONObject(j) ?: continue
                        val videoUrl = variant.optString("url")
                        if (videoUrl.startsWith("https://video.twimg.com/")) {
                            videoVariants.put(JSONObject()
                                .put("url", videoUrl)
                                .put("content_type", variant.optString("content_type"))
                                .put("bitrate", variant.optInt("bitrate")))
                        }
                    }
                    parsed.put("variants", videoVariants)
                }
                media.put(parsed)
            }
        }
        val screenName = user?.optString("screen_name").orEmpty()
        return JSONObject()
            .put("type", "post").put("id", id)
            .put("url", if (screenName.isNotEmpty()) "https://x.com/$screenName/status/$id" else "https://x.com/i/status/$id")
            .put("text", input.optString("text"))
            .put("created_at", input.optString("created_at"))
            .put("author", JSONObject()
                .put("username", screenName)
                .put("name", user?.optString("name"))
                .put("avatar", user?.optString("profile_image_url_https")))
            .put("like_count", input.optInt("favorite_count"))
            .put("reply_count", input.optInt("conversation_count"))
            .put("media", media)
            .put("source", source).put("partial", false)
    }

    private fun get(url: String, acceptHtml: Boolean = false): Response {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", if (acceptHtml) "text/html" else "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36")
            val status = conn.responseCode
            // Do not follow redirects to login or any non-X domain.
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 2_000_000) return Response(502, "")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }.orEmpty()
            Response(status, body)
        } catch (_: Exception) { Response(502, "") }
        finally { conn?.disconnect() }
    }

    private fun meta(html: String, key: String): String? {
        val tags = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attrs = Regex("""([\w:-]+)\s*=\s*["']([^"']*)["']""")
        for (tag in tags.findAll(html)) {
            val values = attrs.findAll(tag.value).associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] }
            if (values["property"] == key || values["name"] == key) {
                return Html.fromHtml(values["content"].orEmpty(), Html.FROM_HTML_MODE_LEGACY).toString()
            }
        }
        return null
    }

    private fun failure(code: Int, name: String, message: String, details: JSONObject? = null): Result {
        val obj = JSONObject().put("error", name).put("message", message)
        if (details != null) obj.put("details", details)
        return Result(code, obj)
    }
}
