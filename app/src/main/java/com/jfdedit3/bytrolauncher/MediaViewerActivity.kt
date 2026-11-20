package com.jfdedit3.bytrolauncher

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import java.util.concurrent.Executors

/** Fullscreen image viewer and video player using only content:// MediaStore URIs. */
class MediaViewerActivity : ImmersiveActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var video: VideoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra("media_uri")?.let(Uri::parse)
        if (uri == null || uri.scheme != "content") {
            finish()
            return
        }
        val root = FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.BLACK)
        setContentView(root)

        if (intent.getBooleanExtra("is_video", false)) {
            val player = VideoView(this)
            video = player
            root.addView(player, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            val controls = MediaController(this)
            player.setMediaController(controls)
            player.setVideoURI(uri)
            player.setOnPreparedListener { media ->
                media.isLooping = false
                player.start()
                controls.show(3000)
            }
            player.setOnErrorListener { _, _, _ ->
                showError(root, "Impossible de lire cette vidéo")
                true
            }
        } else {
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            root.addView(image, FrameLayout.LayoutParams(-1, -1))
            worker.execute {
                val bitmap = runCatching {
                    if (Build.VERSION.SDK_INT >= 28) {
                        val source = ImageDecoder.createSource(contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            val width = info.size.width
                            val height = info.size.height
                            val longest = maxOf(width, height)
                            if (longest > 4096) {
                                decoder.setTargetSize(
                                    (width * 4096L / longest).toInt().coerceAtLeast(1),
                                    (height * 4096L / longest).toInt().coerceAtLeast(1)
                                )
                            }
                        }
                    } else {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }
                }.getOrNull()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (bitmap == null) showError(root, "Impossible d'ouvrir cette photo")
                    else image.setImageBitmap(bitmap)
                }
            }
        }

        val close = TextView(this).apply {
            text = "✕"
            textSize = 25f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0x66000000)
            setOnClickListener { finish() }
        }
        val closeParams = FrameLayout.LayoutParams(58, 58, Gravity.TOP or Gravity.END)
        root.addView(close, closeParams)
    }

    private fun showError(root: FrameLayout, text: String) {
        val error = TextView(this).apply {
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(error, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onPause() {
        video?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        video?.stopPlayback()
        super.onDestroy()
    }
}
