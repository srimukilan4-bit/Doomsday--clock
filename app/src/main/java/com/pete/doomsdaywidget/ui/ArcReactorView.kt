package com.pete.doomsdaywidget.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import com.pete.doomsdaywidget.theme.ThemePalette
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Original arc-reactor-style graphic: glowing core, ten "coil" segments, two counter-rotating
 * dashed rings and a metallic housing. Lightweight: no blur filters, no bitmaps, no allocation
 * per frame, one animator that only runs while the screen is visible and motion is allowed.
 */
class ArcReactorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var reactor = 0xFF4DE8FF.toInt()
    private var metalLight = 0xFFE6E6E6.toInt()
    private var metalDark = 0xFF2E2E2E.toInt()

    private var cx = 0f
    private var cy = 0f
    private var r = 0f
    private var phase = 0f

    private var animator: ValueAnimator? = null
    private var animationAllowed = true
    private var hostActive = false

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val housingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringAPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringBPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val coilFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val coilStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coilPath = Path()
    private val outerRect = RectF()
    private val innerRect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // decorative
    }

    fun setPalette(p: ThemePalette) {
        reactor = p.reactor
        metalLight = ColorUtils.blendARGB(p.light, 0xFFFFFFFF.toInt(), 0.25f)
        metalDark = ColorUtils.blendARGB(p.dark, 0xFF000000.toInt(), 0.55f)
        rebuild()
        invalidate()
    }

    /** False in battery-friendly mode: the reactor is then drawn as a still image. */
    fun setAnimationAllowed(allowed: Boolean) {
        animationAllowed = allowed
        updateAnimator()
        invalidate()
    }

    /** Called by the host screen from onStart/onStop. */
    fun setHostActive(active: Boolean) {
        hostActive = active
        updateAnimator()
    }

    private fun updateAnimator() {
        val shouldRun = animationAllowed && hostActive && isAttachedToWindow &&
            ValueAnimator.areAnimatorsEnabled()
        if (shouldRun) {
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 24_000L
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimator()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        r = min(w, h) / 2f * 0.96f
        rebuild()
    }

    private fun a(color: Int, alpha: Int) = ColorUtils.setAlphaComponent(color, alpha)

    private fun rebuild() {
        if (r <= 0f) return
        glowPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(a(reactor, 120), a(reactor, 50), a(reactor, 0)),
            floatArrayOf(0.45f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
        housingPaint.strokeWidth = r * 0.09f
        housingPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(metalLight, metalDark, metalLight, metalDark, metalLight), null,
        )
        bezelPaint.strokeWidth = r * 0.012f
        bezelPaint.color = a(reactor, 170)

        val ringARadius = r * 0.66f
        val segA = (2f * PI.toFloat() * ringARadius) / 24f
        ringAPaint.strokeWidth = r * 0.035f
        ringAPaint.color = a(reactor, 210)
        ringAPaint.pathEffect = DashPathEffect(floatArrayOf(segA * 0.62f, segA * 0.38f), 0f)

        val ringBRadius = r * 0.34f
        val segB = (2f * PI.toFloat() * ringBRadius) / 12f
        ringBPaint.strokeWidth = r * 0.02f
        ringBPaint.color = a(reactor, 230)
        ringBPaint.pathEffect = DashPathEffect(floatArrayOf(segB * 0.55f, segB * 0.45f), 0f)

        // ten coil segments between two radii
        val ro = r * 0.58f
        val ri = r * 0.41f
        outerRect.set(cx - ro, cy - ro, cx + ro, cy + ro)
        innerRect.set(cx - ri, cy - ri, cx + ri, cy + ri)
        coilPath.reset()
        for (i in 0 until 10) {
            val start = i * 36f + 5f
            val sweep = 26f
            coilPath.arcTo(outerRect, start, sweep, true)
            coilPath.arcTo(innerRect, start + sweep, -sweep, false)
            coilPath.close()
        }
        coilFill.color = a(reactor, 55)
        coilStroke.strokeWidth = r * 0.012f
        coilStroke.color = a(reactor, 220)

        corePaint.shader = RadialGradient(
            cx, cy, r * 0.30f,
            intArrayOf(0xFFFFFFFF.toInt(), reactor, a(reactor, 0)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (r <= 0f) return
        canvas.drawCircle(cx, cy, r, glowPaint)
        canvas.drawCircle(cx, cy, r * 0.86f, housingPaint)
        canvas.drawCircle(cx, cy, r * 0.74f, bezelPaint)
        canvas.drawPath(coilPath, coilFill)
        canvas.drawPath(coilPath, coilStroke)

        canvas.save()
        canvas.rotate(360f * phase, cx, cy)
        canvas.drawCircle(cx, cy, r * 0.66f, ringAPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-720f * phase, cx, cy)
        canvas.drawCircle(cx, cy, r * 0.34f, ringBPaint)
        canvas.restore()

        val pulse = 1f + 0.05f * sin(2.0 * PI * 6.0 * phase).toFloat()
        canvas.save()
        canvas.scale(pulse, pulse, cx, cy)
        canvas.drawCircle(cx, cy, r * 0.30f, corePaint)
        canvas.restore()
    }
}
