package com.jfdedit3.bytrolauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
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
                val selected = allItems.indexOfFirst { it.uri == media.uri }
                val first = (selected - 6).coerceAtLeast(0)
                val last = (selected + 7).coerceAtMost(allItems.size)
                val strip = if (selected >= 0) allItems.subList(first, last)
                    else listOf(media)
                startActivity(Intent(this, MediaViewerActivity::class.java)
                    .putExtra("media_uri", media.uri.toString())
                    .putExtra("media_name", media.name)
                    .putExtra("is_video", media.isVideo)
                    .putStringArrayListExtra("strip_uris",
                        ArrayList(strip.map { it.uri.toString() }))
                    .putStringArrayListExtra("strip_names",
                        ArrayList(strip.map { it.name }))
                    .putExtra("strip_videos", strip.map { it.isVideo }.toBooleanArray()))
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
        binding.menuButton.setOnClickListener { showDrawer() }
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

    private fun showDrawer() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (density * v).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(23), dp(42), dp(23), dp(25))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF271043.toInt(), 0xFF0E0C1B.toInt(), 0xFF13101F.toInt())
            ).apply { cornerRadius = dp(25).toFloat() }
        }
        val header = TextView(this).apply {
            text = "▧\n\nMa Galerie"
            textSize = 27f
            setTextColor(-1)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(3))
        }
        container.addView(header)
        val sub = TextView(this).apply {
            text = "Tes médias, sur ton appareil"
            textSize = 13f
            setTextColor(0xFFCBC2E1.toInt())
            setPadding(0, 0, 0, dp(30))
        }
        container.addView(sub)
        val dialog = AlertDialog.Builder(this).setView(container).create()
        fun drawerItem(icon: String, label: String, action: () -> Unit) {
            val view = TextView(this).apply {
                text = icon + "    " + label
                textSize = 18f
                setTextColor(-1)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(0x442B1B53)
                    cornerRadius = dp(14).toFloat()
                }
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }
            val params = LinearLayout.LayoutParams(-1, dp(58)).apply {
                bottomMargin = dp(10)
            }
            container.addView(view, params)
        }
        drawerItem("▧", "Galerie") {
            tab = 0
            album = null
            filter = 0
            showItems()
        }
        drawerItem("▱", "Albums") { chooseAlbum() }
        drawerItem("♡", "Favoris") {
            tab = 2
            album = null
            filter = 3
            showItems()
        }
        drawerItem("▥", "Statistiques") { showStats() }
        drawerItem("⚙", "Autorisations") { askAccess(true) }
        val storage = TextView(this).apply {
            val stat = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
            val capacity = stat.totalBytes / (1024 * 1024 * 1024)
            val used = (stat.totalBytes - stat.availableBytes) / (1024 * 1024 * 1024)
            text = "STOCKAGE\n\n" + used + " Go / " + capacity + " Go"
            textSize = 14f
            setTextColor(0xFFD6CDE9.toInt())
            setPadding(dp(8), dp(18), 0, 0)
        }
        container.addView(storage)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setGravity(Gravity.START or Gravity.CENTER_VERTICAL)
                setLayout(
                    (resources.displayMetrics.widthPixels * 0.83).toInt(),
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                )
            }
        }
        dialog.show()
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
