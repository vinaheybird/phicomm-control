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
     * Kết nối loa vào mạng WiFi với SSID và Password theo chuẩn steinwurf/adb-join-wifi.
     */
    fun connectToWifi(ssid: String, password: String, passwordType: String? = null): Pair<Boolean, String> {
        val cleanSsid = ssid.trim()
        val cleanPass = password.trim()
        val type = passwordType?.trim()?.uppercase() ?: if (cleanPass.isEmpty()) "OPEN" else "WPA"

        Log.d(TAG, "========== BẮT ĐẦU KẾT NỐI WIFI ==========")
        Log.d(TAG, "SSID='$cleanSsid', Type='$type', PassLength=${cleanPass.length}")

        return try {
            // 1. Đảm bảo WiFi Client đang bật
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "WiFi đang tắt, tiến hành bật isWifiEnabled = true")
                wifiManager.isWifiEnabled = true
                Thread.sleep(2000)
                Log.d(TAG, "Trạng thái sau khi bật WiFi: ${wifiManager.wifiState}")
            } else {
                Log.d(TAG, "WiFi đã được bật sẵn.")
            }

            // 2. Thử nối qua ubus & wpa_cli (Cách native mạnh nhất trên Phicomm R1)
            Log.d(TAG, "--- Đang gọi connectViaRoot ---")
            val rootSuccess = connectViaRoot(cleanSsid, cleanPass, type)
            Log.d(TAG, "Kết quả connectViaRoot: $rootSuccess")

            // 3. Dự phòng bằng Android WifiManager API
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
                    else -> {
                        preSharedKey = "\"$cleanPass\""
                    }
                }
                priority = 999
            }
            Log.d(TAG, "WifiConfiguration SSID: ${conf.SSID}, preSharedKey: ${conf.preSharedKey != null}")

            try {
                wifiManager.configuredNetworks?.let { networks ->
                    Log.d(TAG, "Đang quét ${networks.size} mạng đã lưu để xóa cấu hình cũ.")
                    networks.filter { it.SSID == "\"$cleanSsid\"" || it.SSID == cleanSsid }
                        .forEach { 
                            val removed = wifiManager.removeNetwork(it.networkId)
                            Log.d(TAG, "Đã xóa mạng cũ id=${it.networkId}, result=$removed")
                        }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi khi xóa mạng cũ", e)
            }

            val netId = wifiManager.addNetwork(conf)
            Log.d(TAG, "wifiManager.addNetwork() trả về netId=$netId")

            if (netId != -1) {
                val saveRes = wifiManager.saveConfiguration()
                Log.d(TAG, "wifiManager.saveConfiguration() trả về $saveRes")
                
                val disRes = wifiManager.disconnect()
                Log.d(TAG, "wifiManager.disconnect() trả về $disRes")
                
                val enableRes = wifiManager.enableNetwork(netId, true)
                Log.d(TAG, "wifiManager.enableNetwork($netId) trả về $enableRes")
                
                val recRes = wifiManager.reconnect()
                Log.d(TAG, "wifiManager.reconnect() trả về $recRes")
            } else {
                Log.e(TAG, "addNetwork() THẤT BẠI (-1)")
            }

            Log.d(TAG, "========== KẾT THÚC LỆNH KẾT NỐI WIFI ==========")
            Pair(true, "Đã gửi lệnh kết nối vào '$cleanSsid'.")
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi hệ thống khi nối WiFi: ${e.message}", e)
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
            
            // Xây dựng script đầy đủ log ra shell
            val script = StringBuilder().apply {
                append("echo '=== ROOT SCRIPT START ==='\n")
                append("svc wifi enable\n")
                append("sleep 1\n")
                
                // Cách 1: Native OpenWrt Phicomm
                append("echo 'Chay ubus call onboarding...'\n")
                append("ubus call onboarding connect '{\"ssid\":\"$ssid\", \"password\":\"$pass\"}' 2>&1\n")
                
                // Cách 2: wpa_cli
                append("echo 'Chay wpa_cli...'\n")
                append("wpa_cli -i wlan0 reconfigure 2>&1\n")
                append("NID=\$(wpa_cli -i wlan0 add_network 2>&1)\n")
                append("echo 'NID sinh ra la: '\$NID\n")
                append("wpa_cli -i wlan0 set_network \$NID ssid '\"$ssid\"' 2>&1\n")
                
                if (type == "OPEN" || pass.isEmpty()) {
                    append("wpa_cli -i wlan0 set_network \$NID key_mgmt NONE 2>&1\n")
                } else {
                    append("wpa_cli -i wlan0 set_network \$NID psk '\"$pass\"' 2>&1\n")
                }
                
                append("wpa_cli -i wlan0 enable_network \$NID 2>&1\n")
                append("wpa_cli -i wlan0 select_network \$NID 2>&1\n")
                append("wpa_cli -i wlan0 save_config 2>&1\n")
                append("wpa_cli -i wlan0 reassociate 2>&1\n")
                append("echo '=== ROOT SCRIPT END ==='\n")
                append("exit\n")
            }.toString()

            os.writeBytes(script)
            os.flush()

            // Đọc kết quả từ stdout & stderr
            java.lang.Thread {
                try {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "[ROOT OUT] $line")
                    }
                } catch (e: Exception) {}
            }.start()
            
            java.lang.Thread {
                try {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.e(TAG, "[ROOT ERR] $line")
                    }
                } catch (e: Exception) {}
            }.start()

            val exitCode = process.waitFor()
            Log.d(TAG, "Root script exitCode: $exitCode")
            exitCode == 0
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi khi chạy Root Script: ${e.message}", e)
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
