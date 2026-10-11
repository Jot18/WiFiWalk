# WiFiWalk v0.3 — Android Wi-Fi survey and diagnostics

**New in v0.3:** connected Wi-Fi refresh now actively resamples (with sample timestamp), live foreground RSSI history graph, observed connected-access-point roaming events, manual nearby SSID and BSSID scan, basic visible-channel counts, an opt-in HTTP responsiveness test, US feet/meters units, and an optional higher-resolution imagery source.

## v0.3 features and important limits

- **Map:** optional public USGS/USDA orthoimagery, or optional **Esri World Imagery** for higher-detail aerial backgrounds where available. Choose from the app's Imagery menu. Esri use is governed by [Esri service terms](https://www.esri.com/en-us/legal/terms/web-site-service) and the optional Esri option is intended only for permissible **noncommercial** use; please verify the terms for your situation. Esri owns/licences its imagery—it is **not** public domain or covered by WiFiWalk's MIT source license. Visible in-app attribution is provided. Imagery is not live, capture dates are provider-dependent. No Google satellite images are extracted or scraped. Imported user maps remain supported.
- **Units:** switch between **feet (ft)** and **meters (m)**; the chosen unit affects map area width, not signal dBm or channel MHz.
- **Signal:** updates about every 1.5 seconds only while the app is visible. A manual Refresh button makes a fresh connection reading and shows its timestamp, even if RSSI is unchanged. Roaming entries record observed connected BSSID changes; background logging is not currently provided.
- **Scan:** tap **Scan nearby networks** to request a fresh Android Wi-Fi scan. Android may throttle requests and return results from previous scans; WiFiWalk displays approximate ages. Visible AP counts by channel are not true measurements of spectrum utilization or interference.
- **Tests:** explicit manual 3-request test to Google's HTTP 204 endpoint; measures end-to-end HTTP responsiveness, **not** ping, internet throughput or Wi-Fi PHY speed. No tests run automatically or in the background. The test sends standard requests to Google but does not send Wi-Fi survey data.
- **Privacy:** survey, Wi-Fi readings, BSSID roam history, and aerial image snapshots remain local. Optional imagery downloads send requested bounding boxes to imagery servers. Exact indoor phone location is not inferred from satellite imagery. Wi-Fi scanning requires Android location permission and system Location enabled. `CHANGE_WIFI_STATE` only permits user-initiated scans.
- **Storage:** app data stays in private Android storage. Export surveys before uninstalling—debug APKs can be signed with incompatible keys on different GitHub runners.

The [GitHub Actions page](https://github.com/Jot18/WiFiWalk/actions) has the installable `WiFiWalk-debug` artifact in successful **Android debug APK** workflows; unzip it to obtain `app-debug.apk`. This remains a test build and real-device behavior may differ by manufacturer.

---

# WiFiWalk — Android Wi-Fi coverage survey

Free open-source Android application for measuring the **currently connected Wi-Fi signal** and placing readings on a floor plan or a **real USGS aerial imagery background**. Built with Kotlin and native Android views. No AI API, registration, or paid map service.

## Features — v0.2

- Live connected Wi-Fi RSSI, SSID, BSSID, frequency and negotiated link speed when Android exposes them.
- Import a floor-plan image or choose **Satellite — use my location** to download a georeferenced aerial snapshot near you.
- Alternative **Enter coordinates** option when the phone's GPS center is not accurate enough.
- Select an aerial image area width: 60, 140, 300, or 600 meters.
- Tap the corresponding location on the displayed image to record RSSI measurements.
- Plot color-coded points, optional nearby-only interpolated signal heatmap, survey management, CSV export.
- Satellite-based samples include approximate latitude and longitude computed from the clicked map location.
- Saved satellite snapshot and Wi-Fi survey readings remain usable offline.
- No background location collection and no Wi-Fi survey uploads.

## Satellite imagery — important distinctions

Satellite mode uses **USGS The National Map / USDA NAIP** public aerial imagery through the USGSNAIPImagery ImageServer.

- **Not live:** imagery was captured in the past. Capture dates and available resolution vary geographically.
- **Internet is required to download or change the satellite image.** The selected location and map extent are sent to USGS to retrieve the imagery.
- Map selection is manual. The phone's position is used only to center an image; it cannot determine which room you are inside or where you tapped.
- Changing the center/scale after readings exist would invalidate old coordinates, so WiFiWalk blocks background replacement for surveys that already contain readings. Start a new survey to change the map.
- USGS aerial coverage is best within the United States. If the service is unavailable, import your own map image instead.
- Imagery acknowledgment: **USGS The National Map / USDA NAIP**. https://www.usgs.gov/ .

## Get an APK

Go to the GitHub [Actions](../../actions) page, open a **successful** Android debug APK workflow and download the `WiFiWalk-debug` artifact. Unzip it and install `app-debug.apk` on Android 8.0+.

These are **debug APKs**, intended for testing, not a signed production store release. GitHub-hosted runners may generate a different debug signing key for each build. If the next APK reports a signature conflict, **export readings before uninstalling** the existing app; uninstalling erases its private data. A stable release signing process is planned.

## Build from source

The GitHub Actions workflow installs Android SDK Platform 35 and runs Gradle 8.11.1 `:app:assembleDebug`. On your computer, use Android Studio with JDK 17, Android SDK 35, Android Gradle Plugin 8.9.2, and Kotlin 2.1.20. Android Studio can build the APK from the project.

## Use

1. Connect to the Wi-Fi network you want to test.
2. Enable system Location and grant **Precise location** to WiFiWalk. Android requires this for Wi-Fi SSID/BSSID metadata.
3. Open Map and load an aerial background near your current phone position, enter coordinates, or import a floor plan.
4. Physically stand at a sample location and tap the **same place on the displayed map**.
5. Repeat and view color-coded points. Export CSV to preserve your work.
6. Revisit areas after changing routers and compare the surveys.

## Scope and privacy

- The app currently samples **only the active connected Wi-Fi**, not all networks nearby. BSSID roam history, local throughput testing and continuous walk mode are future features.
- Android's connected-network info may be restricted or redacted; location services and permission must be enabled.
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_WIFI_STATE`, and `ACCESS_NETWORK_STATE` support network diagnostics; `INTERNET` is used **only for optional satellite image downloads**.
- Wi-Fi readings and imported backgrounds are saved in Android's private app storage. No telemetry or user account. No Wi-Fi readings are sent over the internet.
- Coverage interpolation is an estimate and does not predict actual throughput. Wi-Fi link speed is not measured throughput.

## Project files

- `MainActivity.kt` — dark theme UI, GPS permission and location selection, survey workflow
- `WifiReader.kt` — connected-network signal and identity
- `SurveyMapView.kt` — image background, measurement markers and interpolated heatmap
- `SatelliteImagery.kt` — USGS aerial image download and Web Mercator coordinate math
- `SurveyStore.kt` — local JSON persistence, offline image snapshots and CSV export
- `.github/workflows/android.yml` — repeatable debug APK compilation on GitHub

## License

MIT — see [LICENSE](LICENSE). Contributions and bug reports welcome.
