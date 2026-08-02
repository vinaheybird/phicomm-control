package com.phicomm.gemini.web

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class MDnsPublisher(private val context: Context) {
    companion object {
        private const val TAG = "MDnsPublisher"
        private const val SERVICE_TYPE = "_http._tcp."
        private const val SERVICE_NAME = "phicomm"
    }

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun registerService(port: Int) {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsdManager == null) return

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = SERVICE_NAME
                serviceType = SERVICE_TYPE
                setPort(port)
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "✅ mDNS đã phát tên miền local: http://phicomm.local:$port")
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
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi hủy mDNS: ${e.message}")
        }
    }
}
