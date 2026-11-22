package com.phicomm.r1.control.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("R1VoiceEngine", "BOOT_COMPLETED received; starting VoiceEngineService")
            context.startService(Intent(context, VoiceEngineService::class.java))
        }
    }
}
