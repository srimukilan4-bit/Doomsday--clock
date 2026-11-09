package com.pete.doomsdaywidget.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.pete.doomsdaywidget.R
import com.pete.doomsdaywidget.countdown.CountdownFormat
import com.pete.doomsdaywidget.countdown.CountdownState
import com.pete.doomsdaywidget.theme.ThemePalette
import kotlin.math.min

/**
 * MONTHS : DAYS : HOURS : MINUTES : SECONDS in ONE row, drawn directly on a canvas.
 *
 * Drawing it ourselves (instead of nesting TextViews) lets us solve the layout exactly:
 * the digit size is computed in closed form from the available width (and height, when the
 * parent limits it) so the five units always fit, never clip and never overlap, from
 * small phones to tablets, in portrait and landscape. Labels sit directly under their numbers.
 */
class CountdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val labels = arrayOf(
        context.getString(R.string.unit_months),
        context.getString(R.string.unit_days),
        context.getString(R.string.unit_hours),
        context.getString(R.string.unit_minutes),
        context.getString(R.string.unit_seconds),
    )
    private val longestLabel = labels.maxByOrNull { it.length } ?: "MINUTES"

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // palette
    private var light = 0xFFC4F8D2.toInt()
    private var mid = 0xFF3DDC84.toInt()
    private var dark = 0xFF0F6B3A.toInt()
    private var glow = 0xFF1FD66B.toInt()
    private var labelColor = 0xFF86BFA0.toInt()

    // state
    private var state: CountdownState = CountdownState.ZERO
    private var animateTicks = true
    private var pop = 0f
    private var popAnimator: ValueAnimator? = null

    // layout results (all in px)
    private var digitSize = 0f
    private var digitHeight = 0f
    private var cellWidth = 0f
    private var sepWidth = 0f
    private var labelSize = 0f
    private var contentHeight = 0f
    private var gap = 0f
    private var topPad = 0f

    private val bounds = Rect()
    private val digitRatioHeight: Float
    private val digitWidthPerPx: Float
    private val labelWidthPerPx: Float

    init {
        numberPaint.textSize = 100f
        numberPaint.getTextBounds("8", 0, 1, bounds)
        digitRatioHeight = bounds.height() / 100f
        digitWidthPerPx = numberPaint.measureText("88") / 100f
        labelPaint.textSize = 100f
        labelWidthPerPx = labelPaint.measureText(longestLabel) / 100f
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = describe(state)
    }

    // ---- public API ----

    fun setPalette(p: ThemePalette) {
        light = p.light; mid = p.mid; dark = p.dark; glow = p.glow; labelColor = p.label
        rebuildPaints()
        invalidate()
    }

    fun setAnimateTicks(enabled: Boolean) {
        animateTicks = enabled
        if (!enabled) {
            popAnimator?.cancel()
            pop = 0f
            invalidate()
        }
    }

    fun setState(s: CountdownState) {
        if (s == state) return
        val secondChanged = s.seconds != state.seconds
        state = s
        contentDescription = describe(s)
        if (secondChanged && animateTicks && !s.reached) startPop()
        invalidate()
    }

    private fun describe(s: CountdownState): String =
        context.getString(R.string.countdown_description, s.months, s.days, s.hours, s.minutes, s.seconds)

    private fun startPop() {
        popAnimator?.cancel()
        popAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // ---- measuring ----

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val wMode = MeasureSpec.getMode(widthSpec)
        val w = if (wMode == MeasureSpec.UNSPECIFIED) {
            (resources.displayMetrics.density * 320).toInt()
        } else MeasureSpec.getSize(widthSpec)
        val hMode = MeasureSpec.getMode(heightSpec)
        val hSize = MeasureSpec.getSize(heightSpec)
        val maxH = if (hMode == MeasureSpec.UNSPECIFIED) Float.MAX_VALUE else hSize.toFloat()

        val availW = (w - paddingLeft - paddingRight).coerceAtLeast(1).toFloat()
        solve(availW, maxH - paddingTop - paddingBottom)

        val wanted = (contentHeight + paddingTop + paddingBottom).toInt()
        val h = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> min(wanted, hSize)
            else -> wanted
        }
        setMeasuredDimension(w, h)
    }

    private fun heightFor(size: Float, availW: Float): Float {
        val cell = digitWidthPerPx * size * CELL_FACTOR
        val lbl = labelSizeFor(size, cell)
        return digitRatioHeight * size + size * GAP_RATIO + lbl * 1.25f + size * GLOW_PAD_RATIO * 2f
    }

    private fun labelSizeFor(size: Float, cell: Float): Float {
        val byWidth = cell * 1.02f / labelWidthPerPx
        return min(size * LABEL_RATIO, byWidth)
    }

    /** Closed-form size so 5 cells + 4 separators exactly fill the width (or the height limit). */
    private fun solve(availW: Float, maxH: Float) {
        var size = availW * 0.985f / (5f * CELL_FACTOR * digitWidthPerPx + 4f * SEP_RATIO)
        val h = heightFor(size, availW)
        if (h > maxH && maxH > 0f) size *= (maxH / h) * 0.995f
        size = size.coerceAtLeast(8f)

        digitSize = size
        digitHeight = digitRatioHeight * size
        cellWidth = digitWidthPerPx * size * CELL_FACTOR
        sepWidth = size * SEP_RATIO
        labelSize = labelSizeFor(size, cellWidth)
        gap = size * GAP_RATIO
        topPad = size * GLOW_PAD_RATIO
        contentHeight = heightFor(size, availW)
        numberPaint.textSize = digitSize
        labelPaint.textSize = labelSize
        rebuildPaints()
    }

    private fun rebuildPaints() {
        if (digitSize <= 0f) return
        // Gradient is defined in the translated space used by onDraw (content top = 0).
        numberPaint.shader = LinearGradient(
            0f, topPad, 0f, topPad + digitHeight,
            intArrayOf(light, mid, dark), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        numberPaint.setShadowLayer(digitSize * 0.14f, 0f, 0f, glow)
        labelPaint.color = labelColor
        dotPaint.color = mid
        dotPaint.setShadowLayer(digitSize * 0.10f, 0f, 0f, glow)
    }

    // ---- drawing ----

    override fun onDraw(canvas: Canvas) {
        if (digitSize <= 0f) return
        val total = 5f * cellWidth + 4f * sepWidth
        var x = paddingLeft + ((width - paddingLeft - paddingRight) - total) / 2f
        val usable = height - paddingTop - paddingBottom
        val top = paddingTop + ((usable - contentHeight) / 2f).coerceAtLeast(0f)
        val baseline = topPad + digitHeight
        val labelBase = baseline + gap + labelSize
        canvas.save()
        canvas.translate(0f, top)
        val values = intArrayOf(state.months, state.days, state.hours, state.minutes, state.seconds)

        for (i in 0 until 5) {
            val cx = x + cellWidth / 2f
            val text = CountdownFormat.two(values[i])
            if (i == 4 && pop > 0f) {
                val s = 1f + 0.05f * pop
                canvas.save()
                canvas.scale(s, s, cx, baseline - digitHeight / 2f)
                canvas.drawText(text, cx, baseline, numberPaint)
                canvas.restore()
            } else {
                canvas.drawText(text, cx, baseline, numberPaint)
            }
            canvas.drawText(labels[i], cx, labelBase, labelPaint)
            if (i < 4) {
                val sx = x + cellWidth + sepWidth / 2f
                val r = digitSize * 0.045f
                canvas.drawCircle(sx, baseline - digitHeight * 0.68f, r, dotPaint)
                canvas.drawCircle(sx, baseline - digitHeight * 0.32f, r, dotPaint)
            }
            x += cellWidth + sepWidth
        }
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        popAnimator?.cancel()
        popAnimator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val CELL_FACTOR = 1.06f   // cell width relative to a two-digit number
        const val SEP_RATIO = 0.30f     // separator width relative to digit size
        const val LABEL_RATIO = 0.20f   // label size relative to digit size
        const val GAP_RATIO = 0.16f     // space between digits and label
        const val GLOW_PAD_RATIO = 0.08f
    }
}
