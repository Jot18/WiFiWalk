package dev.wifiwalk.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.location.Location
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val bgColor = Color.rgb(11, 18, 32)
    private val surface = Color.rgb(20, 32, 50)
    private val border = Color.rgb(43, 61, 83)
    private val foreground = Color.rgb(238, 245, 252)
    private val muted = Color.rgb(155, 174, 197)
    private val teal = Color.rgb(45, 212, 191)
    private val red = Color.rgb(251, 113, 133)

    private lateinit var store: SurveyStore
    private lateinit var wifi: WifiReader
    private var surveys: MutableList<WifiSurvey> = mutableListOf()
    private var selectedId: String = ""
    private var activeTab = 0
    private var heatmap = true
    private var pendingPoint: Pair<Float, Float>? = null
    private var exportId: String? = null
    private var planImportId: String? = null
    private var saveEnabled = true
    private var satelliteLoading = false
    private var satelliteSpan = 140.0
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        store = SurveyStore(this)
        wifi = WifiReader(this)
        heatmap = getPreferences(MODE_PRIVATE).getBoolean("heatmap", true)
        try {
            surveys = store.load()
        } catch (err: IOException) {
            saveEnabled = false
            surveys = mutableListOf()
            AlertDialog.Builder(this)
                .setTitle("Cannot read saved surveys")
                .setMessage("The saved file could not be opened. WiFiWalk will not overwrite it. " +
                    "Please preserve the app's data before troubleshooting.\n\n${err.message}")
                .setPositiveButton("OK", null).show()
        }
        if (surveys.isEmpty() && saveEnabled) {
            surveys += WifiSurvey(name = "My first survey")
            persist()
        }
        val preferred = getPreferences(MODE_PRIVATE).getString("selected", "")
        selectedId = surveys.firstOrNull { it.id == preferred }?.id ?: surveys.firstOrNull()?.id.orEmpty()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        // Android 15+ enforces edge-to-edge for target SDK 35. Keep all controls clear
        // of the status and navigation areas while retaining compatibility with API 26.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        } else {
            root.fitsSystemWindows = true
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }
        scroll.addView(container)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
        // Reading Wi-Fi metadata requires runtime location permission on Android.
        // Ask when the app first opens rather than waiting for a map tap.
        if (!wifi.hasPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
        }
    }

    override fun onStart() {
        super.onStart()
        wifi.start()
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) render()
    }

    override fun onStop() {
        wifi.stop()
        super.onStop()
    }

    private fun activeSurvey(): WifiSurvey? = surveys.firstOrNull { it.id == selectedId }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 14, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun text(value: String, size: Float = 14f, color: Int = foreground, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            includeFontPadding = true
        }

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    private fun gap(height: Int = 12) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

    private fun panel(): LinearLayout = column().apply {
        background = shape(surface, 16, border)
        setPadding(dp(15), dp(15), dp(15), dp(15))
    }

    private fun button(label: String, filled: Boolean = false, onClick: () -> Unit): TextView = text(
        label, 13f, if (filled) bgColor else foreground, true
    ).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(11), dp(12), dp(11))
        background = shape(if (filled) teal else Color.rgb(30, 46, 68), 11,
            if (filled) null else border)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun addRowButtons(parent: LinearLayout, vararg buttons: TextView) {
        val r = row()
        buttons.forEachIndexed { index, b ->
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            if (index > 0) lp.leftMargin = dp(8)
            r.addView(b, lp)
        }
        parent.addView(r, LinearLayout.LayoutParams(-1, -2))
    }

    private fun persist() {
        if (!saveEnabled) return
        try {
            store.save(surveys)
        } catch (ex: IOException) {
            toast(ex.message ?: "Save failed")
        }
    }

    private fun render() {
        container.removeAllViews()
        val header = row()
        val brand = column()
        brand.addView(text("◉  WiFiWalk", 25f, teal, true))
        brand.addView(text("OFFLINE WI-FI SITE SURVEY", 10f, muted, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("＋  New") { promptNewSurvey() })
        container.addView(header)
        container.addView(gap(18))
        val survey = activeSurvey()
        val choose = panel()
        choose.addView(text("CURRENT SURVEY", 10f, muted, true))
        choose.addView(gap(5))
        val picker = row()
        picker.addView(text("${survey?.name ?: "No survey"}  ▾", 17f, foreground, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        picker.addView(button("Manage") { showManageDialog() })
        picker.setOnClickListener { showSurveyPicker() }
        picker.isClickable = true
        choose.addView(picker)
        container.addView(choose)
        container.addView(gap(12))
        val connection = wifi.read()
        val needsPermission = !wifi.hasPermission()
        val needsLocation = !locationServicesOn()
        val signal = panel()
        val label = row()
        label.addView(text("LIVE CONNECTION", 10f, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
        val state = when {
            needsPermission -> "● Permission needed"
            needsLocation -> "● Location is off"
            connection == null -> "● Not available"
            else -> "● Connected"
        }
        label.addView(text(state, 11f, if (connection == null) red else teal, true))
        signal.addView(label)
        signal.addView(gap(8))
        signal.addView(text(connection?.ssid ?: "Wi-Fi reading unavailable", 18f, foreground, true))
        val desc = when {
            needsPermission -> "Allow precise location access to read your Wi-Fi signal and access-point details. We do not collect GPS coordinates."
            needsLocation -> "Turn on the phone's Location switch; Android requires it for Wi-Fi information."
            connection == null -> "Check that Wi-Fi is connected, then refresh. If the problem persists, reopen WiFiWalk."
            else -> {
                val band = frequencyBand(connection.frequencyMhz)
                "${connection.rssi} dBm  ·  $band  ·  ${connection.frequencyMhz} MHz\nAP: ${connection.bssid}"
            }
        }
        signal.addView(text(desc, 12f, muted))
        signal.addView(gap(9))
        when {
            needsPermission -> signal.addView(button("Allow Wi-Fi access", true) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
            })
            needsLocation -> signal.addView(button("Open Location settings", true) {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            })
        }
        signal.addView(gap(7))
        signal.addView(button("↻  Refresh connection") { render() })
        container.addView(signal)
        container.addView(gap(14))

        val tabs = row()
        listOf("Map", "Readings", "Guide").forEachIndexed { i, title ->
            val b = button(title, filled = false) { activeTab = i; render() }
            b.background = shape(if (activeTab == i) Color.rgb(21, 76, 78) else surface, 12,
                if (activeTab == i) teal else border)
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            if (i > 0) lp.leftMargin = dp(8)
            tabs.addView(b, lp)
        }
        container.addView(tabs)
        container.addView(gap(14))
        when (activeTab) {
            0 -> renderMap(survey)
            1 -> renderReadings(survey)
            else -> renderGuide()
        }
        container.addView(gap(12))
        container.addView(text("No account  •  No AI  •  No cloud uploads", 11f, muted).apply {
            gravity = Gravity.CENTER
        })
    }

    private fun renderMap(survey: WifiSurvey?) {
        if (survey == null) return
        val stats = row()
        val readings = survey.samples
        val avg = if (readings.isEmpty()) "—" else "${readings.map { it.rssi }.average().toInt()}"
        val weakest = readings.minOfOrNull { it.rssi }?.toString() ?: "—"
        listOf(Triple("POINTS", "${readings.size}", teal),
            Triple("AVERAGE", "$avg dBm", foreground),
            Triple("WEAKEST", "$weakest dBm", red)).forEachIndexed { i, (lbl, valStr, color) ->
            val p = panel()
            p.addView(text(lbl, 10f, muted, true))
            p.addView(text(valStr, 16f, color, true))
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            if (i > 0) lp.leftMargin = dp(8)
            stats.addView(p, lp)
        }
        container.addView(stats)
        container.addView(gap(13))
        val actions = row()
        actions.addView(text("Coverage map", 18f, foreground, true), LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("Import plan") { openFloorPlanPicker(survey) })
        container.addView(actions)
        container.addView(gap(8))

        // Satellite imagery is an optional online background, not a live satellite feed.
        container.addView(button(if (satelliteLoading) "Loading USGS imagery…" else "🛰  Satellite — use my location", true) {
            if (!satelliteLoading) usePhonePositionForSatellite(survey)
        })
        container.addView(gap(7))
        addRowButtons(container,
            button("Enter coordinates") { promptSatelliteCoordinates(survey) },
            button("Area: ${satelliteSpan.toInt()} m  ▾") { chooseSatelliteArea() }
        )
        container.addView(gap(6))
        if (survey.satelliteLat != null && survey.satelliteLon != null) {
            container.addView(text("Satellite centered: ${"%.5f".format(Locale.US, survey.satelliteLat)}, " +
                "${"%.5f".format(Locale.US, survey.satelliteLon)}  •  Saved for offline viewing", 11f, muted))
            container.addView(gap(5))
        }
        container.addView(text("USGS aerial imagery needs internet to download. Imagery capture dates vary; " +
            "using my location sends the selected area to USGS. Your Wi-Fi samples remain on-device.", 11f, muted))
        container.addView(gap(8))
        val map = SurveyMapView(this)
        map.samples = readings.toList()
        map.heatmapVisible = heatmap
        map.plan = SurveyMapView.decodeScaled(store.floorPlanFile(survey)?.absolutePath)
        map.onMapTap = { x, y -> recordAt(x, y) }
        container.addView(map, LinearLayout.LayoutParams(-1, dp(350)))
        container.addView(gap(8))
        container.addView(text("Stand at the location you want to test, then tap that spot on the map. " +
            "The phone's GPS only centers the satellite image; it does not identify your exact indoor room.",
            12f, muted))
        container.addView(gap(10))
        val controls = row()
        controls.addView(text("Estimated heatmap", 13f, foreground), LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            isChecked = heatmap
            thumbTintList = android.content.res.ColorStateList.valueOf(teal)
            setOnCheckedChangeListener { _, checked ->
                heatmap = checked
                getPreferences(MODE_PRIVATE).edit().putBoolean("heatmap", heatmap).apply()
                map.heatmapVisible = checked
            }
        }
        controls.addView(toggle)
        container.addView(controls)
        container.addView(gap(8))
        val legend = text("● ≥−55 strong     ● ≥−67 good     ● ≥−75 fair     ● weaker", 11f, muted)
        container.addView(legend)
        container.addView(gap(12))
        addRowButtons(container,
            button("Undo last") { undoLast(survey) },
            button("Export CSV", true) { exportCsv(survey) }
        )
        container.addView(gap(8))
        container.addView(text("Heatmap is an estimate between readings, not a prediction of radio coverage. " +
            "Take more samples for a more useful map. Satellite: USGS The National Map / USDA NAIP.", 11f, muted))
    }

    private fun renderReadings(survey: WifiSurvey?) {
        if (survey == null) return
        container.addView(text("Recorded measurements", 18f, foreground, true))
        container.addView(gap(6))
        container.addView(text("Individual access points are identified by BSSID. Tap a reading to delete it.", 12f, muted))
        container.addView(gap(12))
        if (survey.samples.isEmpty()) {
            val empty = panel()
            empty.addView(text("No readings yet", 17f, foreground, true))
            empty.addView(text("Open Map and tap a position to record your signal.", 13f, muted))
            container.addView(empty)
        }
        survey.samples.asReversed().forEachIndexed { reverseIndex, sample ->
            val index = survey.samples.size - reverseIndex
            val card = panel()
            val top = row()
            top.addView(text("#${index}  ${sample.rssi} dBm", 17f,
                SurveyMapView.strengthColor(sample.rssi), true),
                LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(text(time(sample.timestamp), 12f, muted))
            card.addView(top)
            card.addView(gap(3))
            card.addView(text(sample.ssid, 14f, foreground, true))
            val coordinates = if (sample.latitude != null && sample.longitude != null) {
                "\nApprox. map coordinate: ${"%.6f".format(Locale.US, sample.latitude)}, " +
                    "${"%.6f".format(Locale.US, sample.longitude)}"
            } else ""
            card.addView(text("AP: ${sample.bssid}\n${frequencyBand(sample.frequencyMhz)}  ·  " +
                "${sample.frequencyMhz} MHz  ·  X: ${"%.2f".format(Locale.US, sample.x)}, " +
                "Y: ${"%.2f".format(Locale.US, sample.y)}" + coordinates, 11f, muted))
            card.isClickable = true
            card.setOnClickListener {
                AlertDialog.Builder(this).setTitle("Delete reading #$index?")
                    .setMessage("This removes the measurement from the survey map.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        survey.samples.removeAll { it.id == sample.id }
                        persist(); render()
                    }.show()
            }
            container.addView(card)
            container.addView(gap(8))
        }
        container.addView(button("Export all readings as CSV", true) { exportCsv(survey) })
    }

    private fun renderGuide() {
        val help = panel()
        help.addView(text("How to use WiFiWalk", 19f, foreground, true))
        help.addView(gap(10))
        listOf(
            "1. Connect your Android phone to the Wi-Fi network you want to test.",
            "2. Allow precise location access. Android also needs system Location switched on to expose network identifiers.",
            "3. Load an optional aerial map from USGS with Satellite, enter coordinates, or import your own floor plan.",
            "4. Physically stand at a location and tap that same location on the map.",
            "5. Wait a moment after moving so the current Wi-Fi reading settles; then collect another point.",
            "6. Repeat throughout the building. Green/teal indicates stronger Wi-Fi, pink indicates weaker.",
            "7. Review AP BSSIDs in Readings; export CSV for further analysis.",
            "8. Create a second survey after moving your routers, and compare exported measurements."
        ).forEach { help.addView(text(it, 13f, foreground)); help.addView(gap(10)) }
        container.addView(help)
        container.addView(gap(12))
        val notes = panel()
        notes.addView(text("Current limits — v0.1", 17f, foreground, true))
        notes.addView(gap(8))
        listOf(
            "• Connected Wi-Fi RSSI only, not a full scan of every nearby access point.",
            "• Location taps are manual; phones do not know their position inside your floor plan.",
            "• Wi-Fi RSSI is not download speed. Speed tests and automatic router-handoff logging are planned for a future release.",
            "• Coverage shading interpolates readings and hides areas too far from measured points.",
            "• Survey images remain in private app storage. Export CSV before uninstalling or clearing app data.",
            "• Satellite downloads require Internet and reveal the requested map area to USGS. No Wi-Fi survey readings are uploaded.",
            "• USGS / USDA aerial imagery capture dates vary. This is not live satellite imagery.",
            "• No background services are requested."
        ).forEach { notes.addView(text(it, 12f, muted)); notes.addView(gap(7)) }
        container.addView(notes)
        container.addView(gap(12))
        container.addView(button("Open Android Location Settings") {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        })
    }

    private fun locationServicesOn(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        return try {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
    }

    private fun recordAt(x: Float, y: Float) {
        if (!saveEnabled) { toast("Saved data is unavailable; sampling disabled"); return }
        if (!wifi.hasPermission()) {
            pendingPoint = x to y
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
            return
        }
        if (!locationServicesOn()) {
            AlertDialog.Builder(this)
                .setTitle("Turn on Location")
                .setMessage("Android restricts Wi-Fi identifiers when system Location is disabled. " +
                    "WiFiWalk does not request or store GPS coordinates.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }.show()
            return
        }
        val reading = wifi.read()
        if (reading == null) {
            AlertDialog.Builder(this)
                .setTitle("No valid Wi-Fi reading")
                .setMessage("Connect to Wi-Fi and try again. If you just granted permission, " +
                    "wait a moment and tap Refresh connection.")
                .setPositiveButton("OK", null).show()
            return
        }
        // Give users a chance to identify stale callback values on unusual Android builds.
        if (reading.ageMs > 15_000L) {
            AlertDialog.Builder(this).setTitle("Reading may be stale")
                .setMessage("The last Wi-Fi update was ${reading.ageMs / 1000} seconds ago. " +
                    "Refresh Wi-Fi or move slightly, then try again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save anyway") { _, _ -> saveReading(x, y, reading) }.show()
        } else saveReading(x, y, reading)
    }

    private fun saveReading(x: Float, y: Float, r: WifiReader.Reading) {
        val survey = activeSurvey() ?: return
        val coord = if (survey.satelliteLat != null && survey.satelliteLon != null &&
            survey.satelliteSpanMeters != null) {
            SatelliteImagery.coordinateAt(survey.satelliteLat!!, survey.satelliteLon!!,
                survey.satelliteSpanMeters!!, x, y)
        } else null
        survey.samples.add(SignalSample(timestamp = System.currentTimeMillis(),
            x = x, y = y, rssi = r.rssi, ssid = r.ssid, bssid = r.bssid,
            frequencyMhz = r.frequencyMhz, linkSpeedMbps = r.linkSpeedMbps,
            networkInfoAgeMs = r.ageMs, latitude = coord?.first, longitude = coord?.second))
        persist()
        render()
        toast("Saved: ${r.rssi} dBm")
    }

    private fun undoLast(survey: WifiSurvey) {
        if (survey.samples.isEmpty()) { toast("No readings to undo"); return }
        survey.samples.removeAt(survey.samples.lastIndex)
        persist(); render()
    }

    private fun promptNewSurvey() {
        if (!saveEnabled) { toast("Storage unavailable"); return }
        val input = EditText(this).apply {
            hint = "e.g. Office - before router move"
            setTextColor(this@MainActivity.foreground)
            setHintTextColor(muted)
            setSingleLine(true)
            setPadding(dp(15), dp(8), dp(15), dp(8))
        }
        AlertDialog.Builder(this).setTitle("New survey")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().take(65).ifBlank { "Untitled survey" }
                val survey = WifiSurvey(name = name)
                surveys.add(survey)
                select(survey)
                persist(); render()
            }.show()
    }

    private fun select(survey: WifiSurvey) {
        selectedId = survey.id
        getPreferences(MODE_PRIVATE).edit().putString("selected", selectedId).apply()
    }

    private fun showSurveyPicker() {
        if (surveys.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Choose survey")
            .setItems(surveys.map { "${it.name} (${it.samples.size} points)" }.toTypedArray()) { _, index ->
                select(surveys[index]); render()
            }.setNegativeButton("Close", null).show()
    }

    private fun showManageDialog() {
        val survey = activeSurvey() ?: return
        AlertDialog.Builder(this).setTitle("Manage: ${survey.name}")
            .setItems(arrayOf("Rename survey", "Delete survey")) { _, index ->
                if (index == 0) renameSurvey(survey) else deleteSurvey(survey)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun renameSurvey(survey: WifiSurvey) {
        val input = EditText(this).apply {
            setText(survey.name)
            setTextColor(this@MainActivity.foreground)
            setSingleLine(true)
            setPadding(dp(15), dp(8), dp(15), dp(8))
        }
        AlertDialog.Builder(this).setTitle("Rename survey")
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                survey.name = input.text.toString().trim().take(65).ifBlank { survey.name }
                persist(); render()
            }.show()
    }

    private fun deleteSurvey(survey: WifiSurvey) {
        AlertDialog.Builder(this).setTitle("Delete ${survey.name}?")
            .setMessage("This permanently deletes its readings and imported floor plan. Export data first if needed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                surveys.removeAll { it.id == survey.id }
                store.deleteSurvey(survey)
                if (surveys.isEmpty()) surveys.add(WifiSurvey(name = "My new survey"))
                select(surveys.first())
                persist(); render()
            }.show()
    }

    private fun openFloorPlanPicker(survey: WifiSurvey) {
        if (survey.samples.isNotEmpty()) {
            showError("Create a new survey", "Replacing the background would move existing readings. " +
                "Create a new survey first to preserve your data.")
            return
        }
        planImportId = survey.id
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }, REQUEST_IMAGE)
    }

    private fun exportCsv(survey: WifiSurvey) {
        exportId = survey.id
        val filename = survey.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(40) + ".csv"
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, filename)
        }, REQUEST_EXPORT)
    }

    @Deprecated("Activity result API retained to avoid third-party dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri: Uri = data.data ?: return
        when (requestCode) {
            REQUEST_IMAGE -> {
                val survey = surveys.firstOrNull { it.id == planImportId } ?: return
                try {
                    store.importFloorPlan(survey, uri)
                    persist()
                    toast("Floor plan imported")
                    render()
                } catch (e: Exception) { showError("Import failed", e.message) }
            }
            REQUEST_EXPORT -> {
                val survey = surveys.firstOrNull { it.id == exportId } ?: return
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(store.toCsv(survey).toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Unable to write the chosen document")
                    toast("CSV exported")
                } catch (e: Exception) { showError("Export failed", e.message) }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            val point = pendingPoint
            pendingPoint = null
            if (wifi.hasPermission()) {
                wifi.stop(); wifi.start()
                render()
                if (point != null) recordAt(point.first, point.second)
            } else {
                render()
                showError("Permission needed", "Android requires precise location permission to provide " +
                    "Wi-Fi SSID, BSSID and signal metadata. WiFiWalk does not read GPS coordinates.")
            }
        }
    }

    private fun showError(title: String, message: String?) = AlertDialog.Builder(this)
        .setTitle(title).setMessage(message ?: "An unexpected error occurred")
        .setPositiveButton("OK", null).show()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun time(timestamp: Long): String =
        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    private fun frequencyBand(mhz: Int): String = when (mhz) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> "Unknown band"
    }

    companion object {
        private const val REQUEST_IMAGE = 1101
        private const val REQUEST_EXPORT = 1102
        private const val REQUEST_LOCATION = 1103
    }
}
