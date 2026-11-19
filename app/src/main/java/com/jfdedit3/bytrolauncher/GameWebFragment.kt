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
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.jfdedit3.bytrolauncher.databinding.FragmentGameWebBinding

class GameWebFragment : Fragment() {
    private var _binding: FragmentGameWebBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val gameUrl: String by lazy { requireArguments().getString(ARG_URL).orEmpty() }
    private var webViewState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webViewState = savedInstanceState?.getBundle(STATE_WEBVIEW)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGameWebBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val webView = binding.webView
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        binding.swipeRefresh.setOnRefreshListener { webView.reload() }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, progress: Int) {
                val currentBinding = _binding ?: return
                currentBinding.progressBar.progress = progress
                currentBinding.progressBar.visibility =
                    if (progress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "https" && scheme != "http"
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                _binding?.swipeRefresh?.isRefreshing = true
                if (isResumed) (activity as? MainActivity)?.updateUrlBox(url ?: gameUrl)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                _binding?.swipeRefresh?.isRefreshing = false
                if (isResumed) (activity as? MainActivity)?.updateUrlBox(url ?: gameUrl)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    _binding?.swipeRefresh?.isRefreshing = false
                    if (isAdded) Toast.makeText(requireContext(), "Page inaccessible : vérifiez votre connexion", Toast.LENGTH_LONG).show()
                }
            }
        }

        val state = savedInstanceState?.getBundle(STATE_WEBVIEW) ?: webViewState
        val restored = state?.let { webView.restoreState(it) } != null
        if (!restored) webView.loadUrl(gameUrl)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = Bundle()
        if (_binding != null) {
            binding.webView.saveState(state)
            outState.putBundle(STATE_WEBVIEW, state)
        } else if (webViewState != null) {
            outState.putBundle(STATE_WEBVIEW, webViewState)
        }
    }

    fun reloadPage() {
        _binding?.webView?.reload()
    }

    fun loadCustomUrl(url: String) {
        val normalized = normalizeUrl(url) ?: run {
            if (isAdded) Toast.makeText(requireContext(), "Adresse web invalide", Toast.LENGTH_SHORT).show()
            return
        }
        _binding?.webView?.loadUrl(normalized)
    }

    fun getCurrentUrl(): String = _binding?.webView?.url ?: gameUrl
    fun canGoBack(): Boolean = _binding?.webView?.canGoBack() == true
    fun goBack() { _binding?.webView?.goBack() }

    private fun normalizeUrl(url: String): String? {
        val value = url.trim()
        if (value.isEmpty()) return null
        val candidate = if ("://" in value) value else "https://$value"
        val uri = android.net.Uri.parse(candidate)
        return candidate.takeIf {
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.webView?.onResume()
        if (_binding != null) (activity as? MainActivity)?.updateUrlBox(getCurrentUrl())
    }

    override fun onPause() {
        _binding?.webView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        val state = Bundle()
        binding.webView.saveState(state)
        webViewState = state
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
        private const val STATE_WEBVIEW = "webview_state"

        fun newInstance(gameItem: GameItem): GameWebFragment = GameWebFragment().apply {
            arguments = bundleOf(ARG_TITLE to gameItem.title, ARG_URL to gameItem.url)
        }
    }
}
