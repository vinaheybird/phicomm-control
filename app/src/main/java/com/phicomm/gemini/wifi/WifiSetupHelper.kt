package com.phicomm.gemini.wifi

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log

/**
 * WifiSetupHelper — kết nối loa vào WiFi nhà qua Android WifiManager API.
 * Không cần root. Tương thích Android 5.1 (API 21).
 */
@Suppress("DEPRECATION")
class WifiSetupHelper(context: Context) {

    companion object {
        private const val TAG = "WifiSetupHelper"
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Kết nối loa vào mạng WiFi với SSID và mật khẩu đã cho.
     * @return Pair(success, message)
     */
    fun connectToWifi(ssid: String, password: String): Pair<Boolean, String> {
        return try {
            // Bật WiFi nếu đang tắt
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
                Thread.sleep(2000)
            }

            // Tạo WifiConfiguration
            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                if (password.isNotEmpty()) {
                    preSharedKey = "\"$password\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                    allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                    allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
                priority = 999
            }

            // Xóa mạng cũ cùng SSID nếu tồn tại
            wifiManager.configuredNetworks
                ?.filter { it.SSID == "\"$ssid\"" }
                ?.forEach { wifiManager.removeNetwork(it.networkId) }

            // Thêm mạng mới
            val networkId = wifiManager.addNetwork(wifiConfig)
            if (networkId == -1) {
                Log.e(TAG, "addNetwork() thất bại cho SSID: $ssid")
                return Pair(false, "Không thể thêm mạng WiFi. Kiểm tra lại SSID và mật khẩu.")
            }

            wifiManager.saveConfiguration()
            wifiManager.disconnect()
            val enabled = wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()

            Log.d(TAG, "connectToWifi: SSID=$ssid, networkId=$networkId, enabled=$enabled")

            if (enabled) {
                Pair(true, "Đang kết nối vào '$ssid'... Vui lòng đợi 15-30 giây.")
            } else {
                Pair(false, "enableNetwork() thất bại. Kiểm tra lại mật khẩu WiFi.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "connectToWifi lỗi: ${e.message}", e)
            Pair(false, "Lỗi hệ thống: ${e.message}")
        }
    }

    /** Trả về IP hiện tại của wlan0 (dạng xxx.xxx.xxx.xxx). Rỗng nếu chưa có IP. */
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

    /** Trả về SSID đang kết nối. Rỗng nếu chưa kết nối. */
    fun getCurrentSsid(): String {
        return try {
            wifiManager.connectionInfo.ssid?.removeSurrounding("\"") ?: ""
        } catch (e: Exception) { "" }
    }

    /**
     * Kiểm tra loa đã kết nối WiFi nhà chưa.
     * Nếu IP vẫn là 192.168.43.x → đang ở chế độ AP (hotspot riêng).
     * Nếu IP là dải khác → đã kết nối WiFi nhà.
     */
    fun isConnectedToHomeWifi(): Boolean {
        val ip = getCurrentIp()
        return ip.isNotEmpty() && !ip.startsWith("192.168.43.")
    }
}
