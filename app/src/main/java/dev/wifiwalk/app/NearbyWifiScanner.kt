package dev.wifiwalk.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * User-triggered Android nearby Wi-Fi scans. The OS throttles scan frequency.
 * ScanResults can be cached; timestamps are exposed to avoid misleading users.
 */
class NearbyWifiScanner(private val context: Context) {
    data class Nearby(
        val ssid: String, val bssid: String, val rssi: Int, val mhz: Int,
        val channel: Int, val band: String, val ageSeconds: Long, val security: String
    )
    val results: MutableList<Nearby> = mutableListOf()
    var status: String = "Tap Scan to request nearby networks"
        private set
    private var lastRequestAt = 0L
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val handler = Handler(Looper.getMainLooper())

    fun refresh(callback: () -> Unit) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status = "Allow precise location for nearby Wi-Fi scan results."
            callback()
            return
        }
        val elapsed = SystemClock.elapsedRealtime()
        if (lastRequestAt > 0 && elapsed - lastRequestAt < 30_000L) {
            loadCached()
            status = "Android rate limit: showing available cached results. Wait before scanning again."
            callback()
            return
        }
        lastRequestAt = elapsed
        status = "Requesting Wi-Fi scan…"
        callback()
        @Suppress("DEPRECATION")
        val accepted = try { wifi.startScan() } catch (_: Exception) { false }
        handler.postDelayed({
            loadCached()
            status = if (accepted) "Latest Android scan results; individual readings may be cached."
                else "Android declined the scan (throttling or permission). Showing cached results."
            callback()
        }, 2800L)
    }

    @Suppress("DEPRECATION")
    fun loadCached() {
        val scans = try { wifi.scanResults } catch (_: Exception) { emptyList<ScanResult>() }
        val elapsedUs = SystemClock.elapsedRealtimeNanos() / 1000L
        results.clear()
        results.addAll(scans.map { item ->
            val freq = item.frequency
            val band = when (freq) {
                in 2400..2500 -> "2.4 GHz"
                in 4900..5900 -> "5 GHz"
                in 5925..7125 -> "6 GHz"
                else -> "Other"
            }
            val channel = when (band) {
                "2.4 GHz" -> if (freq == 2484) 14 else (freq - 2407) / 5
                "5 GHz" -> (freq - 5000) / 5
                "6 GHz" -> (freq - 5950) / 5
                else -> 0
            }
            val security = when {
                item.capabilities.contains("SAE", true) -> "WPA3"
                item.capabilities.contains("WPA2", true) -> "WPA2"
                item.capabilities.contains("WPA", true) -> "WPA"
                item.capabilities.contains("WEP", true) -> "WEP"
                item.capabilities.contains("OWE", true) -> "Enhanced open"
                else -> "Open / unknown"
            }
            Nearby(
                ssid = item.SSID.ifBlank { "(hidden)" },
                bssid = item.BSSID ?: "Unknown",
                rssi = item.level,
                mhz = freq,
                channel = channel,
                band = band,
                ageSeconds = ((elapsedUs - item.timestamp).coerceAtLeast(0L) / 1_000_000L),
                security = security
            )
        }.distinctBy { it.bssid }.sortedByDescending { it.rssi })
    }
}
