# WiFiWalk — native, offline Wi-Fi survey for Android

WiFiWalk is an open-source Android MVP for mapping **measured connected-network Wi-Fi signal strength** onto a floor plan. No AI APIs, accounts, telemetry, paid services, or remote servers.

## Features in v0.1.0

- Import a PNG/JPEG floor plan using Android's secure document picker (or use the built-in blank grid).
- Stand at a location, tap that same spot, and record the current connected Wi-Fi RSSI (dBm).
- Record SSID, BSSID, channel frequency, link speed and timestamps when Android exposes them.
- Save multiple surveys and switch between them.
- Display measured points on a map. Optional IDW heatmap, restricted to areas near measured points.
- View individual readings, delete readings, undo the latest sample, rename and delete surveys.
- Export CSV with normalized map coordinates, timestamps and network metadata.
- All surveys and imported images stay inside Android's private app storage. No `INTERNET` permission.

## Install / build

1. Install [Android Studio](https://developer.android.com/studio) and Android SDK Platform **35**.
2. Extract this project and choose **Open** in Android Studio; select its `WiFiWalk` root folder.
3. Allow Gradle to download the declared dependencies and create a Gradle wrapper if Android Studio asks. This archive contains `gradle-wrapper.properties` but not `gradle-wrapper.jar` (see below).
4. Plug in an Android 8.0+ phone with USB debugging enabled, select it in Android Studio, and choose **Run**.
5. Alternatively, use Android Studio **Build > Build APK(s)**. Output is typically `app/build/outputs/apk/debug/app-debug.apk`.

The app uses Android Gradle Plugin `8.9.2`, Kotlin Android plugin `2.1.20`, Gradle `8.11.1`, Java 17 and compileSdk 35. Android Studio can supply Gradle; alternatively generate a wrapper with a local Gradle 8.11.1 installation (`gradle wrapper --gradle-version 8.11.1`).

> This project was assembled without an Android SDK/Gradle build environment. The source is provided as an Android Studio project **but no compiled APK or device-test claim is made**.

## How to survey

1. Connect the phone to the Wi-Fi network of interest.
2. Enable Android **Location** and grant **Precise location** permission when asked. This is an Android requirement for Wi-Fi identity/scan information; WiFiWalk doesn't read GPS.
3. Open **Map**, import a floor-plan image if desired.
4. Stand in the actual room and tap your corresponding position in the floor plan.
5. Move to other positions and continue recording. Allow readings a moment to settle after walking.
6. Use **Readings** to inspect measurements and access point identities; export results as CSV.

The colored map is a **visualization of measurements**, not a calibrated propagation simulation. Strength thresholds are approximate and do not guarantee speed, latency or call quality. The heatmap does not claim knowledge far beyond measurement points.

## Scope limits, known issues

- **Only the Wi-Fi network currently connected to the device** is sampled. v0.1 does *not* scan all neighboring SSIDs or maintain a live roam log.
- Android 12+ exposes `WifiInfo` via `ConnectivityManager` callbacks with `FLAG_INCLUDE_LOCATION_INFO`. The app has a compatibility fallback to `WifiManager.getConnectionInfo()`. The latter is deprecated and may be redacted by some device builds.
- Location permission and enabled Android Location services are required for full Wi-Fi details. SSID/BSSID may legitimately appear as `Unknown` if Android restricts access.
- The app requests no `NEARBY_WIFI_DEVICES` permission because it doesn't use connection-management or active-scanning APIs.
- `link_speed_mbps` describes Wi-Fi negotiated link rate, **not measured throughput**.
- All placement is manual; no indoor positioning or device-to-floor-plan calibration.
- CSV export includes coordinates relative to the imported floor-plan image (0–1), not GPS coordinates.
- Wi-Fi callback values can occasionally be stale; the app warns when it knows a cached value is older than 15 seconds.
- Imported images are limited to 20 MB and decoded at reduced resolution for display.
- Survey data is kept only in the app's private storage; export before uninstall or clearing application data.

## Privacy

Permissions: `ACCESS_FINE_LOCATION`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`. No GPS readings are requested. Android may classify Wi-Fi metadata as location-sensitive, hence the first permission. There is no network upload code and no `INTERNET` permission in the manifest. All documents are chosen by the user via Storage Access Framework.

## Structure

- `app/src/main/java/dev/wifiwalk/app/MainActivity.kt` — dark native UI, survey workflows and document pickers.
- `app/src/main/java/dev/wifiwalk/app/WifiReader.kt` — connected Wi-Fi info with Android version compatibility.
- `app/src/main/java/dev/wifiwalk/app/SurveyMapView.kt` — native Canvas floor plan, measured points and IDW heatmap.
- `app/src/main/java/dev/wifiwalk/app/SurveyStore.kt` — AtomicFile JSON persistence, floor plans and CSV export.

## Roadmap

- [ ] Automated validation on an Android 16 device and on older supported API versions.
- [ ] Native Nearby AP scan screen with Android-permission and throttling-aware UX.
- [ ] BSSID roaming event timeline.
- [ ] Before/after heatmap comparisons aligned to the same floor plan.
- [ ] In-home throughput tests against an optional open-source local test server.
- [ ] JSON + floor plan archive backup/import.
- [ ] User-configurable RSSI bands, map calibration, and filter points by BSSID.
- [ ] Accessible map interactions including magnification and pan/zoom.
- [ ] Automated Kotlin tests and a GitHub Actions Android build.

## License

MIT — see [LICENSE](LICENSE). Contributions welcome.
