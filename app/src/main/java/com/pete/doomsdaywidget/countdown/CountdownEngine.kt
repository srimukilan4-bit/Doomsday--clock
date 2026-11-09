package com.pete.doomsdaywidget.countdown

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Event configuration. Change the constants here and the app, widget and tests follow. */
object CountdownConfig {
    const val EVENT_TITLE = "Avengers: Doomsday"
    const val EVENT_DATE_LABEL = "17 December 2026"
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    /** 17 December 2026, 12:00 AM (midnight at the start of the day), Asia/Kolkata. */
    val TARGET: ZonedDateTime = ZonedDateTime.of(2026, 12, 17, 0, 0, 0, 0, ZONE)
}

/**
 * One immutable snapshot of the countdown.
 *
 * [hours], [minutes] and [seconds] are always the remainder inside the current day
 * (0..23, 0..59, 0..59). [secondsUntilRollover] is the number of seconds until that
 * h:m:s remainder reaches zero, i.e. until the day/month units change next.
 */
data class CountdownState(
    val months: Int,
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val reached: Boolean,
    val totalSecondsRemaining: Long,
    val secondsUntilRollover: Long,
) {
    companion object {
        val ZERO = CountdownState(0, 0, 0, 0, 0, true, 0L, 0L)
    }
}

/**
 * The single source of truth for countdown maths. Used by the app UI, the widget,
 * the share text and the unit tests. Pure Kotlin / java.time, no Android dependencies.
 *
 * Algorithm (all calendar maths happens in the target's own time zone):
 *  1. Truncate "now" to a whole second (so the display never runs ahead of the clock).
 *  2. months = whole calendar months from now to the target.
 *  3. Move "now" forward by those months (day-of-month is clamped, e.g. 31 Jan + 1 month = 28/29 Feb).
 *  4. days = whole days from there to the target.
 *  5. The rest (< 24h) is split into hours, minutes and seconds.
 *  6. If now >= target, everything is zero. Nothing can ever be negative.
 */
class CountdownEngine(private val target: ZonedDateTime = CountdownConfig.TARGET) {

    val targetInstant: Instant get() = target.toInstant()

    fun compute(now: Instant): CountdownState {
        val nowZ = now.truncatedTo(ChronoUnit.SECONDS).atZone(target.zone)
        if (!nowZ.isBefore(target)) return CountdownState.ZERO

        val months = ChronoUnit.MONTHS.between(nowZ, target)
        val afterMonths = nowZ.plusMonths(months)
        val days = ChronoUnit.DAYS.between(afterMonths, target)
        val afterDays = afterMonths.plusDays(days)
        val rest = Duration.between(afterDays, target).seconds.coerceAtLeast(0L)

        return CountdownState(
            months = months.toInt(),
            days = days.toInt(),
            hours = (rest / 3600).toInt(),
            minutes = ((rest % 3600) / 60).toInt(),
            seconds = (rest % 60).toInt(),
            reached = false,
            totalSecondsRemaining = Duration.between(nowZ, target).seconds.coerceAtLeast(0L),
            secondsUntilRollover = rest,
        )
    }
}

/**
 * Protects the display against tiny backwards clock steps (NTP corrections, jitter).
 * A backwards step of up to [toleranceMillis] is ignored; a bigger one is treated as a
 * genuine clock change by the user and followed, because hiding that would be wrong.
 */
class ClockGuard(private val toleranceMillis: Long = 5_000L) {
    private var last: Instant? = null

    @Synchronized
    fun stable(raw: Instant): Instant {
        val previous = last
        val result = if (previous != null && raw.isBefore(previous) &&
            Duration.between(raw, previous).toMillis() <= toleranceMillis
        ) previous else raw
        last = result
        return result
    }
}

/** Process-wide shared engine so every screen computes from the same object. */
object CountdownClock {
    val engine = CountdownEngine()
    private val guard = ClockGuard()
    fun now(): Instant = guard.stable(Instant.now())
    fun state(): CountdownState = engine.compute(now())
}

/** Pure text formatting shared by the share action and tests. */
object CountdownFormat {
    fun two(n: Int): String = if (n in 0..9) "0$n" else n.toString()

    private fun unit(n: Int, singular: String): String = "$n $singular" + if (n == 1) "" else "s"

    fun shareText(s: CountdownState): String {
        val header = "${CountdownConfig.EVENT_TITLE} — ${CountdownConfig.EVENT_DATE_LABEL}"
        if (s.reached) return "$header\nIt is here. The countdown has reached zero."
        val body = listOf(
            unit(s.months, "month"), unit(s.days, "day"), unit(s.hours, "hour"),
            unit(s.minutes, "minute"), unit(s.seconds, "second"),
        ).joinToString(" ")
        return "$header\n$body to go"
    }
}
