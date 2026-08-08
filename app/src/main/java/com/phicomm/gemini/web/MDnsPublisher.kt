package com.phicomm.gemini.web

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

class MDnsPublisher(private val context: Context) {
    companion object {
        private const val TAG = "MDnsPublisher"
        private const val SERVICE_TYPE = "_http._tcp."
        private const val SERVICE_NAME = "phicomm"
    }

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun registerService(port: Int) {
        try {
            // Hủy đăng ký cũ nếu có trước khi đăng ký mới
            unregisterService()

            // Lấy MulticastLock để cho phép nhận gói tin mDNS
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("PhicommMDnsLock")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()

            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsdManager == null) return

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = SERVICE_NAME
                serviceType = SERVICE_TYPE
                setPort(port)
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(registeredInfo: NsdServiceInfo) {
                    // registeredInfo.serviceName có thể là "phicomm (1)" nếu "phicomm" đã bị trùng
                    Log.d(TAG, "✅ mDNS đã phát tên miền local: http://${registeredInfo.serviceName}.local:$port")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "❌ mDNS đăng ký tên miền thất bại, mã lỗi: $errorCode")
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    Log.d(TAG, "mDNS Service unregistered")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "mDNS Unregistration failed: $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi khởi tạo mDNS: ${e.message}", e)
        }
    }

    fun unregisterService() {
        try {
            if (registrationListener != null) {
                nsdManager?.unregisterService(registrationListener)
                registrationListener = null
            }
            if (multicastLock != null && multicastLock!!.isHeld) {
                multicastLock?.release()
                multicastLock = null
            }
        } catch (e: IllegalArgumentException) {
            // Đã được unregister hoặc chưa đăng ký thành công
            Log.d(TAG, "mDNS listener chưa được đăng ký hoặc đã hủy: ${e.message}")
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi hủy mDNS: ${e.message}")
        }
    }
}
