package dev.wifiwalk.app

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan

/**
 * A fixed georeferenced aerial snapshot from USGS The National Map, USGSNAIPImagery.
 * No API key or paid provider. Acquisitions vary by region and are NOT live imagery.
 * Fetching a new image requires Internet. Saved snapshots can be viewed offline.
 */
object SatelliteImagery {
    private const val RADIUS = 6378137.0
    private const val MAX_LAT = 85.05112878
    private const val SERVICE =
        "https://imagery.nationalmap.gov/arcgis/rest/services/USGSNAIPImagery/ImageServer/exportImage"

    private fun mercatorX(lon: Double): Double = Math.toRadians(lon) * RADIUS
    private fun mercatorY(lat: Double): Double {
        val v = lat.coerceIn(-MAX_LAT, MAX_LAT)
        return RADIUS * ln(tan(PI / 4.0 + Math.toRadians(v) / 2.0))
    }
    private fun latitudeFromY(y: Double): Double =
        Math.toDegrees(2.0 * atan(exp(y / RADIUS)) - PI / 2.0)

    /** Normalized coordinates follow the exactly-square downloaded snapshot. */
    fun coordinateAt(latitude: Double, longitude: Double, spanMeters: Double, x: Float, y: Float): Pair<Double, Double> {
        val mx = mercatorX(longitude) + (x - 0.5) * spanMeters
        val my = mercatorY(latitude) + (0.5 - y) * spanMeters
        return latitudeFromY(my) to Math.toDegrees(mx / RADIUS)
    }

    /** Network method: must be called from a background thread. */
    fun download(latitude: Double, longitude: Double, spanMeters: Double): ByteArray {
        require(latitude in -85.0..85.0 && longitude in -180.0..180.0)
        require(spanMeters in 40.0..2000.0)
        val x = mercatorX(longitude)
        val y = mercatorY(latitude)
        val half = spanMeters / 2.0
        val bbox = listOf(x - half, y - half, x + half, y + half).joinToString(",") {
            String.format(java.util.Locale.US, "%.3f", it)
        }
        val query = "$SERVICE?bbox=$bbox&bboxSR=3857&imageSR=3857&size=1024%2C1024&format=jpg&f=image"
        val connection = (URL(query).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "WiFiWalk/0.2 (open-source Android aerial survey)")
            setRequestProperty("Accept", "image/jpeg,image/*")
        }
        try {
            if (connection.responseCode != 200) {
                throw IllegalStateException("USGS imagery service returned HTTP ${connection.responseCode}")
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > 12 * 1024 * 1024) throw IllegalStateException("Satellite image too large")
                    output.write(buffer, 0, n)
                }
            }
            val bytes = output.toByteArray()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth < 200 || options.outHeight < 200) {
                throw IllegalStateException("USGS did not provide a usable aerial image for this location")
            }
            return bytes
        } finally {
            connection.disconnect()
        }
    }
}
