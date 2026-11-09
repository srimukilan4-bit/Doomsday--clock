package com.pete.doomsdaywidget

import com.pete.doomsdaywidget.countdown.CountdownConfig
import com.pete.doomsdaywidget.theme.ThemeConfig
import com.pete.doomsdaywidget.theme.ThemeSchedule
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeScheduleTest {
    private val start = Instant.parse("2026-11-01T00:00:00Z")
    private val end = Instant.parse("2026-11-11T00:00:00Z")
    private val s = ThemeSchedule(start, end)

    @Test fun before_start_is_pure_doom_and_after_end_is_pure_iron_man() {
        assertEquals(0f, s.progress(start.minusSeconds(1)), 0f)
        assertEquals(0f, s.progress(start), 0f)
        assertEquals(1f, s.progress(end), 0f)
        assertEquals(1f, s.progress(end.plusSeconds(99999)), 0f)
    }

    @Test fun midpoint_is_half_and_progress_never_decreases() {
        assertEquals(0.5f, s.progress(Instant.parse("2026-11-06T00:00:00Z")), 0.0001f)
        var prev = -1f
        var t = start.minusSeconds(3600)
        repeat(300) {
            val p = s.progress(t)
            assertTrue(p in 0f..1f)
            assertTrue(p >= prev)
            prev = p
            t = t.plusSeconds(3600)
        }
    }

    @Test fun default_schedule_is_doom_today_and_iron_man_at_the_event() {
        assertEquals(0f, ThemeConfig.schedule.progress(Instant.parse("2026-11-07T00:00:00Z")), 0f)
        assertEquals(1f, ThemeConfig.schedule.progress(CountdownConfig.TARGET.toInstant()), 0f)
        assertEquals(1f, ThemeConfig.schedule.progress(CountdownConfig.TARGET.minusDays(7).toInstant()), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalid_schedule_is_rejected() { ThemeSchedule(end, start) }
}
