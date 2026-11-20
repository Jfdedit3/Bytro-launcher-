package com.jfdedit3.bytrolauncher

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.util.concurrent.Executors

data class GalleryMedia(val id: Long, val uri: Uri, val isVideo: Boolean, val name: String, val album: String)

object GalleryRepository {
    fun query(context: Context): List<GalleryMedia> {
        val items = ArrayList<GalleryMedia>()
        val uri = MediaStore.Files.getContentUri("external")
        val cols = arrayOf(MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " +
            MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
        val arguments = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        val sorting = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        context.contentResolver.query(uri, cols, selection, arguments, sorting)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val albumCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val video = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                items.add(GalleryMedia(id, ContentUris.withAppendedId(base, id), video,
                    cursor.getString(nameCol).orEmpty(),
                    if (albumCol >= 0) cursor.getString(albumCol).orEmpty().ifBlank { "Autres" } else "Autres"))
            }
        }
        return items
    }
}

class GalleryGridAdapter(
    private val context: Context,
    private val onOpen: (GalleryMedia) -> Unit,
    private val isFavorite: (GalleryMedia) -> Boolean,
    private val onFavorite: (GalleryMedia) -> Unit
) : BaseAdapter() {
    private val items = ArrayList<GalleryMedia>()
    private val pool = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(8192) {
        override fun sizeOf(key: String, bmp: Bitmap): Int =
            (bmp.byteCount / 1024).coerceAtLeast(1)
    }
    private val density = context.resources.displayMetrics.density
    private val size = ((context.resources.displayMetrics.widthPixels - 26 * density) / 3)
        .toInt().coerceAtLeast(84)

    fun update(data: List<GalleryMedia>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): GalleryMedia = items[position]
    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, old: View?, parent: ViewGroup): View {
        val cell: Cell
        val frame: FrameLayout
        if (old is FrameLayout && old.tag is Cell) {
            frame = old
            cell = old.tag as Cell
        } else {
            frame = FrameLayout(context).apply {
                layoutParams = android.widget.AbsListView.LayoutParams(size, size)
            }
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(28, 32, 40))
            }
            image.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF27213C.toInt())
                cornerRadius = 12f * density
            }
            image.clipToOutline = true
            frame.addView(image, FrameLayout.LayoutParams(-1, -1))
            val badge = TextView(context).apply {
                text = "▶"
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = ColorDrawable(0x88000000.toInt())
            }
            frame.addView(badge, FrameLayout.LayoutParams(size / 3, size / 4,
                Gravity.END or Gravity.BOTTOM))
            val heart = TextView(context).apply {
                text = "♡"
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0xBB160F2A.toInt())
                }
            }
            frame.addView(heart, FrameLayout.LayoutParams(size / 3, size / 3,
                Gravity.TOP or Gravity.END))
            cell = Cell(image, badge, heart)
            frame.tag = cell
        }
        val media = items[position]
        cell.badge.visibility = if (media.isVideo) View.VISIBLE else View.GONE
        cell.heart.text = if (isFavorite(media)) "♥" else "♡"
        cell.heart.setTextColor(if (isFavorite(media)) 0xFFE78BFA.toInt() else Color.WHITE)
        cell.heart.setOnClickListener {
            onFavorite(media)
            notifyDataSetChanged()
        }
        val key = media.uri.toString()
        cell.image.tag = key
        val cached = cache.get(key)
        cell.image.setImageBitmap(cached)
        if (cached == null) {
            pool.execute {
                val bitmap = runCatching {
                    if (Build.VERSION.SDK_INT >= 29)
                        context.contentResolver.loadThumbnail(media.uri, Size(320, 320), null)
                    else if (media.isVideo)
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver,
                            media.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                    else
                        MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver,
                            media.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
                }.getOrNull()
                if (bitmap != null) {
                    cache.put(key, bitmap)
                    cell.image.post {
                        if (cell.image.tag == key) cell.image.setImageBitmap(bitmap)
                    }
                }
            }
        }
        frame.setOnClickListener { onOpen(media) }
        return frame
    }

    fun close() { pool.shutdownNow() }

    private data class Cell(val image: ImageView, val badge: TextView, val heart: TextView)
}
