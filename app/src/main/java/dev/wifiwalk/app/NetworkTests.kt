package dev.wifiwalk.app

import android.app.Activity
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Optional, explicit network diagnostic. No ongoing telemetry, no bandwidth download,
 * and not a throughput test. Makes three small HTTPS requests to google.com's 204 endpoint.
 */
object NetworkTests {
    data class Report(
        val attempts: Int,
        val successes: Int,
        val averageMs: Int?,
        val minimumMs: Int?,
        val maximumMs: Int?,
        val error: String?
    )

    fun measure(activity: Activity, callback: (Report) -> Unit) {
        Thread {
            val times = mutableListOf<Long>()
            var lastError: String? = null
            repeat(3) {
                val started = System.nanoTime()
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 4000
                        useCaches = false
                        setRequestProperty("Cache-Control", "no-cache")
                        setRequestProperty("User-Agent", "WiFiWalk-Android/0.3")
                    }
                    val code = connection.responseCode
                    if (code in 200..299) {
                        times.add((System.nanoTime() - started) / 1_000_000L)
                    } else {
                        lastError = "HTTP $code"
                    }
                } catch (ex: Exception) {
                    lastError = ex.javaClass.simpleName + ": " + (ex.message ?: "request failed")
                } finally {
                    connection?.disconnect()
                }
            }
            val result = Report(
                attempts = 3, successes = times.size,
                averageMs = if (times.isEmpty()) null else times.average().roundToInt(),
                minimumMs = times.minOrNull()?.toInt(),
                maximumMs = times.maxOrNull()?.toInt(),
                error = lastError
            )
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) callback(result)
            }
        }.start()
    }
}
