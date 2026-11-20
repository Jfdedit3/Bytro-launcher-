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

data class GalleryMedia(val id: Long, val uri: Uri, val isVideo: Boolean)

object GalleryRepository {
    fun query(context: Context): List<GalleryMedia> {
        val items = ArrayList<GalleryMedia>()
        val uri = MediaStore.Files.getContentUri("external")
        val cols = arrayOf(MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE)
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " +
            MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
        val arguments = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        val sorting = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        context.contentResolver.query(uri, cols, selection, arguments, sorting)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val video = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                items.add(GalleryMedia(id, ContentUris.withAppendedId(base, id), video))
            }
        }
        return items
    }
}

class GalleryGridAdapter(
    private val context: Context,
    private val onOpen: (GalleryMedia) -> Unit
) : BaseAdapter() {
    private val items = ArrayList<GalleryMedia>()
    private val pool = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(8192) {
        override fun sizeOf(key: String, bmp: Bitmap): Int =
            (bmp.byteCount / 1024).coerceAtLeast(1)
    }
    private val size = (context.resources.displayMetrics.widthPixels / 3).coerceAtLeast(90)

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
            frame.addView(image, FrameLayout.LayoutParams(-1, -1))
            val badge = TextView(context).apply {
                text = "▶"
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = ColorDrawable(0x88000000.toInt())
            }
            frame.addView(badge, FrameLayout.LayoutParams(size / 4, size / 4,
                Gravity.END or Gravity.BOTTOM))
            cell = Cell(image, badge)
            frame.tag = cell
        }
        val media = items[position]
        cell.badge.visibility = if (media.isVideo) View.VISIBLE else View.GONE
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

    private data class Cell(val image: ImageView, val badge: TextView)
}
