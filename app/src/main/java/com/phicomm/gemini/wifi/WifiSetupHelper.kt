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
    private var mThread: Thread? = null

    fun connectToWifi(ssid: String, password: String, passwordType: String? = null): Pair<Boolean, String> {
        val cleanSsid = ssid.trim()
        val cleanPass = password.trim()
        val type = passwordType?.trim()?.uppercase() ?: if (cleanPass.isEmpty()) "OPEN" else "WPA"

        Log.d(TAG, "========== BẮT ĐẦU KẾT NỐI WIFI (BULLETPROOF MODE) ==========")
        Log.d(TAG, "SSID='$cleanSsid', Type='$type', PassLength=${cleanPass.length}")

        return try {
            // 1. Dọn dẹp mạng cũ
            try {
                wifiManager.configuredNetworks?.let { networks ->
                    networks.filter { it.SSID == "\"$cleanSsid\"" || it.SSID == cleanSsid }
                        .forEach { wifiManager.removeNetwork(it.networkId) }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi khi xóa mạng cũ: ${e.message}")
            }

            // 2. Tạo cấu hình cực kỳ cơ bản (để tránh lỗi -1 do sai ciphers)
            val conf = WifiConfiguration()
            conf.SSID = "\"$cleanSsid\""
            
            if (type == "WEP") {
                conf.wepKeys[0] = "\"$cleanPass\""
                conf.wepTxKeyIndex = 0
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
            } else if (type == "WPA") {
                conf.preSharedKey = "\"$cleanPass\""
                // Không set các flags phụ để hệ thống tự cấp phát, tránh bị reject (-1)
            } else if (type == "OPEN") {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }

            // 3. Thêm mạng mới
            val networkId = wifiManager.addNetwork(conf)
            Log.d(TAG, "addNetwork() trả về netId=$networkId")

            if (networkId == -1) {
                return Pair(false, "Không thể thêm mạng WiFi (netId = -1). Vui lòng thử lại.")
            }

            wifiManager.saveConfiguration()

            // 4. Bắt đầu tiến trình kết nối bất đồng bộ
            mThread?.interrupt()
            
            mThread = object : Thread() {
                override fun run() {
                    try {
                        // Trì hoãn 2 giây để Web API kịp trả về HTTP 200 OK cho điện thoại
                        sleep(2000)
                        
                        // Tắt SoftAP bằng Reflection để giải phóng chip WiFi
                        Log.d(TAG, "Đang tắt SoftAP qua Reflection...")
                        try {
                            val method = wifiManager.javaClass.getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
                            method.invoke(wifiManager, null, false)
                        } catch (e: Throwable) {
                            Log.e(TAG, "Lỗi tắt SoftAP: ${e.message}")
                        }
                        
                        sleep(2000) // Đợi chip xả trạng thái
                        
                        // Đảm bảo WiFi Client được bật
                        if (!wifiManager.isWifiEnabled) {
                            wifiManager.isWifiEnabled = true
                        }
                        
                        // Lặp ép kết nối liên tục giống adb-join-wifi
                        for (i in 1..15) { // Thử tối đa 15 lần (75 giây)
                            if (isInterrupted) break
                            Log.d(TAG, "Joining, network id=$networkId (Lần $i)")
                            
                            wifiManager.disconnect()
                            val en = wifiManager.enableNetwork(networkId, true)
                            val rec = wifiManager.reconnect()
                            
                            Log.d(TAG, "enable=$en, reconnect=$rec")
                            sleep(5000)
                        }
                    } catch (ignored: InterruptedException) {
                    }
                }
            }
            mThread?.start()

            Pair(true, "Đã gửi lệnh kết nối. Loa sẽ tự ngắt SoftAP và bắt WiFi sau 2 giây!")
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
