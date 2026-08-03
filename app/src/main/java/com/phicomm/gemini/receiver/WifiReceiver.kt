package com.phicomm.gemini.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.phicomm.gemini.wifi.WifiSetupHelper
import kotlin.concurrent.thread

/**
 * WifiReceiver — Lắng nghe các Broadcast Intent cấu hình Wi-Fi từ ADB hoặc hệ thống
 * Ví dụ:
 * adb shell am broadcast -a com.phicomm.gemini.SET_WIFI --es ssid "TênWiFi" --es password "MậtKhẩu"
 * adb shell am broadcast -a com.phicomm.speaker.SET_WIFI --es ssid "TênWiFi" --es password "MậtKhẩu"
 */
class WifiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WifiReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Đã nhận Broadcast Intent: $action")

        if (action == "com.phicomm.gemini.SET_WIFI" ||
            action == "com.phicomm.speaker.SET_WIFI" ||
            action == "com.phicomm.speaker.ACTION_WIFI_SET") {

            val ssid = intent.getStringExtra("ssid")
                ?: intent.getStringExtra("SSID")
                ?: ""
            val password = intent.getStringExtra("password")
                ?: intent.getStringExtra("pass")
                ?: intent.getStringExtra("key")
                ?: ""
            val passwordType = intent.getStringExtra("password_type")
                ?: intent.getStringExtra("type")

            if (ssid.isNotBlank()) {
                Log.d(TAG, "Nhận được yêu cầu kết nối WiFi từ Broadcast: SSID='$ssid'")
                val pendingResult = goAsync()
                thread {
                    try {
                        val helper = WifiSetupHelper(context)
                        val (success, msg) = helper.connectToWifi(ssid, password, passwordType)
                        Log.d(TAG, "Kết quả Broadcast kết nối WiFi: success=$success, msg=$msg")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Lỗi xử lý WifiReceiver: ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                Log.w(TAG, "Broadcast Intent $action thiếu tham số extra 'ssid'")
            }
        }
    }
}
