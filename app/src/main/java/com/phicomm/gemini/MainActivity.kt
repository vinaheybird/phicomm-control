package com.phicomm.gemini

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.phicomm.gemini.bluetooth.BluetoothController
import com.phicomm.gemini.service.PhicommGeminiService

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var btnTalk: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        btnTalk = findViewById(R.id.btnTalk)

        btnTalk.text = "🔍 Bật Chế Độ Dò Tìm Bluetooth (5p)"

        checkPermissions()
        displayWifiIp()

        btnTalk.setOnClickListener {
            val success = BluetoothController.makeDiscoverable(300)
            if (success) {
                Toast.makeText(this, "Đã bật chế độ dò tìm Bluetooth trong 5 phút!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Không thể bật chế độ dò tìm Bluetooth", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startControllerService() {
        val serviceIntent = Intent(this, PhicommGeminiService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        tvStatus.text = "🟢 Dịch vụ Web Controller Bluetooth đang chạy ngầm"
    }

    private fun displayWifiIp() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
            tvIpAddress.text = "Trang Web Điều Khiển: http://$ipAddress:8080"
        } catch (e: Exception) {
            tvIpAddress.text = "Trang Web Điều Khiển: http://phicomm.local:8080"
        }
    }

    private fun checkPermissions() {
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
        } else {
            startControllerService()
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
