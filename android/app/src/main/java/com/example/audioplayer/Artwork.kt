package com.example.audioplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.collection.LruCache
import java.io.File
import kotlin.concurrent.thread

object Artwork {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = LruCache<Long, Bitmap>(64)

    fun loadAlbumArt(context: Context, view: ImageView, albumId: Long) {
        view.tag = albumId
        if (albumId <= 0) {
            view.setImageDrawable(null)
            return
        }
        val cached = cache.get(albumId)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        view.setImageDrawable(null)
        thread {
            val bmp = decodeAlbumArt(context, albumId, 200) ?: return@thread
            cache.put(albumId, bmp)
            mainHandler.post { if (view.tag == albumId) view.setImageBitmap(bmp) }
        }
    }

    fun loadAlbumArtFull(context: Context, view: ImageView, albumId: Long) {
        view.tag = albumId
        if (albumId <= 0) {
            view.setImageDrawable(null)
            return
        }
        thread {
            val bmp = decodeAlbumArt(context, albumId, 1200) ?: return@thread
            mainHandler.post { if (view.tag == albumId) view.setImageBitmap(bmp) }
        }
    }

    private fun decodeAlbumArt(context: Context, albumId: Long, target: Int): Bitmap? {
        val uri = LocalMusic.albumArtUri(albumId)
        return try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(ins, null, bounds)
                var sample = 1
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                if (maxDim > target) {
                    while ((maxDim / sample) > target) sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { ins2 ->
                    BitmapFactory.decodeStream(ins2, null, opts)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadCover(context: Context, view: ImageView, file: File?) {
        if (file == null || !file.exists()) return
        thread {
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@thread
            mainHandler.post { view.setImageBitmap(bmp) }
        }
    }

    fun loadUri(context: Context, view: ImageView, uri: android.net.Uri?) {
        if (uri == null) {
            view.setImageDrawable(null)
            return
        }
        view.tag = uri
        view.setImageDrawable(null)
        thread {
            val bmp = decodeUri(context, uri, 1200) ?: return@thread
            mainHandler.post { if (view.tag == uri) view.setImageBitmap(bmp) }
        }
    }

    private fun decodeUri(context: Context, uri: android.net.Uri, target: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(ins, null, bounds)
                var sample = 1
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                if (maxDim > target) {
                    while ((maxDim / sample) > target) sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { ins2 ->
                    BitmapFactory.decodeStream(ins2, null, opts)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
