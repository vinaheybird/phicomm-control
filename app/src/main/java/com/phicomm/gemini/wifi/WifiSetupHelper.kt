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

        Log.d(TAG, "========== BẮT ĐẦU KẾT NỐI WIFI (ADB-JOIN-WIFI MODE) ==========")
        Log.d(TAG, "SSID='$cleanSsid', Type='$type', PassLength=${cleanPass.length}")

        return try {
            // Đảm bảo WiFi Client được bật
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "Đang bật WiFi Client...")
                wifiManager.isWifiEnabled = true
            }

            var waitCount = 0
            while (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED && waitCount < 10) {
                Thread.sleep(1000)
                waitCount++
            }

            // Tìm xem cấu hình này đã có chưa
            var existingConfig: WifiConfiguration? = null
            wifiManager.configuredNetworks?.forEach {
                if (it.SSID == "\"$cleanSsid\"" || it.SSID == cleanSsid) {
                    existingConfig = it
                }
            }

            val conf = existingConfig ?: WifiConfiguration()
            conf.SSID = "\"$cleanSsid\""
            
            if (type == "WEP") {
                conf.wepKeys[0] = "\"$cleanPass\""
                conf.wepTxKeyIndex = 0
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
            } else if (type == "WPA") {
                conf.preSharedKey = "\"$cleanPass\""
                // Match adb-join-wifi exact flags for WPA
                conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
            } else if (type == "OPEN") {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }

            val networkId = if (existingConfig == null) {
                wifiManager.addNetwork(conf)
            } else {
                wifiManager.updateNetwork(conf)
            }

            Log.d(TAG, "addNetwork/updateNetwork trả về netId=$networkId")

            if (networkId == -1) {
                return Pair(false, "Không thể thêm mạng WiFi (netId = -1). Vui lòng kiểm tra lại.")
            }

            wifiManager.saveConfiguration()

            // Dừng thread cũ nếu có
            mThread?.interrupt()
            
            // Giống y hệt adb-join-wifi: Lặp liên tục ép nó kết nối (vượt qua sự cản trở của OS)
            mThread = object : Thread() {
                override fun run() {
                    wifiManager.disconnect()
                    try {
                        for (i in 1..10) { // Thử tối đa 10 lần (100 giây) thay vì while(true) để tránh kẹt
                            if (isInterrupted) break
                            Log.d(TAG, "Joining, network id=$networkId (Lần $i)")
                            wifiManager.enableNetwork(networkId, true)
                            wifiManager.reconnect()
                            sleep(10000)
                        }
                    } catch (ignored: InterruptedException) {
                    }
                }
            }
            mThread?.start()

            Pair(true, "Đã gửi lệnh kết nối vào '$cleanSsid'. Vui lòng đợi 15-30 giây để loa bắt mạng.")
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
