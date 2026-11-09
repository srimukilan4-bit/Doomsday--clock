package com.pete.doomsdaywidget.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import com.pete.doomsdaywidget.theme.ThemeMode

/** All persisted settings. Every getter has a safe default so missing/corrupt values never crash. */
class AppPrefs private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = try {
            ThemeMode.valueOf(sp.getString(K_THEME, null) ?: ThemeMode.AUTO.name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.AUTO
        }
        set(v) = sp.edit().putString(K_THEME, v.name).apply()

    var batterySaver: Boolean
        get() = sp.getBoolean(K_BATTERY, false)
        set(v) = sp.edit().putBoolean(K_BATTERY, v).apply()

    var volume: Float
        get() = sp.getFloat(K_VOLUME, 0.6f).coerceIn(0f, 1f)
        set(v) = sp.edit().putFloat(K_VOLUME, v.coerceIn(0f, 1f)).apply()

    var audioUri: String?
        get() = sp.getString(K_AUDIO_URI, null)
        set(v) = sp.edit().putString(K_AUDIO_URI, v).apply()

    var audioName: String?
        get() = sp.getString(K_AUDIO_NAME, null)
        set(v) = sp.edit().putString(K_AUDIO_NAME, v).apply()

    /** 0 = default background; otherwise a changing stamp so views know to reload the image. */
    var wallpaperStamp: Long
        get() = sp.getLong(K_WALLPAPER, 0L)
        set(v) = sp.edit().putLong(K_WALLPAPER, v).apply()

    /** Battery-friendly mode = the user's toggle OR Android's own battery saver. */
    fun reduceMotion(): Boolean {
        if (batterySaver) return true
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isPowerSaveMode == true
    }

    /** Release the persisted read permission of an audio/image URI (best effort). */
    fun releaseUriPermission(uriString: String?) {
        if (uriString == null) return
        try {
            appContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
    }

    /** Is the stored audio URI still readable according to Android's persisted grants? */
    fun hasAudioPermission(): Boolean {
        val stored = audioUri ?: return false
        return try {
            appContext.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == stored && it.isReadPermission
            }
        } catch (_: Exception) {
            false
        }
    }

    fun restoreDefaults() {
        releaseUriPermission(audioUri)
        sp.edit().clear().apply()
    }

    companion object {
        private const val FILE = "doomsday_prefs"
        private const val K_THEME = "theme_mode"
        private const val K_BATTERY = "battery_saver"
        private const val K_VOLUME = "volume"
        private const val K_AUDIO_URI = "audio_uri"
        private const val K_AUDIO_NAME = "audio_name"
        private const val K_WALLPAPER = "wallpaper_stamp"

        @Volatile private var instance: AppPrefs? = null

        fun get(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context).also { instance = it }
            }
    }
}
