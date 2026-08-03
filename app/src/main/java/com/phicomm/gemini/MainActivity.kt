package com.phicomm.gemini

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.phicomm.gemini.bluetooth.BluetoothController
import com.phicomm.gemini.web.WebConfigServer

/**
 * MainActivity — Standard android.app.Activity (100% tương thích ROM Android 5.1 stripped, không phụ thuộc AppCompat theme).
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 200
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var btnTalk: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. ƯU TIÊN SỐ 1: Khởi chạy Web Server 8080 ngay lập tức khi Activity được mở
        try {
            WebConfigServer.startInstance(this, 8080)
            Log.d(TAG, "✅ WebConfigServer đã được khởi chạy trực tiếp từ MainActivity.onCreate()")
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi WebConfigServer từ MainActivity: ${e.message}", e)
        }

        // 2. Khởi chạy Service ngầm
        startControllerService()

        // 3. Xử lý Wi-Fi Intent (nếu gọi qua adb shell am start -n com.phicomm.gemini/.MainActivity -e ssid "..." -e password "...")
        handleWifiIntent(intent)

        // 4. Khởi tạo giao diện UI (nếu có màn hình/trình giả lập)
        try {
            setContentView(R.layout.activity_main)
            tvStatus = findViewById(R.id.tvStatus)
            tvIpAddress = findViewById(R.id.tvIpAddress)
            btnTalk = findViewById(R.id.btnTalk)

            btnTalk.text = "🔍 Bật Chế Độ Dò Tìm Bluetooth (5p)"

            displayWifiIp()

            btnTalk.setOnClickListener {
                try {
                    val success = BluetoothController.makeDiscoverable(300)
                    if (success) {
                        Toast.makeText(this, "Đã bật chế độ dò tìm Bluetooth trong 5 phút!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Không thể bật chế độ dò tìm Bluetooth", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Lỗi makeDiscoverable: ${e.message}", e)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Bỏ qua UI layout (ROM stripped headless): ${e.message}")
        }

        checkPermissions()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWifiIntent(intent)
    }

    private fun handleWifiIntent(intent: Intent?) {
        if (intent == null) return
        val ssid = intent.getStringExtra("ssid")
            ?: intent.getStringExtra("SSID")
            ?: return

        val password = intent.getStringExtra("password")
            ?: intent.getStringExtra("pass")
            ?: intent.getStringExtra("key")
            ?: ""
        val passwordType = intent.getStringExtra("password_type")
            ?: intent.getStringExtra("type")

        Log.d(TAG, "🔑 Phát hiện Intent yêu cầu nối WiFi từ ADB/App: SSID='$ssid'")
        kotlin.concurrent.thread {
            try {
                val helper = com.phicomm.gemini.wifi.WifiSetupHelper(this)
                val (success, msg) = helper.connectToWifi(ssid, password, passwordType)
                Log.d(TAG, "Kết quả nối WiFi từ Intent: success=$success, msg=$msg")
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi xử lý handleWifiIntent: ${e.message}", e)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            WebConfigServer.startInstance(this, 8080)
        } catch (e: Throwable) {}
        startControllerService()
    }

    private fun startControllerService() {
        try {
            val serviceIntent = Intent(this, PhicommGeminiService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            if (::tvStatus.isInitialized) {
                tvStatus.text = "🟢 Dịch vụ Web Controller Bluetooth đang chạy ngầm"
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi khởi chạy Service: ${e.message}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun displayWifiIp() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
            if (::tvIpAddress.isInitialized) {
                tvIpAddress.text = "Trang Web Điều Khiển: http://$ipAddress:8080"
            }
        } catch (e: Throwable) {
            if (::tvIpAddress.isInitialized) {
                tvIpAddress.text = "Trang Web Điều Khiển: http://phicomm.local:8080"
            }
        }
    }

    private fun checkPermissions() {
        try {
            val permissions = mutableListOf(
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            val neededPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (neededPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi checkPermissions: ${e.message}", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startControllerService()
        }
    }
}
