package com.pete.doomsdaywidget.ui

import android.animation.ValueAnimator
import android.content.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pete.doomsdaywidget.R
import com.pete.doomsdaywidget.countdown.CountdownClock
import com.pete.doomsdaywidget.countdown.CountdownFormat
import com.pete.doomsdaywidget.countdown.SecondTicker
import com.pete.doomsdaywidget.data.AppPrefs
import com.pete.doomsdaywidget.data.WallpaperStore
import com.pete.doomsdaywidget.media.AudioController
import com.pete.doomsdaywidget.theme.ThemePalette
import com.pete.doomsdaywidget.theme.ThemeResolver
import com.pete.doomsdaywidget.widget.WidgetUpdater
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

class MainActivity : ComponentActivity(), AudioController.Listener {

    private lateinit var prefs: AppPrefs
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var wallpaperView: ImageView
    private lateinit var atmosphere: AtmosphereView
    private lateinit var titleView: TextView
    private lateinit var dateView: TextView
    private lateinit var reactor: ArcReactorView
    private lateinit var countdown: CountdownView
    private lateinit var toolbar: LinearLayout
    private lateinit var btnAudio: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var btnSettings: ImageButton

    private val ticker = SecondTicker { onTick() }
    private var io: ExecutorService? = null

    private var displayedProgress = -1f
    private var themeAnimator: ValueAnimator? = null
    private var reduceMotion = false
    private var loadedWallpaperStamp = -1L
    private var fullscreen = false

