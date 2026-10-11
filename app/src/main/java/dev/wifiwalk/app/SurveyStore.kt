package dev.wifiwalk.app

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

data class SignalSample(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val rssi: Int,
    val ssid: String,
    val bssid: String,
    val frequencyMhz: Int,
    val linkSpeedMbps: Int,
    val networkInfoAgeMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class WifiSurvey(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val created: Long = System.currentTimeMillis(),
    var planFile: String? = null,
    var satelliteLat: Double? = null,
    var satelliteLon: Double? = null,
    var satelliteSpanMeters: Double? = null,
    var satelliteProvider: String = "usgs",
    val samples: MutableList<SignalSample> = mutableListOf()
)

/** All survey and image data lives in private app storage. No network permissions are needed. */
class SurveyStore(private val context: Context) {
    private val dataFile = AtomicFile(File(context.filesDir, "wifiwalk-surveys.json"))
    private val plansDir = File(context.filesDir, "plans").apply { mkdirs() }

    fun load(): MutableList<WifiSurvey> {
        if (!dataFile.baseFile.exists()) return mutableListOf()
        return try {
            val json = JSONObject(dataFile.openRead().bufferedReader().use { it.readText() })
            val surveysJson = json.optJSONArray("surveys") ?: JSONArray()
            val surveys = mutableListOf<WifiSurvey>()
            for (i in 0 until surveysJson.length()) {
                val obj = surveysJson.getJSONObject(i)
                val samples = mutableListOf<SignalSample>()
                val sampleArray = obj.optJSONArray("samples") ?: JSONArray()
                for (j in 0 until sampleArray.length()) {
                    val sample = sampleArray.getJSONObject(j)
                    samples += SignalSample(
                        id = sample.optString("id", UUID.randomUUID().toString()),
                        timestamp = sample.optLong("timestamp"),
                        x = sample.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                        y = sample.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                        rssi = sample.optInt("rssi", -127),
                        ssid = sample.optString("ssid", "Unknown"),
                        bssid = sample.optString("bssid", "Unknown"),
                        frequencyMhz = sample.optInt("frequencyMhz"),
                        linkSpeedMbps = sample.optInt("linkSpeedMbps", -1),
                        networkInfoAgeMs = sample.optLong("networkInfoAgeMs", -1L),
                        latitude = if (sample.has("latitude") && !sample.isNull("latitude")) sample.optDouble("latitude") else null,
                        longitude = if (sample.has("longitude") && !sample.isNull("longitude")) sample.optDouble("longitude") else null
                    )
                }
                surveys += WifiSurvey(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Untitled survey"),
                    created = obj.optLong("created"),
                    planFile = obj.optString("planFile").takeIf { it.isNotBlank() },
                    satelliteLat = if (obj.has("satelliteLat") && !obj.isNull("satelliteLat")) obj.optDouble("satelliteLat") else null,
                    satelliteLon = if (obj.has("satelliteLon") && !obj.isNull("satelliteLon")) obj.optDouble("satelliteLon") else null,
                    satelliteSpanMeters = if (obj.has("satelliteSpanMeters") && !obj.isNull("satelliteSpanMeters")) obj.optDouble("satelliteSpanMeters") else null,
                    satelliteProvider = obj.optString("satelliteProvider", "usgs"),
                    samples = samples
                )
            }
            surveys
        } catch (_: Exception) {
            // Preserve the possibly damaged source; never overwrite it with an empty dataset.
            throw IOException("Unable to open saved surveys. Your data has not been changed.")
        }
    }

    fun save(surveys: List<WifiSurvey>) {
        val root = JSONObject().put("schemaVersion", 2)
        val arr = JSONArray()
        surveys.forEach { survey ->
            val sampleArray = JSONArray()
            survey.samples.forEach { s ->
                sampleArray.put(JSONObject()
                    .put("id", s.id)
                    .put("timestamp", s.timestamp)
                    .put("x", s.x.toDouble())
                    .put("y", s.y.toDouble())
                    .put("rssi", s.rssi)
                    .put("ssid", s.ssid)
                    .put("bssid", s.bssid)
                    .put("frequencyMhz", s.frequencyMhz)
                    .put("linkSpeedMbps", s.linkSpeedMbps)
                    .put("networkInfoAgeMs", s.networkInfoAgeMs)
                    .put("latitude", s.latitude)
                    .put("longitude", s.longitude))
            }
            arr.put(JSONObject()
                .put("id", survey.id)
                .put("name", survey.name)
                .put("created", survey.created)
                .put("planFile", survey.planFile ?: "")
                .put("satelliteLat", survey.satelliteLat)
                .put("satelliteLon", survey.satelliteLon)
                .put("satelliteSpanMeters", survey.satelliteSpanMeters)
                .put("satelliteProvider", survey.satelliteProvider)
                .put("samples", sampleArray))
        }
        root.put("surveys", arr)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = dataFile.startWrite()
            stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
            dataFile.finishWrite(stream)
        } catch (ex: Exception) {
            if (stream != null) dataFile.failWrite(stream)
            throw IOException("Failed to save surveys: ${ex.message}", ex)
        }
    }

    fun importFloorPlan(survey: WifiSurvey, uri: Uri) {
        val file = File(plansDir, "${survey.id}.plan")
        val temp = File(plansDir, "${survey.id}.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var count = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > 20L * 1024L * 1024L) {
                            throw IOException("Floor plan must be smaller than 20 MB")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("Cannot read the selected image")
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(temp.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) throw IOException("Unsupported or damaged image")
            if (file.exists() && !file.delete()) throw IOException("Cannot replace previous floor plan")
            if (!temp.renameTo(file)) throw IOException("Unable to save imported image")
            survey.planFile = file.name
            survey.satelliteLat = null
            survey.satelliteLon = null
            survey.satelliteSpanMeters = null
            survey.satelliteProvider = "usgs"
        } finally {
            temp.delete()
        }
    }

    /** Write a verified JPEG snapshot atomically; metadata is saved by the activity. */
    fun importSatellite(survey: WifiSurvey, bytes: ByteArray, lat: Double, lon: Double,
                        spanMeters: Double, provider: String = "usgs") {
        if (survey.samples.isNotEmpty()) {
            // Allow replacing blurry imagery while keeping the *same* map extent.
            // Otherwise existing normalized map sample positions would be shifted.
            val sameExtent = survey.satelliteLat == lat &&
                survey.satelliteLon == lon && survey.satelliteSpanMeters == spanMeters
            if (!sameExtent) throw IOException("Create a new survey before moving the map background")
        }
        val file = File(plansDir, "${survey.id}.plan")
        val temp = File(plansDir, "${survey.id}.tmp")
        try {
            temp.writeBytes(bytes)
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(temp.absolutePath, opts)
            if (opts.outWidth < 200 || opts.outHeight < 200) throw IOException("Invalid satellite image")
            if (file.exists() && !file.delete()) throw IOException("Cannot replace previous map")
            if (!temp.renameTo(file)) throw IOException("Cannot save satellite map")
            survey.planFile = file.name
            survey.satelliteLat = lat
            survey.satelliteLon = lon
            survey.satelliteSpanMeters = spanMeters
            survey.satelliteProvider = provider
        } finally {
            temp.delete()
        }
    }

    fun floorPlanFile(survey: WifiSurvey): File? {
        val name = survey.planFile ?: return null
        if (name != "${survey.id}.plan") return null
        return File(plansDir, name).takeIf { it.exists() }
    }

    fun deleteSurvey(survey: WifiSurvey) {
        floorPlanFile(survey)?.delete()
    }

    fun toCsv(survey: WifiSurvey): String {
        val csv = StringBuilder("\uFEFF")
        csv.append("survey,sample_id,time_utc,x_normalized,y_normalized,rssi_dbm,ssid,bssid,frequency_mhz,band,link_speed_mbps,metadata_age_ms,latitude,longitude\r\n")
        survey.samples.forEach { s ->
            val date = java.time.Instant.ofEpochMilli(s.timestamp).toString()
            val band = when (s.frequencyMhz) {
                in 2400..2500 -> "2.4 GHz"
                in 4900..5900 -> "5 GHz"
                in 5925..7125 -> "6 GHz"
                else -> "Unknown"
            }
            val values = listOf(
                survey.name, s.id, date, "%.5f".format(java.util.Locale.US, s.x),
                "%.5f".format(java.util.Locale.US, s.y), s.rssi.toString(),
                s.ssid, s.bssid, s.frequencyMhz.toString(), band,
                s.linkSpeedMbps.takeIf { it >= 0 }?.toString() ?: "",
                s.networkInfoAgeMs.takeIf { it >= 0 }?.toString() ?: "",
                s.latitude?.let { "%.7f".format(java.util.Locale.US, it) } ?: "",
                s.longitude?.let { "%.7f".format(java.util.Locale.US, it) } ?: ""
            )
            csv.append(values.joinToString(",") { value ->
                "\"${value.replace("\"", "\"\"")}\""
            }).append("\r\n")
        }
        return csv.toString()
    }
}
