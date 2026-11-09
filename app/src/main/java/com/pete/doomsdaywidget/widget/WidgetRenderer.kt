package com.pete.doomsdaywidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.pete.doomsdaywidget.R
import com.pete.doomsdaywidget.countdown.CountdownClock
import com.pete.doomsdaywidget.countdown.CountdownFormat
import com.pete.doomsdaywidget.data.AppPrefs
import com.pete.doomsdaywidget.theme.ThemeResolver
import com.pete.doomsdaywidget.ui.MainActivity
import java.time.Instant

/**
 * Builds the widget's RemoteViews from the SAME CountdownEngine and ThemeResolver as the app.
 *
 * Months, days and hours are plain text, refreshed by [WidgetUpdater] at every hour boundary
 * of the countdown. Minutes:seconds is a system Chronometer in count-down mode, so the
 * launcher itself ticks it every second without any app code running.
 */
object WidgetRenderer {

    fun build(ctx: Context, options: Bundle?): RemoteViews {
        val prefs = AppPrefs.get(ctx)
        val now = Instant.now()
        val st = CountdownClock.engine.compute(now)
        val p = ThemeResolver.palette(ThemeResolver.targetProgress(prefs.themeMode, now))

        val wDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: 250
        val hDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)?.takeIf { it > 0 } ?: 110

        val v = RemoteViews(ctx.packageName, R.layout.widget_countdown)

        try {
            v.setImageViewBitmap(R.id.w_bg, WidgetBackground.render(ctx, wDp, hDp, p))
        } catch (_: Exception) {
        } catch (_: OutOfMemoryError) {
        }

        v.setTextViewText(R.id.w_months, CountdownFormat.two(st.months))
        v.setTextViewText(R.id.w_days, CountdownFormat.two(st.days))
        v.setTextViewText(R.id.w_hours, CountdownFormat.two(st.hours))

        if (st.reached) {
            v.setViewVisibility(R.id.w_mmss, View.GONE)
            v.setViewVisibility(R.id.w_mmss_static, View.VISIBLE)
            v.setTextViewText(R.id.w_mmss_static, "00:00")
        } else {
            // Seconds left until the next hour boundary of the countdown (0..3599).
            val toBoundary = st.secondsUntilRollover % 3600L
            val fractionMs = now.toEpochMilli() % 1000L
            // Chronometer shows floor(remaining); the engine shows ceil(remaining). +999 ms aligns them.
            val base = SystemClock.elapsedRealtime() + toBoundary * 1000L - fractionMs + 999L
            v.setViewVisibility(R.id.w_mmss, View.VISIBLE)
            v.setViewVisibility(R.id.w_mmss_static, View.GONE)
            v.setChronometerCountDown(R.id.w_mmss, true)
            v.setChronometer(R.id.w_mmss, base, "%s", true)
        }

        // theme colours
        v.setTextColor(R.id.w_title, p.mid)
        listOf(R.id.w_months, R.id.w_days, R.id.w_hours, R.id.w_mmss, R.id.w_mmss_static).forEach {
            v.setTextColor(it, p.light)
        }
        listOf(R.id.w_colon1, R.id.w_colon2, R.id.w_colon3).forEach { v.setTextColor(it, p.mid) }
        listOf(R.id.w_lbl_months, R.id.w_lbl_days, R.id.w_lbl_hours, R.id.w_lbl_minutes, R.id.w_lbl_seconds)
            .forEach { v.setTextColor(it, p.label) }

        // very short widgets drop the title line so the numbers stay as large as possible
        v.setViewVisibility(R.id.w_title, if (hDp >= 90) View.VISIBLE else View.GONE)

        v.setContentDescription(
            R.id.w_root,
            ctx.getString(
                R.string.countdown_description, st.months, st.days, st.hours, st.minutes, st.seconds,
            ),
        )

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        v.setOnClickPendingIntent(R.id.w_root, open)
        return v
    }
}
