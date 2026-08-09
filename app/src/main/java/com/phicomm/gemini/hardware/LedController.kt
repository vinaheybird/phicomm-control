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

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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
            Log.d(TAG, "Executing via ADB: lights_test show $code")
            // Đảm bảo kết nối ADB nội bộ
            Runtime.getRuntime().exec(arrayOf("/system/bin/adb", "connect", "127.0.0.1:5555")).waitFor()
            
            // Gọi lệnh thông qua adb shell để có quyền 'shell', lách luật SELinux untrusted_app
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/adb", "-s", "127.0.0.1:5555", "shell", "lights_test", "show", code.toString()))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            Log.d(TAG, "adb stdout: $stdout")
            if (stderr.isNotEmpty()) {
                Log.e(TAG, "adb stderr: $stderr")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi lights_test qua ADB: ${e.message}", e)
        }
    }
}
