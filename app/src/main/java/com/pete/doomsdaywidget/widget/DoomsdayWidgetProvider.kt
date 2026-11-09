package com.pete.doomsdaywidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.Executors

/**
 * Home-screen widget entry point. Everything heavy runs on a background thread under goAsync(),
 * and the widget is refreshed on: first add, resize, the 30-minute system safety-net update,
 * the exact hour-boundary alarm, device reboot, app update, and system clock/time-zone changes.
 */
class DoomsdayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runAsync {
            WidgetUpdater.update(context, appWidgetManager, appWidgetIds)
            WidgetUpdater.scheduleNext(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        runAsync { WidgetUpdater.update(context, appWidgetManager, intArrayOf(appWidgetId)) }
    }

    override fun onEnabled(context: Context) {
        runAsync { WidgetUpdater.scheduleNext(context) }
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.cancelAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            WidgetUpdater.ACTION_ROLLOVER,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> runAsync { WidgetUpdater.updateAllNow(context) }
        }
    }

    private fun runAsync(work: () -> Unit) {
        val pending = goAsync()
        worker.execute {
            try {
                work()
            } catch (_: Exception) {
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        val worker = Executors.newSingleThreadExecutor()
    }
}
