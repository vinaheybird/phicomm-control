package com.phicomm.gemini.wifi

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log
import java.io.DataOutputStream

/**
 * WifiSetupHelper — Cấu hình Wi-Fi chuẩn 100% steinwurf/adb-join-wifi cho Android 5.1.
 */
@Suppress("DEPRECATION")
class WifiSetupHelper(private val context: Context) {

    companion object {
        private const val TAG = "geminiwifi"
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Kết nối loa vào mạng WiFi.
     */
    fun connectToWifi(ssid: String, password: String, passwordType: String? = null): Pair<Boolean, String> {
        val cleanSsid = ssid.trim()
        val cleanPass = password.trim()

        Log.d(TAG, "========== BẮT ĐẦU KẾT NỐI WIFI (DAEMON MODE) ==========")
        Log.d(TAG, "SSID='$cleanSsid', PassLength=${cleanPass.length}")

        return try {
            val file = java.io.File("/data/local/tmp/wifi.txt")
            file.writeText("$cleanSsid\n$cleanPass")
            
            // Cấp quyền để root script có thể đọc và xóa file
            Runtime.getRuntime().exec("chmod 666 /data/local/tmp/wifi.txt")
            
            Log.d(TAG, "Đã ghi file wifi.txt thành công cho Root Daemon xử lý!")
            Pair(true, "Đã gửi lệnh kết nối vào '$cleanSsid'. Vui lòng đợi 15-30 giây để loa tự ngắt SoftAP và kết nối mạng.")
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi khi ghi file cấu hình: ${e.message}", e)
            Pair(false, "Lỗi hệ thống khi ghi cấu hình: ${e.message}")
        }
    }

    fun getCurrentSsid(): String {
        return try {
            wifiManager.connectionInfo.ssid?.removeSurrounding("\"") ?: ""
        } catch (e: Exception) { "" }
    }

    fun getCurrentIp(): String {
        return try {
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt != 0) {
                String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            } else ""
        } catch (e: Exception) { "" }
    }

    fun isConnectedToHomeWifi(): Boolean {
        val ip = getCurrentIp()
        return ip.isNotEmpty() && !ip.startsWith("192.168.43.")
    }
}
