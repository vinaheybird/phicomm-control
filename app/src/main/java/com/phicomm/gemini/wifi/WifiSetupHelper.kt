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
     * Kết nối loa vào mạng WiFi. Chạy trong Background Thread để không làm block HTTP server.
     */
    fun connectToWifi(ssid: String, password: String, passwordType: String? = null): Pair<Boolean, String> {
        val cleanSsid = ssid.trim()
        val cleanPass = password.trim()
        val type = passwordType?.trim()?.uppercase() ?: if (cleanPass.isEmpty()) "OPEN" else "WPA"

        Log.d(TAG, "========== BẮT ĐẦU KẾT NỐI WIFI ==========")
        Log.d(TAG, "SSID='$cleanSsid', Type='$type', PassLength=${cleanPass.length}")

        return try {
            // 1. Tắt điểm phát WiFi (SoftAP)
            Log.d(TAG, "Đang tắt chế độ phát WiFi (SoftAP)...")
            disableSoftAp()
            Thread.sleep(1500)

            // 2. Bật chế độ WiFi thu (Client Mode)
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "WiFi Client đang tắt. Đang bật...")
                wifiManager.isWifiEnabled = true
            }

            // Chờ tối đa 10 giây để WiFi thực sự bật (WIFI_STATE_ENABLED == 3)
            var waitCount = 0
            while (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED && waitCount < 10) {
                Log.d(TAG, "Đang chờ WiFi bật... Trạng thái hiện tại: ${wifiManager.wifiState}")
                Thread.sleep(1000)
                waitCount++
            }
            
            if (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED) {
                Log.e(TAG, "LỖI: Không thể bật WiFi Client Mode. Trạng thái cuối: ${wifiManager.wifiState}")
                return Pair(false, "Không thể bật WiFi trên loa.")
            }
            Log.d(TAG, "WiFi Client đã BẬT THÀNH CÔNG (Trạng thái: 3).")

            // 3. Tạo cấu hình mạng
            Log.d(TAG, "--- Đang cấu hình WifiConfiguration API ---")
            val conf = WifiConfiguration().apply {
                SSID = "\"$cleanSsid\""
                when (type) {
                    "WEP" -> {
                        wepKeys[0] = "\"$cleanPass\""
                        wepTxKeyIndex = 0
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                        allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                    }
                    "OPEN", "NONE" -> {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    }
                    else -> { // WPA/WPA2
                        preSharedKey = "\"$cleanPass\""
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    }
                }
                priority = 999
            }
            Log.d(TAG, "WifiConfiguration SSID: ${conf.SSID}, preSharedKey: ${conf.preSharedKey != null}")

            // 4. Xóa cấu hình mạng cũ nếu có
            try {
                wifiManager.configuredNetworks?.let { networks ->
                    networks.filter { it.SSID == "\"$cleanSsid\"" || it.SSID == cleanSsid }
                        .forEach { 
                            val removed = wifiManager.removeNetwork(it.networkId)
                            Log.d(TAG, "Đã xóa mạng cũ id=${it.networkId}, result=$removed")
                        }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi khi xóa mạng cũ", e)
            }

            // 5. Thêm mạng và kết nối
            val netId = wifiManager.addNetwork(conf)
            Log.d(TAG, "wifiManager.addNetwork() trả về netId=$netId")

            if (netId != -1) {
                val saveRes = wifiManager.saveConfiguration()
                Log.d(TAG, "wifiManager.saveConfiguration() trả về $saveRes")
                
                wifiManager.disconnect()
                
                val enableRes = wifiManager.enableNetwork(netId, true)
                Log.d(TAG, "wifiManager.enableNetwork($netId) trả về $enableRes")
                
                val recRes = wifiManager.reconnect()
                Log.d(TAG, "wifiManager.reconnect() trả về $recRes")
                
                Log.d(TAG, "========== KẾT THÚC LỆNH KẾT NỐI WIFI ==========")
                Pair(true, "Đã gửi lệnh kết nối vào '$cleanSsid'.")
            } else {
                Log.e(TAG, "addNetwork() THẤT BẠI (-1)")
                Log.d(TAG, "========== KẾT THÚC LỆNH KẾT NỐI WIFI ==========")
                Pair(false, "Không thể thêm mạng WiFi. Vui lòng kiểm tra lại.")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi hệ thống khi nối WiFi: ${e.message}", e)
            Pair(false, "Lỗi hệ thống khi nối WiFi: ${e.message}")
        }
    }

    /**
     * Tắt chế độ phát WiFi (SoftAP) thông qua Java Reflection.
     * Không cần quyền root (su).
     */
    private fun disableSoftAp() {
        try {
            val method = wifiManager.javaClass.getMethod("setWifiApEnabled", WifiConfiguration::class.java, java.lang.Boolean.TYPE)
            val result = method.invoke(wifiManager, null, false)
            Log.d(TAG, "setWifiApEnabled(false) trả về: $result")
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi khi tắt SoftAP qua Reflection: ${e.message}")
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
