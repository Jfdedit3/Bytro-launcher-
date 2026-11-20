package com.jfdedit3.bytrolauncher

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONTokener

/** Opens X in its own WebView. Never exports credentials or cookies. */
class XBrowserActivity : ImmersiveActivity() {
    private lateinit var browser: WebView
    private lateinit var status: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 18, 12, 12)
        }
        status = TextView(this).apply { text = "Sign in to X, then open a post." }
        val extract = Button(this).apply { text = "Extract visible post JSON" }
        val back = Button(this).apply { text = "Back to API" }
        browser = WebView(this)
        layout.addView(status)
        layout.addView(extract)
        layout.addView(back)
        layout.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        browser.webChromeClient = WebChromeClient()
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val target = request.url
                val host = target.host?.lowercase().orEmpty()
                val allowed = target.scheme == "https" &&
                    (host == "x.com" || host.endsWith(".x.com") ||
                     host == "twitter.com" || host.endsWith(".twitter.com"))
                if (!allowed) {
                    if (target.scheme == "https")
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, target)) }
                    return true
                }
                return false
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                status.text = if (url?.contains("/status/") == true)
                    "Post opened. Tap Extract visible post JSON."
                else "Sign in to X, then open a post."
            }
        }
        extract.setOnClickListener { extractPost() }
        back.setOnClickListener { finish() }
        val input = intent.getStringExtra("url").orEmpty()
        val uri = Uri.parse(input)
        val host = uri.host?.lowercase().orEmpty()
        val url = if (uri.scheme == "https" && host in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com"))
            input else "https://x.com/"
        browser.loadUrl(url)
    }

    private fun extractPost() {
        val id = Regex("/status/([0-9]{1,20})").find(browser.url.orEmpty())?.groupValues?.get(1)
        if (id == null) {
            Toast.makeText(this, "Open a post on X first", Toast.LENGTH_SHORT).show()
            return
        }
        val script = """
            (function() {
              try {
                const id = POST_ID_JSON;
                const articles = Array.from(document.querySelectorAll('article'));
                const article = articles.find(a =>
                  Array.from(a.querySelectorAll('a[href*="/status/"]')).some(link => {
                    const href = link.getAttribute('href') || '';
                    return href.includes('/status/' + id) &&
                      (href.split('/status/')[1] || '').match(new RegExp('^' + id + '(?:[^0-9]|$)'));
                  })
                );
                if (!article) return JSON.stringify({error:"post_not_rendered", articles:articles.length});
                const text = article.querySelector('[data-testid="tweetText"]');
                const images = Array.from(article.querySelectorAll('img'))
                  .map(e => e.currentSrc || e.src)
                  .filter(url => url.startsWith('https://pbs.twimg.com/media/'));
                const videos = Array.from(article.querySelectorAll('video'))
                  .map(e => e.currentSrc || e.src)
                  .filter(url => url.startsWith('https://video.twimg.com/'));
                const usernameAnchor = article.querySelector('[data-testid="User-Name"] a[href^="/"]');
                const handle = usernameAnchor ? usernameAnchor.getAttribute('href').split('/')[1] : '';
                const result = {
                  type: "post", id: id, url: location.href,
                  text: text ? text.innerText : "",
                  author: {username: handle},
                  media: Array.from(new Set(images)).map(url => ({type:"image", url:url}))
                    .concat(Array.from(new Set(videos)).map(url => ({type:"video", url:url}))),
                  source: "x_authenticated_dom",
                  partial: true
                };
                return JSON.stringify(result);
              } catch (e) {
                return JSON.stringify({error:"javascript_error", message:String(e)});
              }
            })();
        """.trimIndent().replace("POST_ID_JSON", JSONObject.quote(id))
        browser.evaluateJavascript(script) { raw ->
            try {
                if (raw.isNullOrBlank() || raw == "null") {
                    status.text = "X page did not return script data. Reload X and retry."
                    return@evaluateJavascript
                }
                val decoded = JSONTokener(raw).nextValue() as? String
                    ?: throw IllegalStateException("Unexpected WebView result: " + raw.take(140))
                val json = JSONObject(decoded)
                when (json.optString("error")) {
                    "post_not_rendered" -> {
                        status.text = "Post not visible in X (articles: " +
                            json.optInt("articles") + "). Wait for loading or scroll to it."
                    }
                    "javascript_error" -> {
                        status.text = "Extractor JavaScript error: " + json.optString("message")
                    }
                    "" -> {
                        XSessionCache.save(json.toString())
                        status.text = "Post extracted. Return to API to view JSON."
                        Toast.makeText(this, "Post captured locally", Toast.LENGTH_SHORT).show()
                    }
                    else -> status.text = "Extraction error: " + json.optString("error")
                }
            } catch (error: Exception) {
                status.text = "JSON decoding error: " + (error.message ?: error.javaClass.simpleName)
            }
        }
    }

    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }
}
