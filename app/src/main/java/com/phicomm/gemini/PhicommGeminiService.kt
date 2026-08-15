package com.phicomm.gemini

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.phicomm.gemini.bluetooth.BluetoothController

import com.phicomm.gemini.web.MDnsPublisher
import com.phicomm.gemini.web.WebConfigServer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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

        // 1. Khởi chạy Web Server
        try {
            webConfigServer = WebConfigServer(this, 8080)
            webConfigServer?.startServer()
            Log.d(TAG, "✅ WebConfigServer đã khởi tạo thành công tại cổng 8080")
            
            val web19999 = WebConfigServer(this, 19999)
            web19999.startServer()
            Log.d(TAG, "✅ WebConfigServer đã khởi tạo thành công tại cổng 19999")
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

        // 5. Bắt đầu VPN Auth Activity
        try {
            val authIntent = Intent(this, com.phicomm.gemini.vpn.VpnAuthActivity::class.java)
            authIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(authIntent)
            Log.d(TAG, "✅ Đã gửi lệnh startActivity cho VpnAuthActivity")
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Lỗi khởi chạy VpnAuthActivity: ${e.message}", e)
        }
    }

    private fun testAudioRecord() {
        Thread {
            try {
                val sampleRate = 16000
                val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
                val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
                val minBufSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                
                val audioRecord = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufSize * 2
                )
                
                if (audioRecord.state != android.media.AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize!")
                    return@Thread
                }
                
                audioRecord.startRecording()
                Log.d(TAG, "AudioRecord started successfully!")
                
                val file = java.io.File(getExternalFilesDir(null), "audiorecord_test.pcm")
                val fos = java.io.FileOutputStream(file)
                val buffer = ByteArray(minBufSize)
                
                // Record for 3 seconds
                val endTime = System.currentTimeMillis() + 3000
                while (System.currentTimeMillis() < endTime) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        fos.write(buffer, 0, read)
                    }
                }
                
                audioRecord.stop()
                audioRecord.release()
                fos.close()
                Log.d(TAG, "AudioRecord test finished! Saved to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord test exception: ${e.message}", e)
            }
        }.start()
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
            @Suppress("DEPRECATION")
            val notification: Notification = NotificationCompat.Builder(this)
                .setContentTitle("Phicomm Gemini")
                .setContentText("Web server đang chạy tại cổng 8080")
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
        try { webConfigServer?.stop() } catch (e: Throwable) {}

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

