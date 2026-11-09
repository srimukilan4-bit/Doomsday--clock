package com.pete.doomsdaywidget.ui

import android.app.AlertDialog
import android.content.ColorStateList
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pete.doomsdaywidget.R
import com.pete.doomsdaywidget.countdown.CountdownConfig
import com.pete.doomsdaywidget.data.AppPrefs
import com.pete.doomsdaywidget.data.WallpaperStore
import com.pete.doomsdaywidget.media.AudioController
import com.pete.doomsdaywidget.theme.ThemeConfig
import com.pete.doomsdaywidget.theme.ThemeMode
import com.pete.doomsdaywidget.theme.ThemePalette
import com.pete.doomsdaywidget.theme.ThemeResolver
import com.pete.doomsdaywidget.widget.WidgetUpdater
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SettingsActivity : ComponentActivity(), AudioController.Listener {

    private lateinit var prefs: AppPrefs
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var btnThemeAuto: Button
    private lateinit var btnThemeDoom: Button
    private lateinit var btnThemeIron: Button
    private lateinit var txtThemeInfo: TextView
    private lateinit var imgWallpaper: ImageView
    private lateinit var txtWallpaperStatus: TextView
    private lateinit var txtAudioName: TextView
    private lateinit var btnAudioRemove: Button
    private lateinit var btnAudioPlay: Button
    private lateinit var btnAudioStop: Button
    private lateinit var seekVolume: SeekBar
    private lateinit var switchBattery: Switch

    private var palette: ThemePalette = ThemePalette.DOOM

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importWallpaper(uri)
    }
    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importAudio(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = AppPrefs.get(this)

        val scroll = findViewById<ScrollView>(R.id.settings_root)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        btnThemeAuto = findViewById(R.id.btn_theme_auto)
        btnThemeDoom = findViewById(R.id.btn_theme_doom)
        btnThemeIron = findViewById(R.id.btn_theme_iron)
        txtThemeInfo = findViewById(R.id.txt_theme_info)
        btnThemeAuto.setOnClickListener { setThemeMode(ThemeMode.AUTO) }
        btnThemeDoom.setOnClickListener { setThemeMode(ThemeMode.DOOM) }
        btnThemeIron.setOnClickListener { setThemeMode(ThemeMode.IRON_MAN) }

        imgWallpaper = findViewById(R.id.img_wallpaper_preview)
        txtWallpaperStatus = findViewById(R.id.txt_wallpaper_status)
        findViewById<Button>(R.id.btn_wallpaper_choose).setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }
        findViewById<Button>(R.id.btn_wallpaper_restore).setOnClickListener { restoreWallpaper() }

        txtAudioName = findViewById(R.id.txt_audio_name)
        btnAudioRemove = findViewById(R.id.btn_audio_remove)
        btnAudioPlay = findViewById(R.id.btn_audio_play)
        btnAudioStop = findViewById(R.id.btn_audio_stop)
        seekVolume = findViewById(R.id.seek_volume)
        findViewById<Button>(R.id.btn_audio_choose).setOnClickListener {
            pickAudio.launch(arrayOf("audio/*", "application/ogg"))
        }
        btnAudioRemove.setOnClickListener { removeAudio() }
        btnAudioPlay.setOnClickListener { AudioController.toggle(this) }
        btnAudioStop.setOnClickListener { AudioController.stop() }
        seekVolume.progress = (prefs.volume * 100).toInt()
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = progress / 100f
                prefs.volume = v
                AudioController.setVolume(v)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })

        switchBattery = findViewById(R.id.switch_battery)
        switchBattery.isChecked = prefs.batterySaver
        switchBattery.setOnCheckedChangeListener { _, checked -> prefs.batterySaver = checked }

        findViewById<Button>(R.id.btn_restore_defaults).setOnClickListener { confirmRestoreDefaults() }

        refreshAll()
    }

    override fun onStart() {
        super.onStart()
        AudioController.addListener(this)
        refreshAudioUi()
    }

    override fun onStop() {
        AudioController.removeListener(this)
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    // ---------- refresh ----------

    private fun refreshAll() {
        refreshThemeUi()
        refreshWallpaperUi()
        refreshAudioUi()
    }

    // ---------- theme ----------

    private fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        refreshThemeUi()
        WidgetUpdater.updateAll(this) // the widget follows the same theme immediately
    }

    private fun refreshThemeUi() {
        val mode = prefs.themeMode
        btnThemeAuto.isSelected = mode == ThemeMode.AUTO
        btnThemeDoom.isSelected = mode == ThemeMode.DOOM
        btnThemeIron.isSelected = mode == ThemeMode.IRON_MAN

        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(CountdownConfig.ZONE)
        txtThemeInfo.text = getString(
            R.string.theme_info,
            fmt.format(ThemeConfig.schedule.blendStart),
            fmt.format(ThemeConfig.schedule.blendEnd),
        )

        palette = ThemeResolver.palette(ThemeResolver.targetProgress(mode, Instant.now()))
        val accent = ColorStateList.valueOf(palette.mid)
        listOf(R.id.hdr_theme, R.id.hdr_wallpaper, R.id.hdr_audio, R.id.hdr_battery).forEach {
            findViewById<TextView>(it).setTextColor(palette.mid)
        }
        seekVolume.progressTintList = accent
        seekVolume.thumbTintList = accent
        switchBattery.thumbTintList = accent
        switchBattery.trackTintList = ColorStateList.valueOf(palette.dark)
        if (!WallpaperStore.hasCustom(this)) showDefaultPreview()
    }

    // ---------- wallpaper ----------

    private fun showDefaultPreview() {
        imgWallpaper.setImageDrawable(null)
        imgWallpaper.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(palette.bgTop, palette.bgBottom),
        )
    }

    private fun refreshWallpaperUi() {
        if (prefs.wallpaperStamp == 0L || !WallpaperStore.hasCustom(this)) {
            txtWallpaperStatus.setText(R.string.wallpaper_default)
            showDefaultPreview()
            return
        }
        txtWallpaperStatus.setText(R.string.wallpaper_custom)
        val app = applicationContext
        io.execute {
            val bmp = WallpaperStore.load(app, 900)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (bmp != null) {
                    imgWallpaper.setImageBitmap(bmp)
                } else {
                    prefs.wallpaperStamp = 0L
                    WallpaperStore.clear(app)
                    toast(R.string.wallpaper_unavailable)
                    refreshWallpaperUi()
                }
            }
        }
    }

    private fun importWallpaper(uri: Uri) {
        val app = applicationContext
        // Keep access to the original where the provider allows it (best effort).
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        io.execute {
            val ok = WallpaperStore.importFrom(app, uri)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (ok) {
                    prefs.wallpaperStamp = System.currentTimeMillis()
                    toast(R.string.wallpaper_imported)
                    refreshWallpaperUi()
                    WidgetUpdater.updateAll(this)
                } else {
                    toast(R.string.wallpaper_failed)
                }
            }
        }
    }

    private fun restoreWallpaper() {
        WallpaperStore.clear(this)
        prefs.wallpaperStamp = 0L
        refreshWallpaperUi()
        WidgetUpdater.updateAll(this)
    }

    // ---------- audio ----------

    private fun refreshAudioUi() {
        val uri = prefs.audioUri
        val has = uri != null
        txtAudioName.text = when {
            !has -> getString(R.string.audio_none)
            !prefs.hasAudioPermission() ->
                getString(R.string.audio_selected_fmt, prefs.audioName ?: "?") + "\n" +
                    getString(R.string.audio_permission_lost)
            else -> getString(R.string.audio_selected_fmt, prefs.audioName ?: "?")
        }
        btnAudioRemove.isEnabled = has
        btnAudioPlay.isEnabled = has
        btnAudioStop.isEnabled = has
        btnAudioPlay.setText(if (AudioController.isBusy) R.string.audio_pause else R.string.audio_play)
    }

    private fun importAudio(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Some providers do not offer persistable grants; playback still works this session.
        }
        val app = applicationContext
        io.execute {
            val playable = AudioController.isPlayableAudio(app, uri)
            val name = AudioController.displayName(app, uri)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (!playable) {
                    prefs.releaseUriPermission(uri.toString())
                    toast(R.string.audio_unsupported)
                    return@runOnUiThread
                }
                val previous = prefs.audioUri
                AudioController.stop()
                if (previous != null && previous != uri.toString()) prefs.releaseUriPermission(previous)
                prefs.audioUri = uri.toString()
                prefs.audioName = name ?: uri.lastPathSegment ?: "audio"
                toast(R.string.audio_saved)
                refreshAudioUi()
            }
        }
    }

    private fun removeAudio() {
        AudioController.stop()
        prefs.releaseUriPermission(prefs.audioUri)
        prefs.audioUri = null
        prefs.audioName = null
        refreshAudioUi()
    }

    override fun onAudioStateChanged() = refreshAudioUi()

    override fun onAudioError(@StringRes message: Int) = toast(message)

    // ---------- defaults ----------

    private fun confirmRestoreDefaults() {
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_title)
            .setMessage(R.string.restore_message)
            .setPositiveButton(R.string.restore_confirm) { _, _ ->
                AudioController.stop()
                WallpaperStore.clear(this)
                prefs.restoreDefaults()
                seekVolume.progress = (prefs.volume * 100).toInt()
                switchBattery.isChecked = prefs.batterySaver
                refreshAll()
                WidgetUpdater.updateAll(this)
                toast(R.string.restored)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(@StringRes res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
}