    private val exitFullscreenOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setFullscreen(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = AppPrefs.get(this)

        root = findViewById(R.id.root)
        content = findViewById(R.id.content)
        wallpaperView = findViewById(R.id.wallpaper)
        atmosphere = findViewById(R.id.atmosphere)
        titleView = findViewById(R.id.title)
        dateView = findViewById(R.id.date)
        reactor = findViewById(R.id.reactor)
        countdown = findViewById(R.id.countdown)
        toolbar = findViewById(R.id.toolbar)
        btnAudio = findViewById(R.id.btn_audio)
        btnShare = findViewById(R.id.btn_share)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        btnSettings = findViewById(R.id.btn_settings)

        val pad = resources.getDimensionPixelSize(R.dimen.screen_padding)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad)
            insets
        }

        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnShare.setOnClickListener { shareCountdown() }
        btnFullscreen.setOnClickListener { setFullscreen(true) }
        btnAudio.setOnClickListener { AudioController.toggle(this) }
        root.setOnClickListener { if (fullscreen) setFullscreen(false) }
        root.isClickable = false
        onBackPressedDispatcher.addCallback(this, exitFullscreenOnBack)

        if (savedInstanceState?.getBoolean(STATE_FULLSCREEN) == true) setFullscreen(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FULLSCREEN, fullscreen)
    }

    override fun onStart() {
        super.onStart()
        io = Executors.newSingleThreadExecutor()
        applyMotionSetting()
        refreshWallpaper()
        onTick() // paint immediately (a changed theme setting cross-fades), then tick once per second
        ticker.start()
        reactor.setHostActive(true)
        atmosphere.setHostActive(true)
        AudioController.addListener(this)
        updateAudioButton()
        if (fullscreen) applyFullscreenWindowState(true)
        WidgetUpdater.updateAll(this)
    }

    override fun onStop() {
        ticker.stop()
        themeAnimator?.cancel()
        reactor.setHostActive(false)
        atmosphere.setHostActive(false)
        AudioController.removeListener(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        io?.shutdownNow()
        io = null
        super.onStop()
    }

    // ---------- per-second work ----------

    private fun onTick() {
        val now = CountdownClock.now()
        countdown.setState(CountdownClock.engine.compute(now))
        applyThemeFor(ThemeResolver.targetProgress(prefs.themeMode, now))
    }

    private fun applyMotionSetting() {
        reduceMotion = prefs.reduceMotion()
        reactor.setAnimationAllowed(!reduceMotion)
        atmosphere.setAnimationAllowed(!reduceMotion)
        countdown.setAnimateTicks(!reduceMotion)
    }

    // ---------- theme ----------

    private fun applyThemeFor(target: Float) {
        if (displayedProgress < 0f) {                      // first paint: no animation
            setThemeProgress(target)
            return
        }
        val diff = target - displayedProgress
        if (themeAnimator?.isRunning == true || abs(diff) < THEME_EPSILON) return
        if (abs(diff) > THEME_ANIMATE_THRESHOLD && !reduceMotion) {
            // A big jump (e.g. the user picked a theme preview): cross-fade smoothly.
            themeAnimator = ValueAnimator.ofFloat(displayedProgress, target).apply {
                duration = 700
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { setThemeProgress(it.animatedValue as Float) }
                start()
            }
        } else {
            setThemeProgress(target) // tiny step of the slow automatic blend
        }
    }

    private fun setThemeProgress(progress: Float) {
        displayedProgress = progress
        applyPalette(ThemeResolver.palette(progress))
    }

    private fun applyPalette(p: ThemePalette) {
        root.setBackgroundColor(p.bgTop)
        atmosphere.setPalette(p)
        reactor.setPalette(p)
        countdown.setPalette(p)
        titleView.setTextColor(p.light)
        titleView.setShadowLayer(22f, 0f, 0f, p.glow)
        dateView.setTextColor(p.mid)
        val tint = ColorStateList.valueOf(p.light)
        listOf(btnAudio, btnShare, btnFullscreen, btnSettings).forEach { it.imageTintList = tint }
    }

    // ---------- wallpaper ----------

    private fun refreshWallpaper() {
        val stamp = prefs.wallpaperStamp
        if (stamp == loadedWallpaperStamp) return
        if (stamp == 0L || !WallpaperStore.hasCustom(this)) {
            loadedWallpaperStamp = stamp
            showDefaultBackground()
            return
        }
        val dm = resources.displayMetrics
        val target = max(dm.widthPixels, dm.heightPixels)
        val app = applicationContext
        io?.execute {
            val bmp = WallpaperStore.load(app, target)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (bmp == null) {
                    prefs.wallpaperStamp = 0L
                    WallpaperStore.clear(app)
                    loadedWallpaperStamp = 0L
                    showDefaultBackground()
                    toast(R.string.wallpaper_unavailable)
                } else {
                    // Only now is it "loaded": if the screen stopped mid-load we simply retry next time.
                    loadedWallpaperStamp = stamp
                    wallpaperView.setImageBitmap(bmp)
                    wallpaperView.visibility = View.VISIBLE
                    atmosphere.hasWallpaper = true
                }
            }
        }
    }

    private fun showDefaultBackground() {
        wallpaperView.setImageDrawable(null)
        wallpaperView.visibility = View.GONE
        atmosphere.hasWallpaper = false
    }

    // ---------- share ----------

    private fun shareCountdown() {
        val text = CountdownFormat.shareText(CountdownClock.state())
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.title_doomsday))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser)))
    }

    // ---------- fullscreen ----------

    private fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        exitFullscreenOnBack.isEnabled = enabled
        root.isClickable = enabled
        toolbar.visibility = if (enabled) View.GONE else View.VISIBLE
        applyFullscreenWindowState(enabled)
    }

    private fun applyFullscreenWindowState(enabled: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // Keep the screen on only while in fullscreen, and never in battery-friendly mode.
            if (!prefs.reduceMotion()) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ---------- audio ----------

    private fun updateAudioButton() {
        val playing = AudioController.isBusy
        btnAudio.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        btnAudio.contentDescription = getString(if (playing) R.string.cd_pause_audio else R.string.cd_play_audio)
    }

    override fun onAudioStateChanged() = updateAudioButton()

    override fun onAudioError(@StringRes message: Int) {
        toast(message)
        if (message == R.string.audio_none_selected) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun toast(@StringRes res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()

    private companion object {
        const val STATE_FULLSCREEN = "fullscreen"
        const val THEME_EPSILON = 0.0005f
        const val THEME_ANIMATE_THRESHOLD = 0.02f
    }
}
