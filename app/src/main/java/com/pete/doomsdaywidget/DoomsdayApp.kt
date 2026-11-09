package com.pete.doomsdaywidget

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.pete.doomsdaywidget.media.AudioController

/**
 * Application class. Its only job is the audio lifecycle: when no activity of this app is
 * visible any more (home pressed, app switched away, app closed) the ticking audio is stopped.
 * Screen rotation does not count as leaving the app.
 */
class DoomsdayApp : Application() {
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        AudioController.attach(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++ }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0 && !activity.isChangingConfigurations) {
                    AudioController.stop()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
