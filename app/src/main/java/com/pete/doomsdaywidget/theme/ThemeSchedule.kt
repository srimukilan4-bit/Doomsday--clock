package com.pete.doomsdaywidget.theme

import com.pete.doomsdaywidget.countdown.CountdownConfig
import java.time.Duration
import java.time.Instant

/**
 * Deterministic Doom -> Iron Man timeline.
 *
 * progress = 0.0 : full Doctor Doom identity
 * progress = 1.0 : full Iron Man identity
 * In between the two palettes are blended with a smoothstep curve (no flash, no jump).
 *
 * The progress is a pure function of the current instant, so closing and reopening the
 * app, or rendering the widget, always gives the same answer.
 */
class ThemeSchedule(val blendStart: Instant, val blendEnd: Instant) {
    init {
        require(blendEnd.isAfter(blendStart)) { "blendEnd must be after blendStart" }
    }

    fun progress(now: Instant): Float {
        if (!now.isAfter(blendStart)) return 0f
        if (!now.isBefore(blendEnd)) return 1f
        val done = Duration.between(blendStart, now).toMillis().toDouble()
        val total = Duration.between(blendStart, blendEnd).toMillis().toDouble()
        val t = (done / total).coerceIn(0.0, 1.0)
        return (t * t * (3.0 - 2.0 * t)).toFloat()
    }
}

/**
 * CONFIGURE THE TRANSITION HERE.
 * Default: pure Doom until 30 days before the event, blending gradually, fully Iron Man
 * from 7 days before the event until the end.
 */
object ThemeConfig {
    const val BLEND_START_DAYS_BEFORE = 30L
    const val BLEND_END_DAYS_BEFORE = 7L

    val schedule = ThemeSchedule(
        blendStart = CountdownConfig.TARGET.minusDays(BLEND_START_DAYS_BEFORE).toInstant(),
        blendEnd = CountdownConfig.TARGET.minusDays(BLEND_END_DAYS_BEFORE).toInstant(),
    )
}
