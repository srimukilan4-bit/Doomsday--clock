package com.pete.doomsdaywidget

import com.pete.doomsdaywidget.countdown.ClockGuard
import com.pete.doomsdaywidget.countdown.CountdownConfig
import com.pete.doomsdaywidget.countdown.CountdownEngine
import com.pete.doomsdaywidget.countdown.CountdownFormat
import com.pete.doomsdaywidget.countdown.CountdownState
import java.time.Instant
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownEngineTest {

    private val zone = CountdownConfig.ZONE
    private val engine = CountdownEngine() // 17 Dec 2026 00:00 IST

    private fun ist(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0, n: Int = 0): Instant =
        ZonedDateTime.of(y, mo, d, h, mi, s, n, zone).toInstant()

    private fun assertState(st: CountdownState, mo: Int, d: Int, h: Int, mi: Int, s: Int) {
        assertEquals("months", mo, st.months)
        assertEquals("days", d, st.days)
        assertEquals("hours", h, st.hours)
        assertEquals("minutes", mi, st.minutes)
        assertEquals("seconds", s, st.seconds)
    }

    @Test fun target_is_17_december_2026_midnight_ist() {
        val t = CountdownConfig.TARGET
        assertEquals(2026, t.year); assertEquals(12, t.monthValue); assertEquals(17, t.dayOfMonth)
        assertEquals(0, t.hour); assertEquals(0, t.minute)
        assertEquals("Asia/Kolkata", t.zone.id)
        // 17 Dec 00:00 IST == 16 Dec 18:30 UTC
        assertEquals(Instant.parse("2026-12-16T18:30:00Z"), t.toInstant())
    }

    @Test fun normal_countdown() {
        val st = engine.compute(ist(2026, 11, 6, 21, 29, 0))
        assertState(st, 1, 10, 2, 31, 0)
        assertFalse(st.reached)
    }

    @Test fun month_boundary_exact_month_then_one_second_later() {
        assertState(engine.compute(ist(2026, 11, 17, 0, 0, 0)), 1, 0, 0, 0, 0)
        assertState(engine.compute(ist(2026, 11, 17, 0, 0, 1)), 0, 29, 23, 59, 59)
    }

    @Test fun month_boundary_with_31_day_start_clamps_correctly() {
        // 31 Oct + 1 month clamps to 30 Nov; then 16 days 12 h remain.
        assertState(engine.compute(ist(2026, 10, 31, 12, 0, 0)), 1, 16, 12, 0, 0)
    }

    @Test fun year_boundary() {
        val e = CountdownEngine(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, zone))
        assertState(e.compute(ist(2026, 12, 31, 23, 59, 50)), 0, 0, 0, 0, 10)
        assertState(e.compute(ist(2026, 12, 1, 0, 0, 0)), 1, 0, 0, 0, 0)
        assertState(e.compute(ist(2026, 11, 30, 12, 0, 0)), 1, 1, 12, 0, 0)
    }

    @Test fun leap_year_vs_non_leap_year() {
        val leap = CountdownEngine(ZonedDateTime.of(2028, 3, 1, 0, 0, 0, 0, zone))
        assertState(leap.compute(ist(2028, 2, 1)), 1, 0, 0, 0, 0)
        assertState(leap.compute(ist(2028, 2, 28)), 0, 2, 0, 0, 0)      // 28 Feb, 29 Feb, then 1 Mar
        assertState(leap.compute(ist(2028, 1, 31)), 1, 1, 0, 0, 0)      // 31 Jan + 1 month = 29 Feb

        val plain = CountdownEngine(ZonedDateTime.of(2027, 3, 1, 0, 0, 0, 0, zone))
        assertState(plain.compute(ist(2027, 2, 28)), 0, 1, 0, 0, 0)     // no 29 Feb in 2027
    }

    @Test fun final_minute() {
        assertState(engine.compute(ist(2026, 12, 16, 23, 59, 0)), 0, 0, 0, 1, 0)
        assertState(engine.compute(ist(2026, 12, 16, 23, 59, 30)), 0, 0, 0, 0, 30)
    }

    @Test fun final_second() {
        val st = engine.compute(ist(2026, 12, 16, 23, 59, 59))
        assertState(st, 0, 0, 0, 0, 1)
        assertFalse(st.reached)
        // A fractional "now" must never run ahead of the whole second.
        val frac = engine.compute(ist(2026, 12, 16, 23, 59, 59, 900_000_000))
        assertState(frac, 0, 0, 0, 0, 1)
        assertFalse(frac.reached)
    }

    @Test fun target_reached_shows_all_zeros() {
        val st = engine.compute(ist(2026, 12, 17, 0, 0, 0))
        assertState(st, 0, 0, 0, 0, 0)
        assertTrue(st.reached)
        assertEquals(0L, st.totalSecondsRemaining)
    }

    @Test fun reached_is_independent_of_the_instants_own_zone() {
        // Exactly the target, expressed in UTC.
        assertTrue(engine.compute(Instant.parse("2026-12-16T18:30:00Z")).reached)
        // One second before, in UTC.
        assertFalse(engine.compute(Instant.parse("2026-12-16T18:29:59Z")).reached)
    }

    @Test fun target_already_passed_never_goes_negative() {
        for (now in listOf(ist(2026, 12, 17, 0, 0, 1), ist(2026, 12, 18), ist(2030, 1, 1), ist(2100, 6, 1))) {
            val st = engine.compute(now)
            assertState(st, 0, 0, 0, 0, 0)
            assertTrue(st.reached)
            assertEquals(0L, st.totalSecondsRemaining)
        }
    }

    private fun assertStrictCountdown(from: Instant, seconds: Int) {
        var prev = Long.MAX_VALUE
        var t = from
        repeat(seconds) {
            val st = engine.compute(t)
            assertTrue(st.months >= 0 && st.days >= 0)
            assertTrue(st.hours in 0..23 && st.minutes in 0..59 && st.seconds in 0..59)
            assertTrue("never increases", st.totalSecondsRemaining <= prev)
            if (prev != Long.MAX_VALUE && prev > 0) assertEquals("exactly one second per step", prev - 1, st.totalSecondsRemaining)
            prev = st.totalSecondsRemaining
            t = t.plusSeconds(1)
        }
    }

    @Test fun counts_down_one_second_at_a_time_across_the_target() {
        assertStrictCountdown(ist(2026, 12, 16, 22, 0, 0), 3 * 60 * 60) // 22:00 -> 01:00, crosses the target
        assertEquals(0L, engine.compute(ist(2026, 12, 17, 1, 0, 0)).totalSecondsRemaining)
    }

    @Test fun counts_down_one_second_at_a_time_across_a_month_rollover() {
        assertStrictCountdown(ist(2026, 11, 16, 22, 0, 0), 3 * 60 * 60) // crosses 17 Nov 00:00
    }

    @Test fun rollover_matches_when_day_units_change() {
        val now = ist(2026, 11, 6, 21, 29, 0)
        val st = engine.compute(now)
        assertEquals(2 * 3600 + 31 * 60L, st.secondsUntilRollover)
        val atRollover = engine.compute(now.plusSeconds(st.secondsUntilRollover))
        assertState(atRollover, 1, 10, 0, 0, 0)
        val after = engine.compute(now.plusSeconds(st.secondsUntilRollover + 1))
        assertState(after, 1, 9, 23, 59, 59)
    }

    @Test fun clock_guard_ignores_tiny_backwards_steps_but_follows_real_changes() {
        val g = ClockGuard(5_000L)
        val t0 = Instant.parse("2026-11-07T10:00:00Z")
        assertEquals(t0, g.stable(t0))
        assertEquals(t0, g.stable(t0.minusMillis(800)))           // jitter ignored
        val later = t0.plusSeconds(3)
        assertEquals(later, g.stable(later))                       // forward is accepted
        val manual = later.minusSeconds(3600)
        assertEquals(manual, g.stable(manual))                     // real clock change is followed
    }

    @Test fun share_text_contains_title_date_and_values() {
        val txt = CountdownFormat.shareText(engine.compute(ist(2026, 11, 6, 21, 29, 0)))
        assertTrue(txt.contains("Avengers: Doomsday"))
        assertTrue(txt.contains("17 December 2026"))
        assertTrue(txt.contains("1 month 10 days 2 hours 31 minutes 0 seconds to go"))
        assertTrue(CountdownFormat.shareText(CountdownState.ZERO).contains("zero"))
    }
}
