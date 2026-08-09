package com.phicomm.gemini.hardware

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object LedController {
    private const val TAG = "LedController"
    private const val PREF_NAME = "phicomm_led_prefs"
    private const val KEY_LED_ENABLED = "led_enabled"
    private const val KEY_LED_MODE = "led_mode"

    enum class LedMode(val lightsTestCode: Int) {
        OFF(504),           // Tắt hoàn toàn (Thực tế lệnh tắt là 504 và 505)
        CYAN_PULSE(514),    // Xanh nhấp nháy êm ái (Breathe)
        BLUE_SOLID(502),    // Xanh lam tĩnh
        RED_SOLID(500),     // Đỏ tĩnh
        GREEN_SOLID(501)    // Xanh lá tĩnh
    }

    private var currentMode = LedMode.BLUE_SOLID
    private var isLedEnabled = true
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isLedEnabled = prefs?.getBoolean(KEY_LED_ENABLED, true) ?: true
        val savedModeName = prefs?.getString(KEY_LED_MODE, LedMode.BLUE_SOLID.name) ?: LedMode.BLUE_SOLID.name
        currentMode = try {
            LedMode.valueOf(savedModeName)
        } catch (e: Exception) {
            LedMode.BLUE_SOLID
        }

        applyCurrentState()
    }

    fun isEnabled(): Boolean = isLedEnabled

    fun setLedEnabled(enabled: Boolean) {
        isLedEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_LED_ENABLED, enabled)?.apply()
        applyCurrentState()
    }

    fun toggleLed(): Boolean {
        setLedEnabled(!isLedEnabled)
        return isLedEnabled
    }

    fun setMode(mode: LedMode) {
        currentMode = mode
        prefs?.edit()?.putString(KEY_LED_MODE, mode.name)?.apply()
        if (isLedEnabled) {
            applyCurrentState()
        }
    }

    fun getCurrentMode(): LedMode = currentMode

    private fun applyCurrentState() {
        if (!isLedEnabled || currentMode == LedMode.OFF) {
            Log.d(TAG, "Đã TẮT đèn LED")
            runLightsTest(504)
            runLightsTest(505)
            return
        }

        Log.d(TAG, "Áp dụng chế độ LED: $currentMode (Code: ${currentMode.lightsTestCode})")
        runLightsTest(currentMode.lightsTestCode)
    }

    private fun runLightsTest(code: Int) {
        try {
            appContext?.let { context ->
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                
                // Map lights_test code to ARGB
                val color = when (code) {
                    502 -> android.graphics.Color.BLUE
                    500 -> android.graphics.Color.RED
                    501 -> android.graphics.Color.GREEN
                    514 -> android.graphics.Color.CYAN
                    504, 505 -> android.graphics.Color.TRANSPARENT
                    else -> android.graphics.Color.BLUE
                }
                
                if (color == android.graphics.Color.TRANSPARENT) {
                    notificationManager.cancel(1001)
                    Log.d(TAG, "Canceled LED Notification")
                } else {
                    val builder = android.app.Notification.Builder(context)
                        .setSmallIcon(android.R.drawable.ic_dialog_info) // Must have icon
                        .setContentTitle("LED")
                        .setContentText("LED Color")
                        .setPriority(android.app.Notification.PRIORITY_MAX)
                    
                    if (code == 514) { // Pulse
                        builder.setLights(color, 1000, 1000)
                    } else { // Solid
                        builder.setLights(color, 1, 0)
                    }
                    
                    val notification = builder.build()
                    // Add FLAG_SHOW_LIGHTS explicitly
                    notification.flags = notification.flags or android.app.Notification.FLAG_SHOW_LIGHTS
                    
                    notificationManager.notify(1001, notification)
                    Log.d(TAG, "Sent LED Notification with color: $color (Code: $code)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi Notification LED: ${e.message}", e)
        }
    }
}
