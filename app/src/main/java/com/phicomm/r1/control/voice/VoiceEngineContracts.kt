package com.phicomm.r1.control.voice

import com.phicomm.r1.control.model.AudioDiagnosticsSnapshot
import com.phicomm.r1.control.model.WakeWordEvent
import kotlinx.coroutines.flow.StateFlow

interface VoicePipelineListener {
    fun onWakeWordDetected(event: WakeWordEvent)
    fun onOpusFrameEncoded(opusPacket: ByteArray, encodeDurationUs: Long)
    fun onStreamingWindowEnded()
}

interface VoiceEngineController {
    val diagnostics: StateFlow<AudioDiagnosticsSnapshot>
    fun setPlaybackActive(active: Boolean)
    fun setListener(listener: VoicePipelineListener?)
    fun returnToWakeWordListening()
}
