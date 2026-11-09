package com.pete.doomsdaywidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pete.doomsdaywidget.countdown.CountdownClock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

/**
 * Refreshes all widget instances and keeps ONE alarm pending for the next refresh.
 *
 * Why an alarm: the launcher ticks the minutes:seconds Chronometer by itself, but months, days
 * and hours are text that must be re-sent when they change. The next change is exactly one
 * second after the next hour boundary of the countdown, so that is when the alarm fires.
 * The alarm is non-waking (RTC): it never wakes a sleeping phone just for a widget; if the
 * phone was asleep, the widget catches up the moment the device is awake again.
 */
object WidgetUpdater {
    const val ACTION_ROLLOVER = "com.pete.doomsdaywidget.action.ROLLOVER"
    private const val ALARM_REQUEST_CODE = 4217
    private val executor = Executors.newSingleThreadExecutor()

    /** Asynchronous: safe to call from the UI thread. */
    fun updateAll(ctx: Context) {
        val app = ctx.applicationContext
        executor.execute { updateAllNow(app) }
    }

    /** Synchronous (decodes images): call from a background thread. */
    fun updateAllNow(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, DoomsdayWidgetProvider::class.java))
        if (ids.isEmpty()) {
            cancelAlarm(ctx)
            return
        }
        update(ctx, mgr, ids)
        scheduleNext(ctx)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try {
                mgr.updateAppWidget(id, WidgetRenderer.build(ctx, mgr.getAppWidgetOptions(id)))
            } catch (_: Exception) {
                // One bad widget instance must never stop the others.
            }
        }
    }

    fun scheduleNext(ctx: Context) {
        val alarms = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = alarmIntent(ctx)
        val now = Instant.now()
        val st = CountdownClock.engine.compute(now)
        if (st.reached) {
            alarms.cancel(pi)
            return
        }
        val triggerAt = now.truncatedTo(ChronoUnit.SECONDS)
            .plusSeconds(st.secondsUntilRollover % 3600L + 1L)
            .toEpochMilli()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
        }
    }

    fun cancelAlarm(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(alarmIntent(ctx))
    }

    private fun alarmIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, DoomsdayWidgetProvider::class.java).setAction(ACTION_ROLLOVER)
        return PendingIntent.getBroadcast(
            ctx, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
