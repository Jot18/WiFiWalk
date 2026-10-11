package dev.wifiwalk.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock

/** Read-only observation of the CURRENTLY CONNECTED Wi-Fi. No active AP scanning. */
class WifiReader(private val context: Context) {
    data class Reading(
        val rssi: Int,
        val ssid: String,
        val bssid: String,
        val frequencyMhz: Int,
        val linkSpeedMbps: Int,
        val ageMs: Long
    )

    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    @Volatile private var cachedInfo: WifiInfo? = null
    @Volatile private var cachedAtElapsed: Long = 0L
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            val listener = object : ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
            ) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val info = capabilities.transportInfo as? WifiInfo ?: return
                    cachedInfo = info
                    cachedAtElapsed = SystemClock.elapsedRealtime()
                }
                override fun onLost(network: Network) {
                    cachedInfo = null
                    cachedAtElapsed = 0L
                }
            }
            connectivity.registerNetworkCallback(request, listener)
            callback = listener
        } catch (_: Exception) {
            // Some vendor builds restrict callbacks. Fallback remains available.
        }
    }

    fun stop() {
        callback?.let { try { connectivity.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        callback = null
        cachedInfo = null
    }

    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun read(): Reading? {
        if (!hasPermission() || !wifiManager.isWifiEnabled) return null
        val wifiNetworkAvailable = try {
            connectivity.allNetworks.any { network ->
                connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (_: Exception) { false }
        if (!wifiNetworkAvailable) return null

        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cachedInfo ?: tryGetLegacyInfo()
        } else {
            tryGetLegacyInfo()
        } ?: return null

        val rssi = info.rssi
        if (rssi !in -126..-10) return null
        val ssid = info.ssid?.trim('"')?.takeUnless {
            it.isBlank() || it.equals("<unknown ssid>", ignoreCase = true)
        } ?: "Unknown"
        val bssid = info.bssid?.takeUnless { it.isBlank() || it == "02:00:00:00:00:00" }
            ?: "Unknown"
        val age = if (cachedInfo === info && cachedAtElapsed > 0L) {
            (SystemClock.elapsedRealtime() - cachedAtElapsed).coerceAtLeast(0L)
        } else -1L
        return Reading(rssi, ssid, bssid, info.frequency, info.linkSpeed, age)
    }

    @Suppress("DEPRECATION")
    private fun tryGetLegacyInfo(): WifiInfo? = try { wifiManager.connectionInfo } catch (_: Exception) { null }
}
