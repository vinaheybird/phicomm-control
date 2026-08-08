package com.phicomm.gemini.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * WiFi config qua Phicomm firmware API (port 8989).
 * curl -X POST --data '{"ssid":"...","secure":"WPA","password":"..."}' http://localhost:8989/api/configwifi
 */
@Suppress("DEPRECATION")
class WifiSetupHelper(private val context: Context) {

    companion object {
        private const val TAG = "geminiwifi"
        private const val API = "http://localhost:8989/api/configwifi"
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun connectToWifi(ssid: String, password: String, passwordType: String? = null): Pair<Boolean, String> {
        val secure = passwordType?.trim()?.uppercase() ?: if (password.isEmpty()) "OPEN" else "WPA"
        Log.d(TAG, "connectToWifi SSID='$ssid' secure=$secure")
        return try {
            val json = """{"ssid":"$ssid","secure":"$secure","password":"$password"}"""
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(json) }
            val code = conn.responseCode
            Log.d(TAG, "API response: $code")
            if (code in 200..299) Pair(true, "Loa đang kết nối vào '$ssid'...")
            else Pair(false, "Lỗi firmware (HTTP $code)")
        } catch (e: Throwable) {
            Log.e(TAG, "connectToWifi lỗi: ${e.message}")
            Pair(false, "Lỗi: ${e.message}")
        }
    }

    fun getCurrentSsid(): String = try {
        wifiManager.connectionInfo.ssid?.removeSurrounding("\"") ?: ""
    } catch (_: Exception) { "" }

    fun getCurrentIp(): String = try {
        val ip = wifiManager.connectionInfo.ipAddress
        if (ip != 0) "%d.%d.%d.%d".format(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
        else ""
    } catch (_: Exception) { "" }

    fun isConnectedToHomeWifi() = getCurrentIp().let { it.isNotEmpty() && !it.startsWith("192.168.43.") }
}
