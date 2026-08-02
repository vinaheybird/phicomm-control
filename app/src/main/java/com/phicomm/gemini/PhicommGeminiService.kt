package com.phicomm.gemini

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.phicomm.gemini.audio.PromptMuteController
import com.phicomm.gemini.bluetooth.BluetoothController
import com.phicomm.gemini.hardware.LedController
import com.phicomm.gemini.web.MDnsPublisher
import com.phicomm.gemini.web.WebConfigServer

/**
 * PhicommGeminiService — Background service đặt tại root package com.phicomm.gemini
 * Đảm bảo tương thích 100% với Android 5.1 và adbd của loa Phicomm R1.
 */
class PhicommGeminiService : Service() {

    companion object {
        private const val TAG = "PhicommGeminiService"
        private const val CHANNEL_ID = "PhicommControllerChannel"
        private const val NOTIFICATION_ID = 1001
    }

    private var webConfigServer: WebConfigServer? = null
    private var mDnsPublisher: MDnsPublisher? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Khởi chạy Phicomm Bluetooth Controller Service...")

        // 1. ƯU TIÊN SỐ 1: Khởi chạy Web Server tại cổng 8080 trước tiên!
        try {
            webConfigServer = WebConfigServer(this, 8080)
            webConfigServer?.startServer()
            Log.d(TAG, "✅ WebConfigServer đã khởi tạo thành công tại cổng 8080")
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Lỗi khởi chạy WebConfigServer: ${e.message}", e)
        }

        // 2. Khởi chạy Foreground Notification
        try {
            startForegroundNotification()
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi startForegroundNotification: ${e.message}", e)
        }

        // 3. Khởi tạo mDNS
        try {
            mDnsPublisher = MDnsPublisher(this)
            mDnsPublisher?.registerService(8080)
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi mDNS: ${e.message}", e)
        }

        // 4. Khởi tạo các phần cứng
        try { BluetoothController.init(this) } catch (e: Throwable) { Log.e(TAG, "Lỗi BT: ${e.message}") }
        try { PromptMuteController.init(this) } catch (e: Throwable) { Log.e(TAG, "Lỗi Mute: ${e.message}") }
        try { LedController.init(this) } catch (e: Throwable) { Log.e(TAG, "Lỗi LED: ${e.message}") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (webConfigServer == null) {
                webConfigServer = WebConfigServer(this, 8080)
                webConfigServer?.startServer()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi startServer trong onStartCommand: ${e.message}", e)
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Dịch vụ Loa Phicomm Bluetooth Controller",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Loa Phicomm Bluetooth Controller")
                .setContentText("Trang quản lý web đang hoạt động tại http://192.168.43.1:8080")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            Log.e(TAG, "Không thể tạo foreground notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        try { mDnsPublisher?.unregisterService() } catch (e: Throwable) {}
        try { PromptMuteController.unregister() } catch (e: Throwable) {}
        try { webConfigServer?.stop() } catch (e: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
