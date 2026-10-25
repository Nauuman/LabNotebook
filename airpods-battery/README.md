# PodPulse — AirPods battery on Android

A personal Android app that shows the battery of Apple AirPods on a Samsung
(or any Android 8+) phone, with a dark, animated UI and a resizable home
screen widget.

## How it works

AirPods constantly broadcast a small Bluetooth LE packet (Apple "proximity
pairing" message), which is the same data an iPhone uses for its battery
pop-up. PodPulse listens for that packet and decodes:

- left / right bud battery (in 10 % steps, which is all AirPods report)
- case battery
- charging state for each part
- in-ear detection
- the model (AirPods 1/2/3/4, Pro, Pro 2, Max)

No pairing hacks, no account, no internet: nothing leaves your phone.

- **Live mode:** while the app is open it scans at full speed.
- **Background mode:** a system-owned, hardware-filtered BLE scan keeps
  delivering AirPods packets to the app even when it's closed, so the widget
  stays current without a permanent notification. It is re-armed on reboot,
  when a Bluetooth device connects, and every 30 minutes by the widget.

## Install on your phone

### Option A: download the APK from GitHub Actions (no tools needed)
1. On GitHub open **Actions → PodPulse Android app**, pick the latest
   successful run, and download the **PodPulse-apk** artifact (a zip).
2. Unzip it and copy `app-debug.apk` to your phone (or download it on the
   phone directly).
3. Tap it, allow "Install unknown apps" for your browser / My Files when
   asked, and install.

Each CI build is signed with a fresh debug key, so to update you may need to
uninstall the old version first.

### Option B: build it yourself
Open the `airpods-battery` folder in Android Studio and press Run, or:

```bash
cd airpods-battery
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## First launch

1. Tap **Grant access** and allow *Nearby devices* and *Location → Precise*.
   Android hides BLE broadcasts from apps without these. The app never reads
   your location.
2. Make sure Location is switched on (quick settings tile).
3. Open your AirPods case next to the phone: the rings fill in.
4. Tap **Add widget**, or long-press the home screen → Widgets → PodPulse.

For a reliable widget on Samsung:
- Tap **Allow all the time** (Location) in the Background updates card.
- Tap **Don't optimise** to exempt the app from battery optimisation, and in
  *Settings → Battery → Background usage limits* make sure PodPulse isn't in
  "Sleeping apps" / "Deep sleeping apps".

## Notes and limits
- AirPods only broadcast when they're out of the closed case (or the lid is
  open), so with the lid shut the app shows the last reading and when it was
  seen.
- The app shows the strongest nearby AirPods. If someone sitting right next to
  you has AirPods too, readings may occasionally jump; weak signals (other
  people's AirPods further away) are ignored.
- Battery values come in 10 % steps; that's the resolution AirPods broadcast.

## Code map
| Path | What it does |
| --- | --- |
| `data/AirPodsParser.kt` | Decodes the BLE packet (unit-tested) |
| `data/PodRepository.kt` | Picks your AirPods, keeps/persists the latest reading |
| `ble/PodScanner.kt` | Live + background BLE scanning |
| `ble/ScanResultReceiver.kt`, `ble/RearmReceiver.kt` | Background delivery and re-arming |
| `widget/PodWidget.kt` | Home screen widget (Jetpack Glance) |
| `ui/` | Compose UI: animated battery rings, setup cards |
