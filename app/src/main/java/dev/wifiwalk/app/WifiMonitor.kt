package dev.wifiwalk.app

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Polls the *connected* Wi-Fi while WiFiWalk is visible.
 * No background service, cloud traffic, or active nearby-network scan.
 */
class WifiMonitor(private val reader: WifiReader) {
    data class Point(val time: Long, val rssi: Int)
    data class Roam(val at: Long, val from: String, val to: String, val ssid: String)

    val points: MutableList<Point> = mutableListOf()
    val roams: MutableList<Roam> = mutableListOf()
    var latest: WifiReader.Reading? = null
        private set
    var sampledAt: Long = 0L
        private set
    var lastResult: String = "Waiting for sample"
        private set
    var onUpdate: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastBssid: String? = null
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            sample(true)
            handler.postDelayed(this, 1500L)
        }
    }

    fun start() {
        if (running) return
        running = true
        ticker.run()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(ticker)
    }

    /** Explicitly resample immediately; returns a status even if RSSI has not changed. */
    fun sample(forceFresh: Boolean): WifiReader.Reading? {
        val now = System.currentTimeMillis()
        val value = reader.read(forceFresh = forceFresh)
        latest = value
        sampledAt = now
        if (value == null) {
            lastResult = "No connected Wi-Fi reading"
            lastBssid = null
        } else {
            lastResult = "Sampled " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
            points.add(Point(now, value.rssi))
            while (points.size > 120) points.removeAt(0)
            val bssid = value.bssid.takeUnless { it.equals("Unknown", true) }
            val previous = lastBssid
            if (previous != null && bssid != null && bssid != previous) {
                roams.add(0, Roam(now, previous, bssid, value.ssid))
                while (roams.size > 40) roams.removeAt(roams.lastIndex)
            }
            if (bssid != null) lastBssid = bssid
        }
        onUpdate?.invoke()
        return value
    }
}
