package com.phicomm.r1.control.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.phicomm.r1.control.voice.VoiceEngineService

/**
 * Minimal headless launcher activity for starting [VoiceEngineService] on a screenless speaker.
 */
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, VoiceEngineService::class.java))
        finish()
    }
}
