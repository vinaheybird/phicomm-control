package com.phicomm.gemini.service

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

class PhicommGeminiService : Service() {

    companion object {
        private const val TAG = "PhicommGeminiService"
        private const val CHANNEL_ID = "PhicommControllerChannel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var webConfigServer: WebConfigServer
    private lateinit var mDnsPublisher: MDnsPublisher

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Khởi chạy Phicomm Bluetooth Controller Service...")

        startForegroundNotification()

        // Khởi tạo các bộ điều khiển phần cứng
        BluetoothController.init(this)
        PromptMuteController.init(this)
        LedController.init(this)

        // Khởi chạy Web Server tại cổng 8080
        webConfigServer = WebConfigServer(this, 8080)
        webConfigServer.startServer()

        // Đăng ký tên miền mDNS http://phicomm.local:8080
        mDnsPublisher = MDnsPublisher(this)
        mDnsPublisher.registerService(8080)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            .setContentText("Trang quản lý web đang hoạt động tại http://phicomm.local:8080")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        mDnsPublisher.unregisterService()
        PromptMuteController.unregister()
        webConfigServer.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
