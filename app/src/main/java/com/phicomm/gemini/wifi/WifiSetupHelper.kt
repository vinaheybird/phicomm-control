package com.phicomm.gemini.wifi

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log

/**
 * WifiSetupHelper — Kết nối WiFi cho Android 5.1.
 *
 * Fix lỗi addNetwork() = -1:
 *   Nguyên nhân: chip WiFi đang chạy SoftAP → addNetwork() luôn fail.
 *   Giải pháp: gọi addNetwork() SAU khi SoftAP đã tắt xong.
 *
 * Flow:
 *   1. Start background thread ngay, trả về success cho HTTP caller
 *   2. Thread: delay 3s → tắt SoftAP → đợi chip reset 4s → bật WiFi client
 *   3. Xóa mạng cũ
 *   4. addNetwork() + saveConfiguration() ← chip đã rảnh
 *   5. Loop enableNetwork + reconnect (8 lần x 5s)
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

        mThread?.interrupt()
        mThread = Thread {
            try {
                // 1. Đợi 3s để HTTP response kịp về điện thoại
                Thread.sleep(3000)

                // 2. Tắt SoftAP
                Log.d(TAG, "Đang tắt SoftAP...")
                var softApOff = false
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "svc wifi hotspot stop")).waitFor()
                    softApOff = true
                    Log.d(TAG, "Tắt SoftAP via svc: OK")
                } catch (e: Throwable) {
                    Log.w(TAG, "svc hotspot stop thất bại: ${e.message}")
                }
                
                if (!softApOff) {
                    try {
                        val m = wifiManager.javaClass.getMethod(
                            "setWifiApEnabled",
                            WifiConfiguration::class.java,
                            Boolean::class.javaPrimitiveType
                        )
                        m.invoke(wifiManager, null, false)
                        Log.d(TAG, "Tắt SoftAP via Reflection: OK")
                    } catch (e2: Throwable) {
                        Log.e(TAG, "Reflection thất bại: ${e2.message}")
                    }
                }

                // 3. Đợi chip WiFi reset (chip cần thời gian thoát SoftAP mode)
                Thread.sleep(4000)

                // 4. Bật WiFi client mode
                if (!wifiManager.isWifiEnabled) {
                    Log.d(TAG, "Bật WiFi client mode...")
                    wifiManager.isWifiEnabled = true
                    Thread.sleep(2000)
                }

                // 5. Xóa mạng cũ cùng SSID
                try {
                    wifiManager.configuredNetworks?.filter {
                        it.SSID == "\"$cleanSsid\"" || it.SSID == cleanSsid
                    }?.forEach {
                        Log.d(TAG, "Xóa mạng cũ netId=${it.networkId}")
                        wifiManager.removeNetwork(it.networkId)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Lỗi xóa mạng cũ: ${e.message}")
                }

                // 6. addNetwork() — bây giờ mới hợp lệ (SoftAP đã tắt)
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
                    else -> conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }

                val networkId = wifiManager.addNetwork(conf)
                Log.d(TAG, "addNetwork() → netId=$networkId")
                
                if (networkId == -1) {
                    Log.e(TAG, "addNetwork() vẫn -1 dù đã tắt SoftAP!")
                    return@Thread
                }
                wifiManager.saveConfiguration()

                // 7. Loop enableNetwork + reconnect
                for (i in 1..8) {
                    if (Thread.currentThread().isInterrupted) break
                    Log.d(TAG, "Joining netId=$networkId (lần $i/8)")
                    
                    wifiManager.disconnect()
                    val en = wifiManager.enableNetwork(networkId, true)
                    val rec = wifiManager.reconnect()
                    
                    Log.d(TAG, "enableNetwork=$en, reconnect=$rec")
                    
                    Thread.sleep(5000)
                    
                    val ip = getCurrentIp()
                    if (ip.isNotEmpty()) {
                        Log.d(TAG, "✅ Thành công! IP=$ip SSID=${getCurrentSsid()}")
                        break
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Thread bị interrupt.")
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi background thread: ${e.message}", e)
            }
        }
        mThread!!.start()
        
        return Pair(true, "Đã gửi lệnh kết nối. Loa sẽ tắt SoftAP rồi nối WiFi '$cleanSsid' sau ~3 giây!")
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
