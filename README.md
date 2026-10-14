# Interval

A Wear OS running app for the Galaxy Watch 4. You build interval workouts on the watch, plan them on any day, and run them with guided wrist cues. Every run is recorded in the background and exported as TCX/GPX.

## Features

**Build workouts on the watch**
- **Templates to start from:** run/walk, easy run, intervals, tempo, run to distance, strides, or blank.
- **Steps:** each has an effort (walk, recover, jog, easy, run, tempo, fast, stride). A step ends after a time or a distance, picked on wheel pickers you turn with your finger or the bezel.
- **Pace targets:** a fixed range (e.g. 6:00-6:30 /km), or your goal pace from Settings plus or minus a few seconds.
- **Repeats:** *Repeat N times* (optionally skipping the last recovery) and *Repeat until X km*.
- **Editing:** rename with the watch keyboard or voice, reorder, duplicate, delete. Every change saves immediately.

**Plan your days**
- A week strip on the home screen, plus a calendar from 2 weeks back to 10 weeks ahead.
- Pick any saved workout for any day, or start one straight from the library.
- Days are ticked automatically when a run is saved.
- A **Today** tile for the watch face carousel.

**Run**
- Runs in a foreground service with a wake lock and an Ongoing Activity chip, so it keeps going with the screen off.
- Records heart rate, pace, distance, GPS track, altitude, elevation gain, cadence and calories through Health Services.
- A progress ring around the screen edge shows how far you are through the current step, in that step's effort colour.
- Countdown digits roll like an odometer, and step names slide in when a step changes.
- **Pace tint:** the background glows green when you're on pace, blue when too fast and amber when too slow.
- Wrist cues, each with its own pattern (optional beeps in Settings):

  | Event | Buzz |
  |---|---|
  | Work step starts | 3 long |
  | Walk/recovery starts | 2 long |
  | 10 s or 100 m left | 2 short taps |
  | Too fast | 4 quick taps |
  | Too slow | 2 medium |
  | Workout complete | 1 very long |
- Swipe between three pages: the current step, totals with a live heart-rate trail, and controls (pause, skip, end).

**After the run**
- The distance counts up, and heart rate, pace and elevation graphs draw themselves in.
- Per-interval pace bars: green inside the target, blue faster, amber slower.
- History of all runs, with a heart-rate sparkline on each.
- Each interval is its own lap in the TCX file, so Strava, Runalyze and Garmin Connect show the structure.

## Install on the Galaxy Watch 4

1. **Turn on developer mode.** On the watch, go to Settings → About watch → Software info and tap **Software version** five times.
2. **Turn on Wi-Fi debugging.** In Settings → Developer options, turn on **ADB debugging** and **Wireless debugging** (called "Debug over Wi-Fi" on older software). Put the watch on the same Wi-Fi network as the PC.
3. **Pair and connect.** Tap **Pair new device** on the watch for a pairing code, then run:

   ```
   adb pair <watch-ip>:<pairing-port>
   adb connect <watch-ip>:<port>
   ```

   On older builds, skip pairing and run `adb connect <watch-ip>:5555`.
4. **Install.** Run this from the project folder:

   ```
   .\tools\install.ps1
   ```

   Or open the project in Android Studio and press Run.
5. **Grant permissions.** Open **Interval** on the watch and allow location, physical activity, body sensors and notifications.

## Getting runs off the watch

Every run is saved to `/sdcard/Android/data/com.om.interval/files/runs/` as four files:

| File | Contents |
|---|---|
| `<date>_<workout>_<HHmm>.tcx` | Laps, GPS, HR, cadence, altitude. Upload this one to Strava, Runalyze or Garmin Connect. |
| `.gpx` | GPS track with HR and cadence. |
| `.json` | Summary and graph data used by the app. |
| `.csv` | Raw sensor samples, written live during the run. |

With the watch connected over ADB, this copies them into `.\runs`:

```
.\tools\pull-runs.ps1
```

## Build and test

Gradle needs JDK 17+. Use Android Studio's bundled JDK:

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Unit tests cover:
- the interval engine (timed, distance and repeat-to-distance blocks; no drift when sensor data arrives late)
- pace targets and the pace-coach timing
- library JSON, scheduling and editing operations
- time and distance estimates
- TCX/GPX output and graph downsampling

To try it on an emulator, use a Wear OS AVD and turn on simulated running:

```
adb shell am broadcast -a whs.USE_SYNTHETIC_PROVIDERS com.google.android.wearable.healthservices
adb shell am broadcast -a whs.synthetic.user.START_RUNNING com.google.android.wearable.healthservices
```

## Code map

| Path | What |
|---|---|
| `workout/` | Workout model, library + schedule (JSON), edit operations, time estimates, templates |
| `engine/` | Pure-Kotlin interval engine and pace coach |
| `exercise/` | Health Services session, 1 s ticker, cues, laps, saving |
| `record/` | Track building, summaries, graph series, TCX/GPX writers |
| `data/` | Library, settings and run storage on the watch |
| `ui/` | Wear Compose screens, charts, pickers, motion |
| `tile/` | The Today tile |

## Known limits

- **Pace alerts can lag with the screen off.** Health Services sends sensor data in batches then, so an alert can arrive a few seconds late. Interval timing is not affected: it runs off the watch's own clock.
- **Distance-based steps can end a little late with the screen off.** They end on live distance, so the cue can come up to one batch late. Lap distances in the saved files are corrected from the timestamped distance stream.
- **A crash loses the run summary.** If the app is killed mid-run, the summary isn't written, but the raw `.csv` up to that point is kept.