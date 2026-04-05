package com.jfdedit3.bytrolauncher

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.jfdedit3.bytrolauncher.databinding.FragmentGameWebBinding

class GameWebFragment : Fragment() {
    private var _binding: FragmentGameWebBinding? = null
    private val binding get() = _binding!!

    private val gameUrl: String by lazy { requireArguments().getString(ARG_URL).orEmpty() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameWebBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            userAgentString = userAgentString + " BytroLauncher/1.0"
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                return !(url.startsWith("http://") || url.startsWith("https://"))
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.swipeRefresh.isRefreshing = true
                if (isVisible) {
                    (activity as? MainActivity)?.updateUrlBox(url ?: gameUrl)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.swipeRefresh.isRefreshing = false
                if (isVisible) {
                    (activity as? MainActivity)?.updateUrlBox(url ?: gameUrl)
                }
            }
        }

        if (savedInstanceState == null) {
            binding.webView.loadUrl(gameUrl)
        }
    }

    fun reloadPage() {
        if (_binding != null) binding.webView.reload()
    }

    fun loadCustomUrl(url: String) {
        if (_binding == null) return
        val finalUrl = normalizeUrl(url)
        binding.webView.loadUrl(finalUrl)
    }

    fun getCurrentUrl(): String {
        return _binding?.webView?.url ?: gameUrl
    }

    private fun normalizeUrl(url: String): String {
        val value = url.trim()
        return if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        (activity as? MainActivity)?.updateUrlBox(getCurrentUrl())
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        binding.webView.stopLoading()
        binding.webView.webChromeClient = null
        binding.webView.webViewClient = WebViewClient()
        binding.webView.destroy()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_URL = "arg_url"

        fun newInstance(gameItem: GameItem): GameWebFragment {
            return GameWebFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to gameItem.title,
                    ARG_URL to gameItem.url
                )
            }
        }
    }
}
