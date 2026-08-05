package com.phicomm.gemini.wifi

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log

/**
 * WifiSetupHelper — Kết nối WiFi theo đúng chuẩn steinwurf/adb-join-wifi cho Android 5.1.
 *
 * Flow:
 *   1. Xóa mạng cũ cùng SSID
 *   2. Tạo WifiConfiguration với ĐẦY ĐỦ cipher flags (theo adb-join-wifi)
 *   3. addNetwork() → saveConfiguration()
 *   4. Background thread:
 *        a. Delay 3s cho HTTP response về điện thoại
 *        b. Tắt SoftAP bằng "svc wifi hotspot stop" (an toàn hơn Reflection)
 *        c. Đợi chip reset 4s
 *        d. Bật WiFi client nếu chưa bật
 *        e. saveConfiguration() lần 2
 *        f. Loop enableNetwork + reconnect (tối đa 8 lần x 5s)
 */
@Suppress("DEPRECATION")
class WifiSetupHelper(private val context: Context) {

    companion object {
        private const val TAG = "geminiwifi"
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

        return try {
            // 1. Dọn dẹp mạng cũ cùng SSID
            try {
                wifiManager.configuredNetworks?.let { networks ->
                    networks.filter { it.SSID == "\"$cleanSsid\"" || it.SSID == cleanSsid }
                        .forEach {
                            Log.d(TAG, "Xóa mạng cũ netId=${it.networkId}")
                            wifiManager.removeNetwork(it.networkId)
                        }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi khi xóa mạng cũ: ${e.message}")
            }

            // 2. Tạo WifiConfiguration theo chuẩn adb-join-wifi (ĐẦY ĐỦ cipher flags)
            val conf = WifiConfiguration()
            conf.SSID = "\"$cleanSsid\""

            when (type) {
                "WEP" -> {
                    conf.wepKeys[0] = "\"$cleanPass\""
                    conf.wepTxKeyIndex = 0
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                    conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                }
                "WPA" -> {
                    // BẮT BUỘC phải set đầy đủ cipher flags theo adb-join-wifi
                    // Thiếu các flags này → addNetwork() trả về -1 hoặc không kết nối được
                    conf.preSharedKey = "\"$cleanPass\""
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
                else -> { // OPEN
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            }

            // 3. Thêm mạng và lưu lần 1
            val networkId = wifiManager.addNetwork(conf)
            Log.d(TAG, "addNetwork() trả về netId=$networkId")

            if (networkId == -1) {
                return Pair(false, "Không thể thêm mạng WiFi (netId = -1). Kiểm tra SSID/mật khẩu.")
            }
            wifiManager.saveConfiguration()

            // 4. Background thread: tắt SoftAP → bật WiFi client → kết nối
            mThread?.interrupt()
            mThread = Thread {
                try {
                    // Đợi 3s cho HTTP response về điện thoại trước khi ngắt SoftAP
                    Thread.sleep(3000)

                    // Tắt SoftAP bằng shell command (tin cậy hơn Reflection trên Android 5.1)
                    Log.d(TAG, "Đang tắt SoftAP...")
                    try {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "svc wifi hotspot stop"))
                            .waitFor()
                    } catch (e: Throwable) {
                        Log.w(TAG, "svc hotspot stop thất bại, thử Reflection: ${e.message}")
                        try {
                            val method = wifiManager.javaClass.getMethod(
                                "setWifiApEnabled",
                                WifiConfiguration::class.java,
                                Boolean::class.javaPrimitiveType
                            )
                            method.invoke(wifiManager, null, false)
                        } catch (e2: Throwable) {
                            Log.e(TAG, "Reflection setWifiApEnabled cũng thất bại: ${e2.message}")
                        }
                    }

                    // Đợi chip WiFi reset xong (quan trọng: chip không thể SoftAP + Station cùng lúc)
                    Thread.sleep(4000)

                    // Bật WiFi client mode nếu chưa bật
                    if (!wifiManager.isWifiEnabled) {
                        Log.d(TAG, "Đang bật WiFi client mode...")
                        @Suppress("DEPRECATION")
                        wifiManager.isWifiEnabled = true
                        Thread.sleep(2000)
                    }

                    // Lưu lại configuration sau khi WiFi client đã bật
                    wifiManager.saveConfiguration()

                    // Loop kết nối theo đúng pattern adb-join-wifi
                    for (i in 1..8) {
                        if (Thread.currentThread().isInterrupted) break
                        Log.d(TAG, "Joining network id=$networkId (Lần $i/8)")

                        wifiManager.disconnect()
                        val en = wifiManager.enableNetwork(networkId, true)
                        val rec = wifiManager.reconnect()
                        Log.d(TAG, "enableNetwork=$en, reconnect=$rec")

                        // Chờ 5s rồi kiểm tra đã có IP chưa
                        Thread.sleep(5000)

                        val ip = getCurrentIp()
                        if (ip.isNotEmpty()) {
                            Log.d(TAG, "✅ Kết nối thành công! IP=$ip, SSID=${getCurrentSsid()}")
                            break
                        }
                    }
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Thread bị interrupt, dừng kết nối.")
                } catch (e: Throwable) {
                    Log.e(TAG, "Lỗi trong background WiFi thread: ${e.message}", e)
                }
            }
            mThread!!.start()

            Pair(true, "Đã gửi lệnh kết nối WiFi. Loa sẽ ngắt SoftAP và kết nối vào '$cleanSsid' sau ~3 giây!")
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
