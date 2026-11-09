package com.pete.doomsdaywidget.countdown

import android.os.Handler
import android.os.Looper

/**
 * Calls [onTick] once per wall-clock second on the main thread, aligned to the second boundary.
 * start() is idempotent (never creates a second timer job) and stop() removes every pending
 * callback, so there is nothing to leak when the screen goes away.
 */
class SecondTicker(private val onTick: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val runnable = object : Runnable {
        override fun run() {
            if (!running) return
            onTick()
            if (running) scheduleNext()
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.removeCallbacks(runnable)
        handler.post(runnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(runnable)
    }

    private fun scheduleNext() {
        val intoSecond = System.currentTimeMillis() % 1000L
        handler.postDelayed(runnable, 1000L - intoSecond + 5L)
    }
}
