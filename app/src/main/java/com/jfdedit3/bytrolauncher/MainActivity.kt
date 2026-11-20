package com.jfdedit3.bytrolauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jfdedit3.bytrolauncher.databinding.ActivityMainBinding
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var server: LocalXApiServer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainThread = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startApi()

        binding.serverButton.setOnClickListener {
            if (server?.isRunning == true) {
                server?.stop()
                server = null
                setStatus("Server stopped")
            } else startApi()
        }
        binding.previewButton.setOnClickListener {
            val url = binding.postUrl.text.toString().trim()
            if (url.isEmpty()) {
                binding.resultText.text = "Enter an X.com profile or post URL."
                return@setOnClickListener
            }
            binding.resultText.text = "Loading..."
            val endpoint = "http://127.0.0.1:8787/v1/resolve?url=" +
                URLEncoder.encode(url, "UTF-8")
            executor.execute {
                val response = try {
                    val conn = URL(endpoint).openConnection() as HttpURLConnection
                    conn.connectTimeout = 11000
                    conn.readTimeout = 26000
                    try {
                        val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
                            .bufferedReader().use { it.readText() }
                        body
                    } finally { conn.disconnect() }
                } catch (e: Exception) { "Request failed: ${e.message}" }
                mainThread.post { if (!isFinishing && !isDestroyed) binding.resultText.text = response }
            }
        }
        binding.copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("X Local API", "http://127.0.0.1:8787/health"))
            Toast.makeText(this, "API endpoint copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startApi() {
        try {
            server = LocalXApiServer().also { it.start() }
            setStatus("● Running on http://127.0.0.1:8787")
        } catch (e: Exception) {
            setStatus("Cannot start local API: ${e.message}")
        }
    }

    private fun setStatus(message: String) {
        binding.serverStatus.text = message
        binding.serverButton.text = if (server?.isRunning == true) "Stop server" else "Start server"
    }

    override fun onDestroy() {
        server?.stop()
        executor.shutdownNow()
        super.onDestroy()
    }
}
