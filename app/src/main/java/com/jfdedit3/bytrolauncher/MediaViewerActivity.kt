package com.jfdedit3.bytrolauncher

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import java.util.concurrent.Executors

/** Fullscreen media player with mockup-style overlay and usable actions. */
class MediaViewerActivity : ImmersiveActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var video: VideoView? = null
    private lateinit var mediaUri: Uri
    private var isVideo = false
    private val prefs by lazy { getSharedPreferences("gallery_favorites", MODE_PRIVATE) }

    private val deletionRequest = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parsed = intent.getStringExtra("media_uri")?.let(Uri::parse)
        if (parsed == null || parsed.scheme != "content") {
            finish()
            return
        }
        mediaUri = parsed
        isVideo = intent.getBooleanExtra("is_video", false)
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF0B0916.toInt()) }
        setContentView(root)

        if (isVideo) {
            val player = VideoView(this)
            video = player
            root.addView(player, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            val controls = MediaController(this)
            player.setMediaController(controls)
            player.setVideoURI(mediaUri)
            player.setOnPreparedListener {
                it.isLooping = false
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
                        val source = ImageDecoder.createSource(contentResolver, mediaUri)
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            val w = info.size.width
                            val h = info.size.height
                            val longest = maxOf(w, h)
                            if (longest > 4096) {
                                decoder.setTargetSize(
                                    (w * 4096L / longest).toInt().coerceAtLeast(1),
                                    (h * 4096L / longest).toInt().coerceAtLeast(1)
                                )
                            }
                        }
                    } else contentResolver.openInputStream(mediaUri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (bitmap == null) showError(root, "Impossible d'ouvrir cette photo")
                    else image.setImageBitmap(bitmap)
                }
            }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(10))
            setBackgroundColor(0xCC0D0A1D.toInt())
        }
        val back = action("‹", 32f) { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(42), dp(50)))
        val title = TextView(this).apply {
            text = intent.getStringExtra("media_name") ?: "Média"
            setTextColor(-1)
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val heart = action(if (isFavorite()) "♥" else "♡", 29f) {
            val newState = !isFavorite()
            prefs.edit().putBoolean(mediaUri.toString(), newState).apply()
            top.getChildAt(2).let { (it as TextView).text = if (newState) "♥" else "♡" }
        }
        top.addView(heart, LinearLayout.LayoutParams(dp(44), dp(48)))
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(12), dp(8), dp(18))
            setBackgroundColor(0xED1B162D.toInt())
        }
        actions.addView(bottomAction("♧", "Partager") { share() })
        actions.addView(bottomAction("✎", "Modifier") { edit() })
        actions.addView(bottomAction("ⓘ", "Informations") { details() })
        actions.addView(bottomAction("♜", "Supprimer") { askDelete() })
        root.addView(actions, FrameLayout.LayoutParams(-1, dp(86), Gravity.BOTTOM))
    }

    private fun action(label: String, size: Float, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = size
            setTextColor(-1)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }

    private fun bottomAction(icon: String, name: String, onClick: () -> Unit): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
        item.addView(TextView(this).apply {
            text = icon
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(-1)
        })
        item.addView(TextView(this).apply {
            text = name
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(-1)
        })
        item.layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        return item
    }

    private fun isFavorite() = prefs.getBoolean(mediaUri.toString(), false)

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, mediaUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Partager le média"))
    }

    private fun edit() {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(mediaUri, if (isVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else AlertDialog.Builder(this).setMessage("Aucun éditeur compatible installé.")
            .setPositiveButton("OK", null).show()
    }

    private fun details() {
        val name = intent.getStringExtra("media_name") ?: "Média"
        val meta = contentResolver.query(mediaUri,
            arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE),
            null, null, null)?.use {
            if (it.moveToFirst()) {
                val bytes = it.getLong(0)
                "Taille : " + String.format("%.2f", bytes / 1048576.0) +
                    " Mo\nType : " + it.getString(1)
            } else "Aucune information disponible"
        } ?: "Aucune information disponible"
        AlertDialog.Builder(this).setTitle(name).setMessage(meta)
            .setPositiveButton("Fermer", null).show()
    }

    private fun askDelete() {
        AlertDialog.Builder(this).setTitle("Supprimer ce média ?")
            .setMessage("Android demandera ton accord avant de supprimer le fichier.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Continuer") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        val pending = MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri))
                        deletionRequest.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(
                                pending.intentSender
                            ).build()
                        )
                    }.onFailure { showErrorDialog("Suppression indisponible.") }
                } else showErrorDialog("La suppression nécessite un gestionnaire de fichiers sur cette version d'Android.")
            }.show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun showError(root: FrameLayout, text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            setTextColor(-1)
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -1))
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
