package com.example.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface

data class HotspotRadioStatus(
    val localHotspotActive: Boolean = false,
    val activeSsid: String = LanProtocol.TARGET_SSID,
    val passphrase: String = "",
    val frequencyLabel: String = "5 GHz Ready",
    val linkSpeedMbps: Int = 0,
    val localIp: String = "127.0.0.1",
    val infoMessage: String = ""
)

class HotspotController(private val context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    @SuppressLint("MissingPermission")
    fun startLocalHotspot(
        onStarted: (HotspotRadioStatus) -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onError("Local-only hotspot API requires Android 8.0+. Using LAN Relay Server.")
            return
        }
        stopLocalHotspot()
        try {
            val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation?) {
                    reservation = res
                    val (ssid, pass, band) = extractReservationDetails(res)
                    val radio = readRadioTelemetry().copy(
                        localHotspotActive = true,
                        activeSsid = ssid,
                        passphrase = pass,
                        frequencyLabel = band,
                        infoMessage = "Hotspot radio active ($ssid)"
                    )
                    onStarted(radio)
                }

                override fun onStopped() {
                    reservation = null
                }

                override fun onFailed(reason: Int) {
                    reservation = null
                    val reasonText = when (reason) {
                        ERROR_NO_CHANNEL -> "No Wi-Fi channel available"
                        ERROR_TETHERING_DISALLOWED -> "Tethering disallowed by policy"
                        ERROR_INCOMPATIBLE_MODE -> "Turn off existing Wi-Fi tether or airplane mode"
                        else -> "Hotspot hardware unavailable (code $reason)"
                    }
                    onError(reasonText)
                }
            }

            wifiManager.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
        } catch (sec: SecurityException) {
            onError("Location / Nearby Wi-Fi permission required for OS hotspot (${sec.localizedMessage})")
        } catch (t: Throwable) {
            onError(t.localizedMessage ?: "Failed to start local hotspot")
        }
    }

    fun stopLocalHotspot() {
        runCatching { reservation?.close() }
        reservation = null
    }

    @Suppress("DEPRECATION")
    private fun extractReservationDetails(res: WifiManager.LocalOnlyHotspotReservation?): Triple<String, String, String> {
        if (res == null) return Triple(LanProtocol.TARGET_SSID, "", "5 GHz / LAN")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cfg = runCatching { res.softApConfiguration }.getOrNull()
            if (cfg != null) {
                val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    cfg.wifiSsid?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
                } else {
                    cfg.ssid?.takeIf { it.isNotBlank() }
                } ?: LanProtocol.TARGET_SSID
                val pass = cfg.passphrase ?: ""
                return Triple(ssid, pass, "Wi-Fi SoftAP")
            }
        }
        val legacyCfg = runCatching { res.wifiConfiguration }.getOrNull()
        val ssid = legacyCfg?.SSID?.trim('"')?.takeIf { it.isNotBlank() } ?: LanProtocol.TARGET_SSID
        val pass = legacyCfg?.preSharedKey ?: ""
        return Triple(ssid, pass, "Wi-Fi SoftAP")
    }

    fun registerAutoConnectSuggestion(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val openSuggestion = WifiNetworkSuggestion.Builder()
                .setSsid(LanProtocol.TARGET_SSID)
                .build()
            val status = wifiManager.addNetworkSuggestions(listOf(openSuggestion))
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
                status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    fun readRadioTelemetry(): HotspotRadioStatus {
        val ip = getLocalIpv4Address() ?: "127.0.0.1"
        var freqLabel = "LAN / Wi-Fi"
        var linkSpeed = 0

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val activeNet = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(activeNet)
                val wifiInfo = caps?.transportInfo as? WifiInfo
                if (wifiInfo != null) {
                    linkSpeed = wifiInfo.linkSpeed.coerceAtLeast(0)
                    freqLabel = formatFrequency(wifiInfo.frequency)
                }
            }
            if (linkSpeed == 0) {
                val info = wifiManager.connectionInfo
                if (info != null && info.frequency > 0) {
                    linkSpeed = info.linkSpeed.coerceAtLeast(0)
                    freqLabel = formatFrequency(info.frequency)
                }
            }
        }

        return HotspotRadioStatus(
            localHotspotActive = reservation != null,
            activeSsid = LanProtocol.TARGET_SSID,
            frequencyLabel = freqLabel,
            linkSpeedMbps = linkSpeed,
            localIp = ip
        )
    }

    private fun formatFrequency(freqMhz: Int): String = when {
        freqMhz >= 5925 -> "6 GHz (${freqMhz} MHz)"
        freqMhz >= 4900 -> "5 GHz (${freqMhz} MHz)"
        freqMhz >= 2400 -> "2.4 GHz (${freqMhz} MHz)"
        else -> "LAN Relay"
    }

    fun getLocalIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallback: String? = null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (intf.name.contains("wlan", ignoreCase = true) ||
                            intf.name.contains("ap", ignoreCase = true) ||
                            intf.name.contains("swlan", ignoreCase = true)
                        ) {
                            return host
                        }
                        if (fallback == null) fallback = host
                    }
                }
            }
            fallback
        }.getOrNull()
    }

    fun openSystemHotspotSettings() {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
