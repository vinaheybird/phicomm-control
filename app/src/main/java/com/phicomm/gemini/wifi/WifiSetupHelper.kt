package com.phicomm.gemini.wifi

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log

/**
 * WifiSetupHelper — Kết nối WiFi cho Android 5.1 (Phicomm R1).
 *
 * Chiến lược: dùng wpa_cli + su thay vì WifiManager.addNetwork()
 * vì addNetwork() luôn trả về -1 trên môi trường này.
 *
 * Flow:
 *   1. Trả về HTTP success ngay lập tức
 *   2. Background thread:
 *        a. Delay 3s (để response về điện thoại)
 *        b. Tắt SoftAP
 *        c. Bật WiFi client mode
 *        d. Cấu hình WiFi qua wpa_cli (bypass WifiManager hoàn toàn)
 *        e. Chờ lấy IP
 */
@Suppress("DEPRECATION")
class WifiSetupHelper(private val context: Context) {

    companion object {
        private const val TAG = "geminiwifi"
        // Socket path của wpa_supplicant trên Phicomm R1
        private val WPA_SOCKET_PATHS = listOf(
            "/data/misc/wifi/sockets",
            "/data/system/wpa_supplicant",
            "/var/run/wpa_supplicant"
        )
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var mThread: Thread? = null

    fun connectToWifi(ssid: String, password: String, passwordType: String? = null): Pair<Boolean, String> {
        val cleanSsid = ssid.trim()
        val cleanPass = password.trim()
        val type = passwordType?.trim()?.uppercase() ?: if (cleanPass.isEmpty()) "OPEN" else "WPA"

        Log.d(TAG, "========== BẮT ĐẦU KẾT NỐI WIFI ==========")
        Log.d(TAG, "SSID='$cleanSsid', Type='$type', PassLength=${cleanPass.length}")

        mThread?.interrupt()
        mThread = Thread {
            try {
                Thread.sleep(3000)

                // Bước 1: Tắt SoftAP
                Log.d(TAG, "[1] Tắt SoftAP...")
                runRoot("svc wifi hotspot stop")
                // Fallback: Reflection
                try {
                    val m = wifiManager.javaClass.getMethod(
                        "setWifiApEnabled",
                        WifiConfiguration::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    m.invoke(wifiManager, null, false)
                } catch (_: Throwable) {}

                Thread.sleep(3000)

                // Bước 2: Bật WiFi client mode
                Log.d(TAG, "[2] Bật WiFi client mode...")
                runRoot("svc wifi enable")
                if (!wifiManager.isWifiEnabled) {
                    wifiManager.isWifiEnabled = true
                }
                Thread.sleep(3000)

                // Bước 3: Kết nối qua wpa_cli (bypass WifiManager)
                Log.d(TAG, "[3] Thử kết nối qua wpa_cli...")
                val wpaSuccess = connectViaWpaCli(cleanSsid, cleanPass, type)

                if (!wpaSuccess) {
                    // Fallback: thử WifiManager.addNetwork() lần cuối
                    Log.d(TAG, "[3b] wpa_cli thất bại, fallback WifiManager...")
                    connectViaWifiManager(cleanSsid, cleanPass, type)
                }

                // Bước 4: Chờ lấy IP
                Log.d(TAG, "[4] Chờ kết nối...")
                for (i in 1..10) {
                    Thread.sleep(3000)
                    val ip = getCurrentIp()
                    if (ip.isNotEmpty()) {
                        Log.d(TAG, "✅ Kết nối thành công! IP=$ip SSID=${getCurrentSsid()}")
                        break
                    }
                    Log.d(TAG, "Chờ IP... lần $i/10")
                }

            } catch (e: InterruptedException) {
                Log.d(TAG, "Thread bị interrupt.")
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi background thread: ${e.message}", e)
            }
        }
        mThread!!.start()

        return Pair(true, "Đang kết nối vào WiFi '$cleanSsid'. Loa sẽ tắt SoftAP sau ~3 giây...")
    }

    /**
     * Kết nối WiFi qua wpa_cli + su — không cần Android permission.
     * Trả về true nếu thành công.
     */
    private fun connectViaWpaCli(ssid: String, password: String, type: String): Boolean {
        // Tìm socket path hợp lệ
        val socketPath = WPA_SOCKET_PATHS.firstOrNull { path ->
            runRoot("test -d $path && echo OK").trim() == "OK"
        } ?: run {
            Log.w(TAG, "Không tìm thấy wpa_supplicant socket!")
            // Thử không có -p flag
            null
        }

        val wpaCli = if (socketPath != null) "wpa_cli -p $socketPath" else "wpa_cli"
        Log.d(TAG, "Dùng wpa_cli: $wpaCli (socket=$socketPath)")

        // Xóa tất cả network cũ để tránh conflict
        val listOut = runRoot("$wpaCli list_networks")
        Log.d(TAG, "Danh sách mạng hiện tại:\n$listOut")

        // Thêm network mới
        val addOut = runRoot("$wpaCli add_network").trim()
        val netId = addOut.trim().toIntOrNull()
        if (netId == null) {
            Log.e(TAG, "wpa_cli add_network thất bại: '$addOut'")
            return false
        }
        Log.d(TAG, "wpa_cli add_network → netId=$netId")

        // Set SSID
        val ssidEscaped = ssid.replace("\"", "\\\"")
        runRoot("$wpaCli set_network $netId ssid '\"$ssidEscaped\"'")

        when (type) {
            "WPA" -> {
                val passEscaped = password.replace("\"", "\\\"")
                runRoot("$wpaCli set_network $netId key_mgmt WPA-PSK")
                runRoot("$wpaCli set_network $netId psk '\"$passEscaped\"'")
            }
            "WEP" -> {
                runRoot("$wpaCli set_network $netId key_mgmt NONE")
                runRoot("$wpaCli set_network $netId wep_key0 '\"$password\"'")
                runRoot("$wpaCli set_network $netId wep_tx_keyidx 0")
            }
            else -> {
                runRoot("$wpaCli set_network $netId key_mgmt NONE")
            }
        }

        // Enable và select
        val enableOut = runRoot("$wpaCli enable_network $netId").trim()
        val selectOut = runRoot("$wpaCli select_network $netId").trim()
        val saveOut = runRoot("$wpaCli save_config").trim()
        val reconnOut = runRoot("$wpaCli reconnect").trim()

        Log.d(TAG, "enable=$enableOut, select=$selectOut, save=$saveOut, reconnect=$reconnOut")

        return enableOut == "OK" || selectOut == "OK"
    }

    /**
     * Fallback: dùng WifiManager.addNetwork() (có thể fail trên Android 5.1 SoftAP)
     */
    private fun connectViaWifiManager(ssid: String, password: String, type: String) {
        val conf = WifiConfiguration()
        conf.SSID = "\"$ssid\""

        when (type) {
            "WEP" -> {
                conf.wepKeys[0] = "\"$password\""
                conf.wepTxKeyIndex = 0
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
            }
            "WPA" -> {
                conf.preSharedKey = "\"$password\""
                conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
            }
            else -> conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }

        val netId = wifiManager.addNetwork(conf)
        Log.d(TAG, "WifiManager.addNetwork() → netId=$netId")
        if (netId != -1) {
            wifiManager.disconnect()
            wifiManager.enableNetwork(netId, true)
            wifiManager.reconnect()
            wifiManager.saveConfiguration()
        }
    }

    /**
     * Chạy lệnh shell với root, trả về stdout.
     */
    private fun runRoot(cmd: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            if (out.isNotBlank()) Log.v(TAG, "su[$cmd] → $out")
            if (err.isNotBlank()) Log.w(TAG, "su[$cmd] err → $err")
            out
        } catch (e: Throwable) {
            Log.e(TAG, "runRoot($cmd) lỗi: ${e.message}")
            ""
        }
    }

    fun getCurrentSsid(): String = try {
        wifiManager.connectionInfo.ssid?.removeSurrounding("\"") ?: ""
    } catch (e: Exception) { "" }

    fun getCurrentIp(): String = try {
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt != 0) String.format("%d.%d.%d.%d",
            ipInt and 0xff, ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
        else ""
    } catch (e: Exception) { "" }

    fun isConnectedToHomeWifi(): Boolean {
        val ip = getCurrentIp()
        return ip.isNotEmpty() && !ip.startsWith("192.168.43.")
    }
}
