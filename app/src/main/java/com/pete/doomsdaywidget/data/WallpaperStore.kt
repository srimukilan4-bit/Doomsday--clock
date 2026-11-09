package com.pete.doomsdaywidget.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Custom wallpaper storage.
 *
 * The picked image is decoded, downscaled and copied into the app's private storage. After that
 * the app and the widget read only that private copy, so a deleted/moved/permission-revoked
 * original can never break the UI. All functions that touch images are BLOCKING: call them
 * from a background thread.
 */
object WallpaperStore {
    private const val FILE_NAME = "wallpaper.jpg"
    private const val MAX_STORED_DIM = 1920

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    fun hasCustom(ctx: Context): Boolean = file(ctx).let { it.exists() && it.length() > 0 }

    /** Copies the chosen image into private storage. Returns false if it could not be read. */
    fun importFrom(ctx: Context, uri: Uri): Boolean {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Not fatal: we copy the bytes right now while the temporary grant is valid.
        }
        val bitmap = decodeUri(ctx, uri, MAX_STORED_DIM) ?: return false
        val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
        return try {
            FileOutputStream(tmp).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) return false
            }
            val target = file(ctx)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (_: Exception) {
            tmp.delete()
            false
        } finally {
            bitmap.recycle()
        }
    }

    /** Loads the stored wallpaper, downsampled so its longest side is about [maxDim] px. */
    fun load(ctx: Context, maxDim: Int): Bitmap? {
        val f = file(ctx)
        if (!f.exists() || f.length() == 0L) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            }
            BitmapFactory.decodeFile(f.absolutePath, opts)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
        File(ctx.filesDir, "$FILE_NAME.tmp").delete()
    }

    private fun sampleSize(w: Int, h: Int, maxDim: Int): Int {
        var s = 1
        val limit = max(1, maxDim)
        while (max(w, h) / (s * 2) >= limit) s *= 2
        return s
    }

    private fun decodeUri(ctx: Context, uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val decoded: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(ctx.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = max(1f, max(w, h).toFloat() / maxDim)
                    decoder.setTargetSize(
                        (w / scale).toInt().coerceAtLeast(1),
                        (h / scale).toInt().coerceAtLeast(1),
                    )
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    null
                } else {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
                    }
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                }
            }
            decoded
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
