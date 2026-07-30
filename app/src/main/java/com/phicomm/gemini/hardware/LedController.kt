package com.phicomm.gemini.hardware

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

object LedController {
    private const val TAG = "LedController"
    private const val PREF_NAME = "phicomm_led_prefs"
    private const val KEY_LED_ENABLED = "led_enabled"
    private const val KEY_LED_MODE = "led_mode"

    enum class LedMode {
        OFF,           // Tắt hoàn toàn (Tiết kiệm điện)
        CYAN_PULSE,    // Xanh chớp nhẹ
        BLUE_SOLID,    // Xanh lam dịu
        ORANGE_SOLID,  // Vàng/Cam
        GREEN_SOLID    // Xanh lá
    }

    private var currentMode = LedMode.BLUE_SOLID
    private var isLedEnabled = true
    private var isBlinking = false
    private var blinkThread: Thread? = null
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
        stopBlinkThread()
        if (!isLedEnabled || currentMode == LedMode.OFF) {
            setRgb(0, 0, 0)
            Log.d(TAG, "Đã TẮT đèn LED")
            return
        }

        Log.d(TAG, "Áp dụng chế độ LED: $currentMode")
        when (currentMode) {
            LedMode.OFF -> setRgb(0, 0, 0)
            LedMode.BLUE_SOLID -> setRgb(0, 50, 200)
            LedMode.CYAN_PULSE -> startBlink(0, 180, 255, 600)
            LedMode.ORANGE_SOLID -> setRgb(255, 120, 0)
            LedMode.GREEN_SOLID -> setRgb(0, 200, 50)
        }
    }

    private fun startBlink(r: Int, g: Int, b: Int, intervalMs: Long) {
        isBlinking = true
        blinkThread = Thread {
            var state = false
            while (isBlinking && !Thread.currentThread().isInterrupted) {
                if (state) {
                    setRgb(r, g, b)
                } else {
                    setRgb(r / 5, g / 5, b / 5)
                }
                state = !state
                try {
                    Thread.sleep(intervalMs)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply { start() }
    }

    private fun stopBlinkThread() {
        isBlinking = false
        blinkThread?.interrupt()
        blinkThread = null
    }

    private fun setRgb(r: Int, g: Int, b: Int) {
        try {
            // Thử ghi vào sysfs trực tiếp nếu có quyền root/system
            writeSysfs("/sys/class/leds/red/brightness", r)
            writeSysfs("/sys/class/leds/green/brightness", g)
            writeSysfs("/sys/class/leds/blue/brightness", b)

            // Hoặc ghi vào node điều khiển R1 tập trung nếu có
            writeSysfs("/sys/class/leds/r1_led/rgb", "$r $g $b")
        } catch (e: Exception) {
            // Silent fallback
        }
    }

    private fun writeSysfs(path: String, value: Any) {
        val file = File(path)
        if (file.exists()) {
            try {
                FileOutputStream(file).use { fos ->
                    fos.write(value.toString().toByteArray())
                }
            } catch (e: Exception) {
                // Ignore sysfs missing
            }
        }
    }
}
