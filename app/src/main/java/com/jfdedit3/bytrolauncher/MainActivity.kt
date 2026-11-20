package com.jfdedit3.bytrolauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.jfdedit3.bytrolauncher.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : ImmersiveActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: GalleryGridAdapter
    private val executor = Executors.newSingleThreadExecutor()
    private var allItems: List<GalleryMedia> = emptyList()
    private var filter = 0

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = GalleryGridAdapter(this) { media ->
            startActivity(Intent(this, MediaViewerActivity::class.java)
                .putExtra("media_uri", media.uri.toString())
                .putExtra("is_video", media.isVideo))
        }
        binding.mediaGrid.numColumns = 3
        binding.mediaGrid.horizontalSpacing = 3
        binding.mediaGrid.verticalSpacing = 3
        binding.mediaGrid.adapter = adapter
        binding.refreshButton.setOnClickListener { refresh() }
        binding.permissionsButton.setOnClickListener { askAccess() }
        binding.allButton.setOnClickListener { setFilter(0) }
        binding.photosButton.setOnClickListener { setFilter(1) }
        binding.videosButton.setOnClickListener { setFilter(2) }
        askAccess()
    }

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasAccess(): Boolean {
        val granted = requiredPermissions().any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        return granted
    }

    private fun askAccess() {
        if (hasAccess()) refresh()
        else requestPermissions.launch(requiredPermissions())
    }

    private fun refresh() {
        if (!hasAccess()) {
            binding.galleryStatus.text = "Autorise l'accès aux photos et vidéos pour afficher ta galerie."
            binding.permissionsButton.isEnabled = true
            adapter.update(emptyList())
            return
        }
        binding.galleryStatus.text = "Chargement des médias..."
        executor.execute {
            val result = runCatching { GalleryRepository.query(this) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    allItems = it
                    setFilter(filter)
                }.onFailure {
                    binding.galleryStatus.text = "Impossible de lire les médias : " + it.javaClass.simpleName
                    Toast.makeText(this, "Erreur de lecture de la galerie", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setFilter(value: Int) {
        filter = value
        val visible = when (filter) {
            1 -> allItems.filter { !it.isVideo }
            2 -> allItems.filter { it.isVideo }
            else -> allItems
        }
        adapter.update(visible)
        val label = when (filter) {
            1 -> "photos"
            2 -> "vidéos"
            else -> "médias"
        }
        binding.galleryStatus.text = visible.size.toString() + " " + label +
            " • Appuie sur un élément pour l'ouvrir"
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && ::adapter.isInitialized && hasAccess()) refresh()
    }

    override fun onDestroy() {
        if (::adapter.isInitialized) adapter.close()
        executor.shutdownNow()
        super.onDestroy()
    }
}
