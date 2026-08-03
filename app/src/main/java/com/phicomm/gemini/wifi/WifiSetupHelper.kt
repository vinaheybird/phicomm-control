package com.phicomm.gemini.wifi

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log
import java.io.DataOutputStream

/**
 * WifiSetupHelper — kết nối loa vào WiFi nhà qua Android WifiManager API (tương tự adb-join-wifi).
 * Hỗ trợ WPA/WPA2/WEP/Open, kèm cơ chế Root Fallback nếu API Android bị hạn chế.
 * Tương thích Android 5.1 (API 21/22).
 */
@Suppress("DEPRECATION")
class WifiSetupHelper(private val context: Context) {

    companion object {
        private const val TAG = "WifiSetupHelper"
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Kết nối loa vào mạng WiFi với SSID, mật khẩu và kiểu bảo mật.
     * @param ssid Tên SSID
     * @param password Mật khẩu WiFi (có thể rỗng nếu mạng OPEN)
     * @param passwordType Kiểu bảo mật: "WPA", "WEP", "NONE" hoặc null (mặc định tự nhận diện)
     * @return Pair(success, message)
     */
    fun connectToWifi(ssid: String, password: String, passwordType: String? = null): Pair<Boolean, String> {
        val cleanSsid = ssid.trim()
        val cleanPass = password.trim()
        val type = passwordType?.trim()?.uppercase() ?: if (cleanPass.isEmpty()) "NONE" else "WPA"

        Log.d(TAG, "Chuẩn bị kết nối WiFi: SSID='$cleanSsid', Type='$type'")

        return try {
            // Bật WiFi nếu đang tắt
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
                Thread.sleep(1500)
            }

            // Tạo đối tượng WifiConfiguration theo chuẩn adb-join-wifi
            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$cleanSsid\""

                when (type) {
                    "WEP" -> {
                        wepKeys[0] = "\"$cleanPass\""
                        wepTxKeyIndex = 0
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                        allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                    }
                    "NONE", "OPEN" -> {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    }
                    else -> { // WPA / WPA2 / WPA3-transition (Mặc định)
                        if (cleanPass.isNotEmpty()) {
                            preSharedKey = "\"$cleanPass\""
                        } else {
                            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                        }
                    }
                }
                priority = 999
            }

            // Xóa cấu hình mạng cũ cùng SSID (nếu có)
            try {
                wifiManager.configuredNetworks
                    ?.filter { it.SSID == "\"$cleanSsid\"" || it.SSID == cleanSsid }
                    ?.forEach { wifiManager.removeNetwork(it.networkId) }
            } catch (e: Throwable) {
                Log.w(TAG, "Không thể danh sách mạng cũ: ${e.message}")
            }

            // Thêm mạng mới
            val networkId = wifiManager.addNetwork(wifiConfig)
            Log.d(TAG, "addNetwork() trả về netId=$networkId cho SSID: $cleanSsid")

            if (networkId != -1) {
                wifiManager.saveConfiguration()
                wifiManager.disconnect()
                val enabled = wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()

                if (enabled) {
                    Log.d(TAG, "✅ enableNetwork() thành công cho netId=$networkId")
                    return Pair(true, "Đang kết nối vào '$cleanSsid'... Vui lòng đợi 15-30 giây.")
                }
            }

            // Nếu WifiManager API thất bại -> Thử Root Fallback (wpa_cli)
            Log.w(TAG, "WifiManager.addNetwork thất bại, thử phương pháp Root Fallback (wpa_cli)...")
            val rootSuccess = connectViaRootWpaCli(cleanSsid, cleanPass, type)
            if (rootSuccess) {
                Pair(true, "Đã gửi lệnh kết nối qua Root (wpa_cli) cho '$cleanSsid'.")
            } else {
                Pair(false, "Không thể thêm mạng WiFi '$cleanSsid'. Kiểm tra mật khẩu hoặc quyền thiết bị.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "connectToWifi lỗi: ${e.message}", e)
            Pair(false, "Lỗi hệ thống khi nối WiFi: ${e.message}")
        }
    }

    /**
     * Fallback bằng wpa_cli (Cần Root) nếu WifiManager API bị Android khóa/lỗi
     */
    private fun connectViaRootWpaCli(ssid: String, pass: String, type: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("svc wifi enable\n")
            os.writeBytes("wpa_cli -i wlan0 reconfigure\n")
            os.writeBytes("NID=\$(wpa_cli -i wlan0 add_network)\n")
            os.writeBytes("wpa_cli -i wlan0 set_network \$NID ssid '\"$ssid\"'\n")

            if (type == "NONE" || pass.isEmpty()) {
                os.writeBytes("wpa_cli -i wlan0 set_network \$NID key_mgmt NONE\n")
            } else {
                os.writeBytes("wpa_cli -i wlan0 set_network \$NID psk '\"$pass\"'\n")
            }

            os.writeBytes("wpa_cli -i wlan0 enable_network \$NID\n")
            os.writeBytes("wpa_cli -i wlan0 select_network \$NID\n")
            os.writeBytes("wpa_cli -i wlan0 save_config\n")
            os.writeBytes("wpa_cli -i wlan0 reassociate\n")
            os.writeBytes("exit\n")
            os.flush()

            val exitCode = process.waitFor()
            Log.d(TAG, "Root wpa_cli exitCode: $exitCode")
            exitCode == 0
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi Root wpa_cli fallback: ${e.message}")
            false
        }
    }

    /** Trả về IP hiện tại của wlan0. Rỗng nếu chưa có IP. */
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

    /** Trả về SSID đang kết nối. */
    fun getCurrentSsid(): String {
        return try {
            wifiManager.connectionInfo.ssid?.removeSurrounding("\"") ?: ""
        } catch (e: Exception) { "" }
    }

    /** Kiểm tra loa đã kết nối WiFi nhà chưa. */
    fun isConnectedToHomeWifi(): Boolean {
        val ip = getCurrentIp()
        return ip.isNotEmpty() && !ip.startsWith("192.168.43.")
    }
}

