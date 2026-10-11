package dev.wifiwalk.app

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/**
 * Downloads best available public-domain aerial imagery from USGS / USDA services.
 * These are aerial photographs from past acquisitions, NOT live imagery.
 * Saved JPEG snapshots and survey points work offline.
 */
object SatelliteImagery {
    private const val RADIUS = 6378137.0
    private const val MAX_LAT = 85.05112878

    // The USDA NAIP mosaic is a useful independent alternative if USGS times out.
    private const val USDA_NAIP =
        "https://apps.geo.fpac.usda.gov/geo-imagery/rest/services/naip/conus_naip/ImageServer/exportImage"
    private const val USGS_NAIP =
        "https://imagery.nationalmap.gov/arcgis/rest/services/USGSNAIPImagery/ImageServer/exportImage"
    private const val USGS_NAIP_PLUS =
        "https://imagery.nationalmap.gov/arcgis/rest/services/USGSNAIPPlus/ImageServer/exportImage"

    private fun mercatorX(lon: Double): Double = Math.toRadians(lon) * RADIUS
    private fun mercatorY(lat: Double): Double {
        val v = lat.coerceIn(-MAX_LAT, MAX_LAT)
        return RADIUS * ln(tan(PI / 4.0 + Math.toRadians(v) / 2.0))
    }
    private fun latitudeFromY(y: Double): Double =
        Math.toDegrees(2.0 * atan(exp(y / RADIUS)) - PI / 2.0)

    /** Georeference the point the user taps in a square exported image. */
    fun coordinateAt(latitude: Double, longitude: Double, spanMeters: Double, x: Float, y: Float): Pair<Double, Double> {
        val mx = mercatorX(longitude) + (x - 0.5) * spanMeters
        val my = mercatorY(latitude) + (0.5 - y) * spanMeters
        return latitudeFromY(my) to Math.toDegrees(mx / RADIUS)
    }

    private fun requestImage(service: String, bbox: String, imagePixels: Int): ByteArray {
        // High output resolution and high JPEG quality reduce avoidable resampling and artifacts.
        // They cannot invent detail beyond what the source aerial photograph captured.
        val url = "$service?bbox=$bbox&bboxSR=3857&imageSR=3857&size=$imagePixels%2C$imagePixels&format=jpg&compressionQuality=95&f=image"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 11000
            readTimeout = 17000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WiFiWalk/0.2.2 (Android; open-source Wi-Fi survey)")
            setRequestProperty("Accept", "image/jpeg,image/*")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            val imageBytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var count = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    count += n
                    if (count > 12L * 1024L * 1024L) throw IllegalStateException("Image exceeds 12 MB")
                    imageBytes.write(buffer, 0, n)
                }
            }
            val bytes = imageBytes.toByteArray()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth < 200 || options.outHeight < 200) {
                throw IllegalStateException("No valid image returned")
            }
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    /** Called on a worker thread; each fallback uses the same bounding box. */
    fun download(latitude: Double, longitude: Double, spanMeters: Double): ByteArray {
        require(latitude in -85.0..85.0 && longitude in -180.0..180.0)
        require(spanMeters in 40.0..2000.0)
        val x = mercatorX(longitude)
        val y = mercatorY(latitude)
        val half = spanMeters / 2.0
        val bbox = listOf(x - half, y - half, x + half, y + half).joinToString(",") {
            String.format(Locale.US, "%.3f", it)
        }
        // NAIP Plus can incorporate finer-resolution high-resolution orthoimagery.
        // Preferred source is not necessarily the most recently photographed at every location.
        val sources = listOf(
            "USGS NAIP Plus / HRO" to Pair(USGS_NAIP_PLUS, 1280),
            "USDA NAIP" to Pair(USDA_NAIP, 1280),
            "USGS NAIP" to Pair(USGS_NAIP, 1024)
        )
        val failures = mutableListOf<String>()
        for ((name, serviceAndSize) in sources) {
            try {
                return requestImage(serviceAndSize.first, bbox, serviceAndSize.second)
            } catch (error: Exception) {
                // A timeout or 504 on one server should not block a different public service.
                failures += "$name: ${error.message ?: "Unavailable"}"
            }
        }
        throw IllegalStateException(
            "All public imagery sources were unavailable. " + failures.joinToString("; ") +
                ". Try again later or use Import plan with a saved aerial screenshot."
        )
    }
}
