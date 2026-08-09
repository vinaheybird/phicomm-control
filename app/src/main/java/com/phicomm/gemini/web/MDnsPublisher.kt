package com.phicomm.gemini.web

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class MDnsPublisher(private val context: Context) {
    companion object {
        private const val TAG = "MDnsPublisher"
        private const val HOSTNAME = "phicomm"
        private const val SERVICE_TYPE = "_http._tcp.local."
    }

    private var jmDNS: JmDNS? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun registerService(port: Int) {
        Thread {
            try {
                // MulticastLock bắt buộc phải bật trước khi dùng jmDNS
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("PhicommMDnsLock")
                multicastLock?.setReferenceCounted(true)
                multicastLock?.acquire()

                // Lấy IP WiFi hiện tại của loa (Chờ nếu chưa có IP)
                var ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                var retries = 0
                while (ipInt == 0 && retries < 15) { // Thử lại tối đa 30 giây
                    Log.d(TAG, "Chờ mạng WiFi để cấp phát mDNS (lần thử ${retries + 1})...")
                    Thread.sleep(2000)
                    ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                    retries++
                }
                
                if (ipInt == 0) {
                    Log.e(TAG, "Không lấy được IP WiFi sau khi boot. Hủy mDNS.")
                    return@Thread
                }

                val ipBytes = byteArrayOf(
                    (ipInt and 0xff).toByte(),
                    (ipInt shr 8 and 0xff).toByte(),
                    (ipInt shr 16 and 0xff).toByte(),
                    (ipInt shr 24 and 0xff).toByte()
                )
                val deviceIp = InetAddress.getByAddress(ipBytes)

                // Tạo JmDNS với hostname "phicomm" → sẽ đăng ký A record cho phicomm.local
                jmDNS = JmDNS.create(deviceIp, HOSTNAME)

                // Đăng ký service HTTP
                val serviceInfo = ServiceInfo.create(SERVICE_TYPE, HOSTNAME, port, "path=/")
                jmDNS?.registerService(serviceInfo)

                Log.d(TAG, "✅ jmDNS đã phát: http://$HOSTNAME.local:$port (IP: $deviceIp)")
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi khởi tạo jmDNS: ${e.message}", e)
            }
        }.apply {
            isDaemon = true
            name = "jmDNS-register"
            start()
        }
    }

    fun unregisterService() {
        try {
            jmDNS?.unregisterAllServices()
            jmDNS?.close()
            jmDNS = null
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi hủy jmDNS: ${e.message}")
        } finally {
            try {
                if (multicastLock?.isHeld == true) multicastLock?.release()
                multicastLock = null
            } catch (_: Throwable) {}
        }
    }
}
