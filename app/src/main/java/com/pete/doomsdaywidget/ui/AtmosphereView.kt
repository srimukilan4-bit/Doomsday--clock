package com.pete.doomsdaywidget.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import com.pete.doomsdaywidget.theme.ThemePalette
import java.util.Random
import kotlin.math.max

/**
 * Background layer: the default cinematic backdrop (gradient + glow + vignette + slow drifting
 * embers) or, when the user imported a wallpaper, just a readability scrim above that image.
 * Embers only animate while allowed and visible; in battery mode it is a still image.
 */
class AtmosphereView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var palette = ThemePalette.DOOM
    var hasWallpaper: Boolean = false
        set(value) { field = value; rebuild(); invalidate() }

    private val bgPaint = Paint()
    private val glowPaint = Paint()
    private val vignettePaint = Paint()
    private val emberPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val count = 26
    private val ex = FloatArray(count)
    private val ey = FloatArray(count)
    private val esize = FloatArray(count)
    private val espeed = FloatArray(count)
    private val ealpha = IntArray(count)

    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var animationAllowed = true
    private var hostActive = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val rnd = Random(7)
        for (i in 0 until count) {
            ex[i] = rnd.nextFloat()
            ey[i] = rnd.nextFloat()
            esize[i] = 0.6f + rnd.nextFloat() * 1.8f
            espeed[i] = 1f + rnd.nextInt(3) // whole loops per cycle, so the loop is seamless
            ealpha[i] = 40 + rnd.nextInt(70)
        }
    }

    fun setPalette(p: ThemePalette) {
        palette = p
        rebuild()
        invalidate()
    }

    fun setAnimationAllowed(allowed: Boolean) {
        animationAllowed = allowed
        updateAnimator()
        invalidate()
    }

    fun setHostActive(active: Boolean) {
        hostActive = active
        updateAnimator()
    }

    private fun updateAnimator() {
        val run = animationAllowed && hostActive && isAttachedToWindow && ValueAnimator.areAnimatorsEnabled()
        if (run) {
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 60_000L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
        } else {
            animator?.cancel()
            animator = null
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimator() }

    override fun onDetachedFromWindow() {
        animator?.cancel(); animator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (hasWallpaper) {
            // translucent scrim: keeps the numerals readable over any photo
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0x99000000.toInt(), 0x66000000, 0xB3000000.toInt()), null, Shader.TileMode.CLAMP,
            )
        } else {
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h, palette.bgTop, palette.bgBottom, Shader.TileMode.CLAMP,
            )
        }
        val gr = max(w, h) * 0.75f
        glowPaint.shader = RadialGradient(
            w / 2f, h * 0.30f, gr,
            intArrayOf(ColorUtils.setAlphaComponent(palette.glow, if (hasWallpaper) 46 else 70), 0x00000000),
            null, Shader.TileMode.CLAMP,
        )
        vignettePaint.shader = RadialGradient(
            w / 2f, h / 2f, max(w, h) * 0.75f,
            intArrayOf(0x00000000, 0x00000000, 0xA6000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        if (animationAllowed || !hasWallpaper) drawEmbers(canvas, w, h)
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }

    private fun drawEmbers(canvas: Canvas, w: Float, h: Float) {
        val density = resources.displayMetrics.density
        for (i in 0 until count) {
            // drift upward, wrap around; still (but visible) when animation is off
            var y = ey[i] - phase * espeed[i]
            y -= Math.floor(y.toDouble()).toFloat()
            val x = ex[i] * w + 6f * density * kotlin.math.sin((phase * espeed[i] + ex[i]) * 6.2831855f)
            emberPaint.color = ColorUtils.setAlphaComponent(palette.glow, ealpha[i])
            canvas.drawCircle(x, y * h, esize[i] * density, emberPaint)
        }
    }
}
