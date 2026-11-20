package com.jfdedit3.bytrolauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.jfdedit3.bytrolauncher.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : ImmersiveActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: GalleryGridAdapter
    private val executor = Executors.newSingleThreadExecutor()
    private var allItems: List<GalleryMedia> = emptyList()
    private var filter = 0
    private var search = ""
    private var album: String? = null
    private var tab = 0
    private val prefs by lazy { getSharedPreferences("gallery_favorites", MODE_PRIVATE) }
    private val askPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adapter = GalleryGridAdapter(this,
            onOpen = { media ->
                startActivity(Intent(this, MediaViewerActivity::class.java)
                    .putExtra("media_uri", media.uri.toString())
                    .putExtra("media_name", media.name)
                    .putExtra("is_video", media.isVideo))
            },
            isFavorite = { media -> isFavorite(media) },
            onFavorite = { media -> toggleFavorite(media) }
        )
        binding.mediaGrid.adapter = adapter
        binding.mediaGrid.numColumns = 3
        binding.allButton.setOnClickListener { changeFilter(0) }
        binding.photosButton.setOnClickListener { changeFilter(1) }
        binding.videosButton.setOnClickListener { changeFilter(2) }
        binding.favoritesButton.setOnClickListener { changeFilter(3) }
        binding.navGallery.setOnClickListener {
            tab = 0
            album = null
            filter = 0
            showItems()
        }
        binding.navAlbums.setOnClickListener { chooseAlbum() }
        binding.navFavorites.setOnClickListener {
            tab = 2
            album = null
            filter = 3
            showItems()
        }
        binding.navStats.setOnClickListener { showStats() }
        binding.searchToggle.setOnClickListener {
            val show = binding.searchInput.visibility != View.VISIBLE
            binding.searchInput.visibility = if (show) View.VISIBLE else View.GONE
            if (show) binding.searchInput.requestFocus()
            else binding.searchInput.setText("")
        }
        binding.menuButton.setOnClickListener {
            val popup = PopupMenu(this, binding.menuButton)
            popup.menu.add("Actualiser")
            popup.menu.add("Albums")
            popup.menu.add("Statistiques")
            popup.menu.add("Autorisations")
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Actualiser" -> refresh()
                    "Albums" -> chooseAlbum()
                    "Statistiques" -> showStats()
                    "Autorisations" -> askAccess(true)
                }
                true
            }
            popup.show()
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                search = s?.toString()?.trim()?.lowercase().orEmpty()
                showItems()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        showItems()
        askAccess()
    }

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasAccess() = requiredPermissions().any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun askAccess(force: Boolean = false) {
        if (hasAccess() && !force) refresh()
        else askPermissions.launch(requiredPermissions())
    }

    private fun refresh() {
        if (!hasAccess()) {
            allItems = emptyList()
            binding.galleryStatus.text = "Autorise tes photos et vidéos"
            binding.loadingIndicator.visibility = View.GONE
            showItems()
            return
        }
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        executor.execute {
            val result = runCatching { GalleryRepository.query(this) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.loadingIndicator.visibility = View.GONE
                result.onSuccess {
                    allItems = it
                    showItems()
                }.onFailure {
                    binding.galleryStatus.text = "Erreur de lecture : " + it.javaClass.simpleName
                    Toast.makeText(this, "Impossible de charger les médias", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun changeFilter(value: Int) {
        filter = value
        if (value == 3) tab = 2
        else if (tab == 2) tab = 0
        showItems()
    }

    private fun isFavorite(media: GalleryMedia): Boolean =
        prefs.getBoolean(media.uri.toString(), false)

    private fun toggleFavorite(media: GalleryMedia) {
        prefs.edit().putBoolean(media.uri.toString(), !isFavorite(media)).apply()
        showItems()
    }

    private fun showItems() {
        if (!::binding.isInitialized || !::adapter.isInitialized) return
        var list = when (filter) {
            1 -> allItems.filter { !it.isVideo }
            2 -> allItems.filter { it.isVideo }
            3 -> allItems.filter { isFavorite(it) }
            else -> allItems
        }
        album?.let { selected -> list = list.filter { it.album == selected } }
        if (search.isNotBlank()) {
            list = list.filter {
                it.name.contains(search, ignoreCase = true) ||
                    it.album.contains(search, ignoreCase = true)
            }
        }
        adapter.update(list)
        val videoCount = allItems.count { it.isVideo }
        binding.galleryStatus.text = allItems.size.toString() + " médias • " + videoCount + " vidéos"
        binding.emptyState.visibility = if (list.isEmpty() &&
            binding.loadingIndicator.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        binding.emptyTitle.text = when {
            search.isNotEmpty() -> "Aucun résultat"
            filter == 3 -> "Aucun favori"
            else -> "Aucun média"
        }
        binding.emptySubtitle.text = if (!hasAccess()) "Autorise l'accès aux médias dans les paramètres."
            else "Les éléments disponibles apparaîtront ici."
        val chips = arrayOf(binding.allButton, binding.photosButton,
            binding.videosButton, binding.favoritesButton)
        chips.forEachIndexed { i, chip ->
            chip.isSelected = filter == i
            chip.setTextColor(if (filter == i) 0xFF140C28.toInt() else 0xFFF8FAFF.toInt())
        }
        val tabs = arrayOf(binding.navGallery, binding.navAlbums,
            binding.navFavorites, binding.navStats)
        tabs.forEachIndexed { i, label ->
            label.setTextColor(if (tab == i) 0xFFC67AFF.toInt() else 0xFF97A4B9.toInt())
        }
    }

    private fun chooseAlbum() {
        val albums = allItems.groupingBy { it.album }.eachCount().toList()
            .sortedByDescending { it.second }
        if (albums.isEmpty()) {
            Toast.makeText(this, "Aucun album disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = albums.map { it.first + " (" + it.second + ")" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Mes albums")
            .setItems(labels) { _, index ->
                album = albums[index].first
                tab = 1
                filter = 0
                showItems()
                binding.galleryStatus.text = album
            }
            .setNeutralButton("Tous les albums") { _, _ ->
                album = null
                tab = 0
                filter = 0
                showItems()
            }.show()
    }

    private fun showStats() {
        tab = 3
        showItems()
        val photos = allItems.count { !it.isVideo }
        val videos = allItems.size - photos
        val favorites = allItems.count { isFavorite(it) }
        val albums = allItems.map { it.album }.distinct().size
        AlertDialog.Builder(this)
            .setTitle("Statistiques de la galerie")
            .setMessage("Photos : " + photos + "\nVidéos : " + videos +
                "\nFavoris : " + favorites + "\nAlbums : " + albums +
                "\n\nTout reste stocké sur ton téléphone.")
            .setPositiveButton("Fermer") { _, _ ->
                tab = 0
                showItems()
            }.setOnCancelListener {
                tab = 0
                showItems()
            }.show()
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
