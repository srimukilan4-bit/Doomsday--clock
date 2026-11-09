package com.pete.doomsdaywidget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.pete.doomsdaywidget.data.AppPrefs
import com.pete.doomsdaywidget.data.WallpaperStore
import com.pete.doomsdaywidget.theme.ThemePalette
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the widget background (themed gradient or the user's wallpaper, scrim, glow, rounded
 * corners, metallic border) into one small bitmap sized to the widget. Blocking: call off the
 * main thread. Returns a plain gradient if the wallpaper cannot be read.
 */
object WidgetBackground {
    private const val MAX_DIM = 720

    fun render(ctx: Context, widthDp: Int, heightDp: Int, p: ThemePalette): Bitmap {
        val density = ctx.resources.displayMetrics.density
        var w = (widthDp * density).toInt().coerceAtLeast(64)
        var h = (heightDp * density).toInt().coerceAtLeast(32)
        val scale = min(1f, MAX_DIM.toFloat() / max(w, h))
        w = (w * scale).toInt().coerceAtLeast(32)
        h = (h * scale).toInt().coerceAtLeast(32)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), p.bgTop, p.bgBottom, Shader.TileMode.CLAMP)
        c.drawRect(rect, paint)

        val prefs = AppPrefs.get(ctx)
        if (prefs.wallpaperStamp != 0L) {
            WallpaperStore.load(ctx, max(w, h))?.let { wp ->
                val s = max(w.toFloat() / wp.width, h.toFloat() / wp.height)
                val srcW = w / s
                val srcH = h / s
                val src = Rect(
                    ((wp.width - srcW) / 2f).toInt(), ((wp.height - srcH) / 2f).toInt(),
                    ((wp.width + srcW) / 2f).toInt(), ((wp.height + srcH) / 2f).toInt(),
                )
                c.drawBitmap(wp, src, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                wp.recycle()
                paint.shader = null
                paint.color = 0x99000000.toInt() // scrim so the numerals stay readable
                c.drawRect(rect, paint)
            }
        }

        paint.shader = RadialGradient(
            w * 0.5f, 0f, max(w, h) * 0.8f,
            intArrayOf(ColorUtils.setAlphaComponent(p.glow, 64), 0x00000000),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRect(rect, paint)

        // anti-aliased rounded corners: keep only what is inside the rounded rectangle
        val radius = min(w, h) * 0.18f
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            color = 0xFF000000.toInt()
        }
        c.drawRoundRect(rect, radius, radius, mask)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, min(w, h) * 0.018f)
            color = ColorUtils.setAlphaComponent(p.mid, 150)
        }
        val inset = border.strokeWidth / 2f
        c.drawRoundRect(
            RectF(inset, inset, w - inset, h - inset), radius - inset, radius - inset, border,
        )
        return bmp
    }
}
