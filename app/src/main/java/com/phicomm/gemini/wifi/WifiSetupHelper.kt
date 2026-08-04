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

        Log.d(TAG, "Chuẩn bị kết nối WiFi: SSID='$cleanSsid', Type='$type'")

        return try {
            // KHÔNG tắt SoftAP thủ công bằng killall ở đây! 
            // Nếu tắt sớm quá sẽ làm hỏng state machine của ubus trên loa Phicomm, 
            // khiến loa không thể kết nối tới WiFi nhà.

            // 1. Đảm bảo WiFi Client đang bật
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
                Thread.sleep(1500)
            }

            // 2. Thử nối qua ubus & wpa_cli (Cách native mạnh nhất trên Phicomm R1)
            val rootSuccess = connectViaRoot(cleanSsid, cleanPass, type)
            if (rootSuccess) {
                Log.d(TAG, "Đã gửi lệnh ubus & wpa_cli thành công.")
            }

            // 3. Dự phòng bằng Android WifiManager API
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
                    else -> {
                        preSharedKey = "\"$cleanPass\""
                    }
                }
                priority = 999
            }

            try {
                wifiManager.configuredNetworks
                    ?.filter { it.SSID == "\"$cleanSsid\"" || it.SSID == cleanSsid }
                    ?.forEach { wifiManager.removeNetwork(it.networkId) }
            } catch (e: Throwable) {}

            val netId = wifiManager.addNetwork(conf)
            if (netId != -1) {
                wifiManager.saveConfiguration()
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
                Log.d(TAG, "WifiManager enableNetwork thành công cho netId=$netId")
            }

            Pair(true, "Đã gửi lệnh kết nối vào '$cleanSsid'.")
        } catch (e: Throwable) {
            Log.e(TAG, "connectToWifi lỗi: ${e.message}", e)
            Pair(false, "Lỗi hệ thống khi nối WiFi: ${e.message}")
        }
    }

    /**
     * Nối mạng bằng ubus (chuẩn Phicomm) & wpa_cli (Cần Root)
     */
    private fun connectViaRoot(ssid: String, pass: String, type: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("svc wifi enable\n")
            os.writeBytes("sleep 1\n")
            
            // Cách 1: Native OpenWrt Phicomm (Rất ổn định trên R1)
            os.writeBytes("ubus call onboarding connect '{\"ssid\":\"$ssid\", \"password\":\"$pass\"}' 2>/dev/null\n")
            
            // Cách 2: wpa_cli đề phòng ubus hỏng
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

            process.waitFor()
            true
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
}
