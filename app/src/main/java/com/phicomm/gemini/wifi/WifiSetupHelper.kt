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
        private const val TAG = "WifiSetupHelper"
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Kết nối loa vào mạng WiFi với SSID và Password theo chuẩn steinwurf/adb-join-wifi.
     */
    fun connectToWifi(ssid: String, password: String, passwordType: String? = null): Pair<Boolean, String> {
        val cleanSsid = ssid.trim()
        val cleanPass = password.trim()
        val type = passwordType?.trim()?.uppercase() ?: if (cleanPass.isEmpty()) "OPEN" else "WPA"

        Log.d(TAG, "Chuẩn bị kết nối WiFi (steinwurf/adb-join-wifi): SSID='$cleanSsid', Type='$type'")

        return try {
            // 0. Tắt Tethering / SoftAP nếu đang mở
            disableSoftApIfActive()
            Thread.sleep(1000)

            // 1. Bật WiFi Client Mode nếu đang tắt
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
                Thread.sleep(1500)
            }

            // 2. Tạo đối tượng WifiConfiguration CHUẨN 100% steinwurf/adb-join-wifi
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
                    else -> { // WPA / WPA2 (Chuẩn steinwurf/adb-join-wifi: chỉ cần set preSharedKey)
                        preSharedKey = "\"$cleanPass\""
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
                Log.w(TAG, "Không thể xóa mạng cũ: ${e.message}")
            }

            // Thêm mạng mới
            val netId = wifiManager.addNetwork(conf)
            Log.d(TAG, "addNetwork() trả về netId=$netId cho SSID: $cleanSsid")

            if (netId != -1) {
                wifiManager.saveConfiguration()
                wifiManager.disconnect()
                val enabled = wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()

                if (enabled) {
                    Log.d(TAG, "✅ enableNetwork() thành công cho netId=$netId")
                    return Pair(true, "Đang kết nối vào '$cleanSsid'... Vui lòng đợi 15-30 giây.")
                }
            }

            // Nếu WifiManager API trả về -1 -> Thử Root Fallback (wpa_cli & ubus)
            Log.w(TAG, "WifiManager.addNetwork trả về -1, thử phương pháp Root Fallback (wpa_cli & ubus)...")
            val rootSuccess = connectViaRoot(cleanSsid, cleanPass, type)
            if (rootSuccess) {
                Pair(true, "Đã gửi lệnh kết nối qua Root (wpa_cli & ubus) cho '$cleanSsid'.")
            } else {
                Pair(false, "Không thể thêm mạng WiFi '$cleanSsid'. Kiểm tra mật khẩu hoặc quyền thiết bị.")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "connectToWifi lỗi: ${e.message}", e)
            Pair(false, "Lỗi hệ thống khi nối WiFi: ${e.message}")
        }
    }

    /**
     * Fallback bằng wpa_cli & ubus (Cần Root)
     */
    private fun connectViaRoot(ssid: String, pass: String, type: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("svc wifi enable\n")
            os.writeBytes("ubus call onboarding connect '{\"ssid\":\"$ssid\", \"password\":\"$pass\"}' 2>/dev/null\n")
            os.writeBytes("wpa_cli -i wlan0 reconfigure 2>/dev/null\n")
            os.writeBytes("NID=\$(wpa_cli -i wlan0 add_network)\n")
            os.writeBytes("wpa_cli -i wlan0 set_network \$NID ssid '\"$ssid\"'\n")

            if (type == "OPEN" || pass.isEmpty()) {
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
            Log.e(TAG, "Lỗi Root fallback: ${e.message}")
            false
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

    private fun disableSoftApIfActive() {
        try {
            val method = wifiManager.javaClass.getMethod("setWifiApEnabled", WifiConfiguration::class.java, java.lang.Boolean.TYPE)
            method.invoke(wifiManager, null, false)
        } catch (e: Throwable) {}

        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("service call connectivity 33 i32 0\n")
            os.writeBytes("killall hostapd 2>/dev/null\n")
            os.writeBytes("killall dnsmasq 2>/dev/null\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
        } catch (e: Throwable) {}
    }
}
