# Doomsday Countdown 2.0

Android app + home-screen widget counting down to **17 December 2026, 12:00 AM IST (Asia/Kolkata)**.
Package ID: `com.pete.doomsdaywidget` · Kotlin · minSdk 26 (Android 8.0) · target/compile SDK 35.

> **Build status, honestly:** this project was written in an environment with no Android SDK and no
> network, so the **APK could not be built there and the app has never been run on a device or
> emulator**. What *was* verified is listed under "Validation results" below. Expect to build it
> yourself (two easy ways below) and report any error you hit.

## Get the APK

**Option A - Android Studio (easiest)**
1. Install Android Studio (Koala or newer) and open the `DoomsdayCountdown` folder.
2. Let Gradle sync (it downloads the Android SDK 35 / Gradle 8.9 automatically).
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The file is `app/build/outputs/apk/debug/app-debug.apk`.
4. Copy it to your phone and open it (allow "install unknown apps" for your file manager), or run it directly with a USB-connected phone.

**Option B - command line**
```
./gradlew testDebugUnitTest assembleDebug     # Windows: gradlew.bat ...
```
Needs JDK 17 and the Android SDK (`ANDROID_HOME`, platform 35, build-tools 35.0.0).

**Option C - GitHub Actions (no installs)**
Push this folder to a GitHub repository. `.github/workflows/build-apk.yml` runs the unit tests,
builds the APK and attaches it as a downloadable artifact on the run page.

## Features
- Five units in one row: MONTHS : DAYS : HOURS : MINUTES : SECONDS, labels directly underneath, colon separators. Digit size is solved from the available width/height so nothing clips or overlaps (phones, tablets, portrait, landscape).
- Doctor Doom (green/black) -> Iron Man (red/gold, cyan reactor) theme that blends **automatically** on a fixed timeline (see below). Manual preview: Settings > Theme preview.
- Original Arc-Reactor-style animation (glowing core, rotating dashed rings, coils). Still image in battery mode.
- Home-screen widget, resizable, same engine and theme as the app.
- Custom wallpaper (Storage Access Framework) for app **and** widget; restore default.
- Ticking-audio import (MP3/WAV/OGG/M4A/AAC/FLAC), play/pause/stop, volume.
- Share as text, immersive fullscreen (tap or Back to exit), battery-friendly mode, restore defaults.
- No Marvel logos, footage or audio are included. All artwork is original and drawn in code.

## The countdown engine (`countdown/CountdownEngine.kt`)
One pure-Kotlin class, used by the app, the widget, the share text and the tests.
1. "now" is truncated to a whole second and converted to `Asia/Kolkata`.
2. If now >= target: all zeros (never negative, never restarts).
3. `months` = whole calendar months to the target; "now" is moved forward by that many months (day-of-month clamps: 31 Jan + 1 month = 28/29 Feb).
4. `days` = whole days from there; the rest (< 24 h) is split into hours/minutes/seconds.
Because it uses `java.time`, month lengths and leap years are handled by the library. `ClockGuard` ignores tiny backwards clock jitter (<= 5 s) but follows a real clock change by the user.
The app updates once per second via `SecondTicker`, aligned to the second boundary; it starts in `onStart`, stops in `onStop`, and `start()` is idempotent so there is never a duplicate timer.

## Theme schedule (`theme/ThemeSchedule.kt`)
`ThemeConfig` (edit `BLEND_START_DAYS_BEFORE` / `BLEND_END_DAYS_BEFORE`):
pure Doctor Doom until **30 days** before the event (17 Nov 2026), smooth-step blend, fully Iron Man from **7 days** before (10 Dec 2026). It is a pure function of the clock, so it survives closing/reopening and is identical in the widget.

## Importing your wallpaper and audio
Open the app > gear icon > Settings.
- **Wallpaper:** *Choose image*, pick a photo. A downscaled private copy is stored, so deleting the original later cannot break anything. *Restore default* brings back the cinematic background.
- **Audio:** *Choose audio file*, pick your ticking file. Press **Play** (in Settings or the play button on the main screen). Audio never starts by itself, loops until paused, and is stopped and released when you leave the app. If the file is later moved/deleted or access is lost, you get a message and can pick it again.

## Adding the widget
Long-press an empty spot on the home screen > **Widgets** > **Doomsday Countdown** > drag it out. Resize by long-pressing it.
Widget limits (Android, not this app): launchers decide refresh behaviour, so per-second app updates are impossible.
Instead MINUTES:SECONDS is a system `Chronometer` that the launcher ticks itself, and MONTHS/DAYS/HOURS are re-sent by an alarm exactly 1 s after every hour boundary (plus a 30-minute system update, and after reboot/clock change). The alarm is non-waking, so a sleeping phone catches up when it wakes. If exact alarms are denied by your device, the update may arrive a little late and the seconds can briefly show a minus sign until it does. Doze/OEM battery "optimizers" that kill background work can delay it too; exempt the app if that happens.

## Layout of the project
```
app/src/main/java/com/pete/doomsdaywidget/
  countdown/  CountdownEngine.kt  SecondTicker.kt
  theme/      ThemeSchedule.kt    ThemePalette.kt
  data/       AppPrefs.kt         WallpaperStore.kt
  media/      AudioController.kt
  ui/         MainActivity  SettingsActivity  CountdownView  ArcReactorView  AtmosphereView
  widget/     DoomsdayWidgetProvider  WidgetUpdater  WidgetRenderer  WidgetBackground
  DoomsdayApp.kt
app/src/test/.../ CountdownEngineTest.kt  ThemeScheduleTest.kt
```

## Validation results (what was and was not checked)
| Check | Result |
|---|---|
| Unit tests: engine + theme schedule (20 tests: normal, month boundary, year boundary, leap year, final minute, final second, reached, already passed, zone handling, strict 1-second stepping, rollover, clock guard, share text) | **Passed** - compiled with Kotlin 2.0.21 and run under JUnit 4.13.2 on JDK 21 |
| Bug found and fixed by those tests | `ClockGuard` crashed on first call (overflow) |
| Gradle wrapper (8.9) generated | Done; **dependency resolution not run** (no network) |
| Kotlin syntax of all Android sources | No syntax errors; **full compilation not possible** (no Android SDK) |
| Resource cross-check (every `R.*` / `@...` reference exists; portrait and landscape ids match) | Passed |
| XML well-formedness (layouts, manifest, drawables, values) | Passed |
| Debug APK build, lint | **Not run** |
| Emulator / device runtime test, widget on a real launcher, audio, wallpaper picker | **Not run** |

## Known limitations
- The first build may reveal compile or layout errors I could not catch without the SDK. Send me the full Gradle error and it will be fixed.
- Widget hour/minute alignment: the MINUTES:SECONDS field is a Chronometer ("MM:SS"), so the two labels under it are split evenly.
- Photos with EXIF rotation on Android 8.x may import rotated (Android 9+ handles it).
- Debug-signed APK only; for the Play Store you would need a release signing key.
- `USE_EXACT_ALARM` is declared for the widget refresh; fine for sideloading, but Google Play restricts this permission.
