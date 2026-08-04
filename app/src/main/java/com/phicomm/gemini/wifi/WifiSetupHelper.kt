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
     * Kết nối loa vào mạng WiFi chuẩn 100% steinwurf/adb-join-wifi
     */
    fun connectToWifi(ssid: String, password: String, passwordType: String? = null): Pair<Boolean, String> {
        val cleanSsid = ssid.trim()
        val cleanPass = password.trim()
        val type = passwordType?.trim()?.uppercase() ?: if (cleanPass.isEmpty()) "OPEN" else "WPA"

        Log.d(TAG, "========== BẮT ĐẦU KẾT NỐI WIFI ==========")
        Log.d(TAG, "SSID='$cleanSsid', Type='$type', PassLength=${cleanPass.length}")

        return try {
            // Chuẩn steinwurf/adb-join-wifi: Bật wifi
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
            }

            // Chuẩn steinwurf/adb-join-wifi: Tạo cấu hình
            val conf = WifiConfiguration()
            conf.SSID = "\"$cleanSsid\""

            if (type == "WEP") {
                conf.wepKeys[0] = "\"$cleanPass\""
                conf.wepTxKeyIndex = 0
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
            } else if (type == "WPA") {
                // CHỈ SET preSharedKey, KHÔNG SET allowedKeyManagement cho WPA!
                conf.preSharedKey = "\"$cleanPass\""
            } else if (type == "OPEN") {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }

            // Chuẩn steinwurf/adb-join-wifi: Thực thi kết nối
            val netId = wifiManager.addNetwork(conf)
            Log.d(TAG, "addNetwork() trả về netId=$netId")

            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
                
                Log.d(TAG, "Đã gửi lệnh enableNetwork và reconnect thành công.")
                Pair(true, "Đã gửi lệnh kết nối vào '$cleanSsid'.")
            } else {
                Log.e(TAG, "addNetwork() THẤT BẠI (-1)")
                Pair(false, "Không thể thêm mạng WiFi (netId = -1). Vui lòng kiểm tra lại.")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi hệ thống khi nối WiFi: ${e.message}", e)
            Pair(false, "Lỗi hệ thống khi nối WiFi: ${e.message}")
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
