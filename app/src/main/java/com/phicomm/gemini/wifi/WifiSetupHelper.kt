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
        val type = passwordType?.trim()?.uppercase() ?: if (cleanPass.isEmpty()) "OPEN" else "WPA"

        Log.d(TAG, "========== BẮT ĐẦU KẾT NỐI WIFI ==========")
        Log.d(TAG, "SSID='$cleanSsid', Type='$type', PassLength=${cleanPass.length}")

        return try {
            // 1. TẮT SOFTAP (ĐIỂM PHÁT WIFI) BẰNG REFLECTION
            // LÝ DO BẮT BUỘC: Chip WiFi của Phicomm R1 (Android 5.1) KHÔNG THỂ vừa thu vừa phát cùng lúc.
            // Nếu không tắt SoftAP, wifiManager.addNetwork() sẽ bị hệ điều hành chặn cứng và trả về -1!
            try {
                Log.d(TAG, "Đang tắt SoftAP qua Reflection...")
                val method = wifiManager.javaClass.getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
                val result = method.invoke(wifiManager, null, false)
                Log.d(TAG, "Tắt SoftAP kết quả: $result")
                Thread.sleep(2000) // Đợi chip WiFi xả trạng thái AP hoàn toàn
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi khi tắt SoftAP: ${e.message}")
            }

            // 2. BẬT WIFI CLIENT
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "Đang bật WiFi Client...")
                wifiManager.isWifiEnabled = true
            }

            // Chờ WiFi Client khởi động hoàn toàn (WIFI_STATE_ENABLED == 3)
            var waitCount = 0
            while (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED && waitCount < 10) {
                Log.d(TAG, "Đang chờ WiFi Client bật... State=${wifiManager.wifiState}")
                Thread.sleep(1000)
                waitCount++
            }

            if (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED) {
                Log.e(TAG, "Không thể bật WiFi Client Mode. State cuối: ${wifiManager.wifiState}")
                return Pair(false, "Không thể bật WiFi trên loa.")
            }

            // 3. TẠO CẤU HÌNH (CHUẨN adb-join-wifi)
            val conf = WifiConfiguration()
            conf.SSID = "\"$cleanSsid\""

            if (type == "WEP") {
                conf.wepKeys[0] = "\"$cleanPass\""
                conf.wepTxKeyIndex = 0
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
            } else if (type == "WPA") {
                conf.preSharedKey = "\"$cleanPass\""
            } else if (type == "OPEN") {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }

            // 4. XÓA MẠNG CŨ & KẾT NỐI MẠNG MỚI
            try {
                wifiManager.configuredNetworks?.let { networks ->
                    networks.filter { it.SSID == "\"$cleanSsid\"" || it.SSID == cleanSsid }
                        .forEach { wifiManager.removeNetwork(it.networkId) }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi khi xóa mạng cũ: ${e.message}")
            }

            val netId = wifiManager.addNetwork(conf)
            Log.d(TAG, "addNetwork() trả về netId=$netId")

            if (netId != -1) {
                val saved = wifiManager.saveConfiguration()
                Log.d(TAG, "saveConfiguration() trả về: $saved")
                
                wifiManager.disconnect()
                val enableRes = wifiManager.enableNetwork(netId, true)
                Log.d(TAG, "enableNetwork() trả về: $enableRes")
                
                val recRes = wifiManager.reconnect()
                Log.d(TAG, "reconnect() trả về: $recRes")
                
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
