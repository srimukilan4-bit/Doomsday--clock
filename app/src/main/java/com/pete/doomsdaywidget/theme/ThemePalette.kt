package com.pete.doomsdaywidget.theme

import androidx.core.graphics.ColorUtils
import com.pete.doomsdaywidget.data.AppPrefs
import java.time.Instant

/** User-selectable theme mode. AUTO follows the countdown timeline; the others are manual previews. */
enum class ThemeMode { AUTO, DOOM, IRON_MAN }

/** All colours for one visual identity (or a blend of the two). */
data class ThemePalette(
    val bgTop: Int,
    val bgBottom: Int,
    val light: Int,   // metallic highlight (top of the numerals)
    val mid: Int,     // main accent
    val dark: Int,    // metallic shadow (bottom of the numerals)
    val glow: Int,    // glow / shadow colour
    val reactor: Int, // arc reactor colour
    val label: Int,   // small captions
) {
    companion object {
        val DOOM = ThemePalette(
            bgTop = 0xFF030705.toInt(),
            bgBottom = 0xFF0A1A10.toInt(),
            light = 0xFFC4F8D2.toInt(),
            mid = 0xFF3DDC84.toInt(),
            dark = 0xFF0F6B3A.toInt(),
            glow = 0xFF1FD66B.toInt(),
            reactor = 0xFF2FE0A8.toInt(),
            label = 0xFF86BFA0.toInt(),
        )

        val IRON_MAN = ThemePalette(
            bgTop = 0xFF0A0304.toInt(),
            bgBottom = 0xFF2B070A.toInt(),
            light = 0xFFFFE493.toInt(),
            mid = 0xFFE8A81C.toInt(),
            dark = 0xFF9B1C1C.toInt(),
            glow = 0xFFE23B2E.toInt(),
            reactor = 0xFF4DE8FF.toInt(),
            label = 0xFFE0B76A.toInt(),
        )

        fun blend(a: ThemePalette, b: ThemePalette, t: Float): ThemePalette {
            if (t <= 0f) return a
            if (t >= 1f) return b
            fun m(x: Int, y: Int) = ColorUtils.blendARGB(x, y, t)
            return ThemePalette(
                m(a.bgTop, b.bgTop), m(a.bgBottom, b.bgBottom), m(a.light, b.light), m(a.mid, b.mid),
                m(a.dark, b.dark), m(a.glow, b.glow), m(a.reactor, b.reactor), m(a.label, b.label),
            )
        }
    }
}

/** The one place that decides which theme blend is active, shared by app and widget. */
object ThemeResolver {
    /** 0 = Doctor Doom, 1 = Iron Man. */
    fun targetProgress(mode: ThemeMode, now: Instant): Float = when (mode) {
        ThemeMode.AUTO -> ThemeConfig.schedule.progress(now)
        ThemeMode.DOOM -> 0f
        ThemeMode.IRON_MAN -> 1f
    }

    fun palette(progress: Float): ThemePalette =
        ThemePalette.blend(ThemePalette.DOOM, ThemePalette.IRON_MAN, progress)

    fun currentProgress(prefs: AppPrefs, now: Instant): Float = targetProgress(prefs.themeMode, now)
}
