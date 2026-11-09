package com.pete.doomsdaywidget.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import com.pete.doomsdaywidget.R
import com.pete.doomsdaywidget.data.AppPrefs

/**
 * App-wide ticking-audio player (one MediaPlayer, ever).
 *
 * Lifecycle design: audio NEVER starts by itself. It starts only when the user presses play.
 * When the app leaves the foreground ([DoomsdayApp]) the player is stopped and released, so
 * nothing keeps playing or holding resources in the background. Playback loops until stopped.
 * All calls must be made on the main thread.
 */
object AudioController {

    interface Listener {
        fun onAudioStateChanged()
        fun onAudioError(@StringRes message: Int) {}
    }

    private val listeners = LinkedHashSet<Listener>()
    private var player: MediaPlayer? = null
    private var preparing = false
    private var focusRequest: AudioFocusRequest? = null

    val isPlaying: Boolean
        get() = try {
            player?.isPlaying == true
        } catch (_: IllegalStateException) {
            false
        }

    val isBusy: Boolean get() = isPlaying || preparing

    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private fun notifyState() = listeners.toList().forEach { it.onAudioStateChanged() }
    private fun notifyError(@StringRes res: Int) = listeners.toList().forEach { it.onAudioError(res) }

    fun toggle(ctx: Context) {
        if (isBusy) pause() else play(ctx)
    }

    fun play(ctx: Context) {
        if (isBusy) return
        val app = ctx.applicationContext
        val prefs = AppPrefs.get(app)
        val uriString = prefs.audioUri
        if (uriString == null) {
            notifyError(R.string.audio_none_selected)
            return
        }
        // Reuse a prepared, paused player.
        player?.let { p ->
            try {
                if (requestFocus(app)) {
                    applyVolume(prefs.volume)
                    p.start()
                    notifyState()
                }
                return
            } catch (_: IllegalStateException) {
                releasePlayer()
            }
        }
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mp.setDataSource(app, Uri.parse(uriString))
            mp.isLooping = true
            mp.setOnPreparedListener { prepared ->
                preparing = false
                if (player !== prepared) return@setOnPreparedListener
                if (requestFocus(app)) {
                    applyVolume(prefs.volume)
                    prepared.start()
                }
                notifyState()
            }
            mp.setOnErrorListener { _, _, _ ->
                releasePlayer()
                notifyError(R.string.audio_error)
                notifyState()
                true
            }
            preparing = true
            mp.prepareAsync()
            notifyState()
        } catch (_: Exception) {
            // SecurityException (permission lost), IOException (file gone/unsupported), etc.
            releasePlayer()
            notifyError(R.string.audio_error)
            notifyState()
        }
    }

    fun pause() {
        try {
            if (player?.isPlaying == true) player?.pause()
        } catch (_: IllegalStateException) {
            releasePlayer()
        }
        if (preparing) releasePlayer() // cancel a pending start
        abandonFocus()
        notifyState()
    }

    /** Stop playback and free the player. */
    fun stop() {
        releasePlayer()
        notifyState()
    }

    /** Applies the volume to the live player. Persisting it is the caller's job (AppPrefs.volume). */
    fun setVolume(volume: Float) = applyVolume(volume.coerceIn(0f, 1f))

    private var appCtxOrNull: Context? = null
    fun attach(ctx: Context) { appCtxOrNull = ctx.applicationContext }

    private fun applyVolume(v: Float) {
        try {
            player?.setVolume(v, v)
        } catch (_: IllegalStateException) {
        }
    }

    private fun releasePlayer() {
        preparing = false
        val p = player
        player = null
        try {
            p?.setOnPreparedListener(null)
            p?.setOnErrorListener(null)
            p?.release()
        } catch (_: Exception) {
        }
        abandonFocus()
    }

    private fun requestFocus(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                // Any loss (call, other media app): pause and stay paused. Never auto-resume.
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) pause()
            }
            .build()
        focusRequest = req
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val ctx = appCtxOrNull ?: return
        val req = focusRequest ?: return
        focusRequest = null
        (ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(req)
    }

    // ---- file helpers (call from a background thread) ----

    /** Human-readable file name for a content URI, or null. */
    fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /** True if Android can read this file and it contains an audio track. */
    fun isPlayableAudio(ctx: Context, uri: Uri): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx.applicationContext, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (_: Exception) {
            false
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }
}
