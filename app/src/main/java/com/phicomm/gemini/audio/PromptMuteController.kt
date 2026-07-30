package com.phicomm.gemini.audio

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

object PromptMuteController {
    private const val TAG = "PromptMuteController"
    private const val PREF_NAME = "phicomm_prompt_prefs"
    private const val KEY_MUTE_ENABLED = "prompt_mute_enabled"

    private var context: Context? = null
    private var audioManager: AudioManager? = null
    private var prefs: SharedPreferences? = null
    private var isMutedTemp = false
    private val handler = Handler(Looper.getMainLooper())

    private val originalVolumes = mutableMapOf<Int, Int>()
    private val STREAMS_TO_MUTE = intArrayOf(
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_ALARM
    )

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isPromptMuteEnabled()) return

            val action = intent?.action ?: return
            Log.d(TAG, "Nhận được sự thay đổi Bluetooth Action: $action")

            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    muteSystemPromptsTemporarily(durationMs = 4000)
                }
            }
        }
    }

    fun init(ctx: Context) {
        context = ctx.applicationContext
        audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        prefs = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        try {
            context?.registerReceiver(btStateReceiver, filter)
            Log.d(TAG, "Đã đăng ký PromptMuteController receiver.")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng ký Receiver: ${e.message}", e)
        }
    }

    fun isPromptMuteEnabled(): Boolean {
        return prefs?.getBoolean(KEY_MUTE_ENABLED, true) ?: true
    }

    fun setPromptMuteEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_MUTE_ENABLED, enabled)?.apply()
    }

    fun muteSystemPromptsTemporarily(durationMs: Long = 4000) {
        val am = audioManager ?: return
        if (isMutedTemp) return

        Log.d(TAG, "=== Đang MUTE âm thanh thông báo Bluetooth trong ${durationMs}ms ===")
        isMutedTemp = true

        try {
            // Lưu lại mức âm lượng gốc
            for (stream in STREAMS_TO_MUTE) {
                originalVolumes[stream] = am.getStreamVolume(stream)
                am.setStreamVolume(stream, 0, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi setStreamVolume(0): ${e.message}")
        }

        // Hẹn giờ khôi phục lại âm lượng sau durationMs
        handler.postDelayed({
            restoreVolumes()
        }, durationMs)
    }

    private fun restoreVolumes() {
        val am = audioManager ?: return
        if (!isMutedTemp) return

        Log.d(TAG, "=== Khôi phục lại âm lượng loa ===")
        try {
            for (stream in STREAMS_TO_MUTE) {
                val origVol = originalVolumes[stream] ?: 10
                am.setStreamVolume(stream, origVol, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khôi phục âm lượng: ${e.message}")
        } finally {
            isMutedTemp = false
        }
    }

    fun unregister() {
        try {
            context?.unregisterReceiver(btStateReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
