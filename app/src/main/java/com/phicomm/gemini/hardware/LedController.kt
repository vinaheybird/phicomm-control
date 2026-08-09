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
        Thread {
            try {
                // Kết nối tới LedDaemon chạy ở port 8081 với quyền shell
                val socket = java.net.Socket("127.0.0.1", 8081)
                val out = socket.getOutputStream()
                out.write("$code\n".toByteArray())
                out.flush()
                socket.close()
                Log.d(TAG, "Đã gửi lệnh $code tới LedDaemon")
            } catch (e: Exception) {
                Log.e(TAG, "Không thể kết nối LedDaemon (port 8081). Vui lòng đảm bảo daemon đang chạy: ${e.message}")
            }
        }.start()
    }
}
