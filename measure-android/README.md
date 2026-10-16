# Measure for Android

An Android version of Apple's **Measure** app, built with Google ARCore,
Kotlin and Jetpack Compose.

## Features

- **Line measuring**: aim the dot at a point, tap **+**, move to the second
  point and tap **+** again. The distance follows you live while you move.
- **Path / Area mode**: chain as many points as you like. Tap ✓ to finish
  an open path, or aim back at the first point to close the shape. You get
  each side's length, the perimeter and the **area**.
- **Snapping**: the reticle snaps to existing points (with a haptic tick), so
  you can chain measurements off each other. When you measure roughly
  straight up or down, a yellow guide locks the line to true vertical.
- **Surface detection**: floors, tables and walls are tracked (plus depth on
  phones that support the ARCore Depth API), and detected surfaces are shown
  as a faint overlay.
- **Tap any label** to see details, copy the numbers or delete that measurement.
- **Undo / Clear** controls, like on iPhone.
- **Photos**: the camera button saves a photo with the measurements drawn on
  it. Photos go to the in-app history (and to your gallery under
  *Pictures/Measure* on Android 10+). You can share them from the history.
- **Level**: a spirit level. Hold an edge against something for the horizon
  view, or lay the phone flat for the bubble view. It turns green at 0°, and
  you can tap to calibrate against a reference surface.
- Metric or imperial units (ft/in with ¼″ precision), haptics and other
  options in Settings.

## Requirements

- Android 7.0+ phone that supports
  [Google Play Services for AR](https://developers.google.com/ar/devices).
  The app offers to install or update it from the Play Store if needed.
- The Level tool works on any phone, even one without ARCore support.

## Installing

Every push builds an installable APK with GitHub Actions:

1. Open the repo's **Actions** tab → **Measure Android app** → the latest run.
2. Download the **Measure-apk** artifact (a zip) and unzip it on your phone.
3. Open `app-release.apk` and allow installing from unknown sources when asked.

The APK is signed with a debug key so you can side-load it. Before
publishing to the Play Store, set up a real signing key.

## Building locally

Open the `measure-android` folder in Android Studio, or run:

```bash
cd measure-android
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest    # unit tests for math and unit formatting
```

## How it works

| Piece | File |
| --- | --- |
| ARCore session lifecycle, install checks, HUD state | `ar/ArController.kt` |
| Camera feed rendering (OpenGL ES) and photo capture | `ar/ArRenderer.kt`, `ar/BackgroundRenderer.kt` |
| Hit testing, anchors, snapping, vertical guide, measurements | `ar/MeasureEngine.kt` |
| 3D→2D projection with near-plane clipping, area math | `ar/Geometry.kt` |
| Lines, labels, reticle and plane drawing (screen and photos) | `ar/OverlayRenderer.kt` |
| Compose UI: HUD, history, level | `ui/`, `level/` |

Every measurement point is an ARCore `Anchor`, so lines stay attached to
the real world and get more accurate as ARCore refines its map.
