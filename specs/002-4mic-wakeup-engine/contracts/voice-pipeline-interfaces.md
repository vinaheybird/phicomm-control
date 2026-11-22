# Internal Contracts & Module Interfaces: Feature `002-4mic-wakeup-engine`

**Feature**: `002-4mic-wakeup-engine`  
**Date**: 2026-11-22

This document defines the stable Kotlin interfaces and ADB diagnostic triggers exposed by Feature `002` for unit testing and downstream integration in Features `003` (Bluetooth Coexistence), `004` (Xiaozhi Cloud), and `005` (Basic Web Dashboard).

---

## 1. Audio Capture & Processing Interfaces

### 1.1 `FourMicCaptureSource`

Abstracts `com.unisound.jni.Uni4micHalJNI` for deterministic lifecycle management and testability.

```kotlin
package com.phicomm.r1.control.audio

interface FourMicCaptureSource {
    fun initialize(debugWavEnabled: Boolean = false): Result<String?>
    fun openAndStart(channelMode: Int): Result<Unit>
    fun read(buffer: ByteArray, length: Int): Int
    fun setWakeUpStatus(wokenUp: Boolean): Int
    fun getDoaDegrees(): Int
    fun setUtteranceTiming(delayMs: Int, utteranceLenMs: Int)
    fun stopAndClose()
    fun release()
}
```

### 1.2 `WakeWordEngine`

Abstracts `cn.yunzhisheng.asrfix.JniAsrFix` for offline `"Xiao Xun Xiao Xun"` detection.

```kotlin
package com.phicomm.r1.control.wakeup

interface WakeWordEngine {
    fun initialize(modelDirPathWithSlash: String, grammarPathsCsv: String): Result<Int>
    fun startWakeup(modelId: Int): Result<Unit>
    fun feedAudio(pcmBytes: ByteArray, length: Int): Int
    fun fetchRawResult(): String?
    fun getUtteranceBoundsMs(): Pair<Int, Int>
    fun stopSession()
    fun cancelSession()
    fun release()
    fun getVersion(): String?
}
```

### 1.3 `OpusCodec`

Bidirectional 16 kHz mono Opus codec interface used by Feature `002` (verification) and Feature `004` (Xiaozhi WebSocket transport).

```kotlin
package com.phicomm.r1.control.opus

interface OpusCodec : AutoCloseable {
    val sampleRateHz: Int
    val channels: Int
    val frameDurationMs: Int
    val samplesPerFrame: Int
    val pcmBytesPerFrame: Int

    /** Encodes exactly [pcmBytesPerFrame] bytes (16-bit LE mono PCM) into a single Opus packet. */
    fun encode(pcm16Le: ByteArray): ByteArray

    /** Decodes a single Opus packet into [pcmBytesPerFrame] bytes (16-bit LE mono PCM). */
    fun decode(opusPacket: ByteArray): ByteArray
}
```

### 1.4 `VoicePipelineListener` & `VoiceEngineController`

Hook points consumed by Features `003`, `004`, and `005`:

```kotlin
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
```

---

## 2. ADB Diagnostic Broadcast Contract (On-Device Verification)

To verify Feature `002` cleanly on the headless Phicomm R1 over ADB (before the Web Dashboard exists in Feature `005`), `VoiceEngineService` accepts explicit local debug intents:

| Intent Action / Extra | Effect |
|---|---|
| `adb shell am startservice -n com.phicomm.r1.control/.voice.VoiceEngineService` | Starts the foreground voice engine service |
| `adb shell am startservice -n com.phicomm.r1.control/.voice.VoiceEngineService -a com.phicomm.r1.control.ACTION_SET_PLAYBACK_MODE --ez playback true` | Switches wake-word confidence threshold between idle (`-9.1f`) and playback (`-11.3f`) |
| `adb shell am startservice -n com.phicomm.r1.control/.voice.VoiceEngineService -a com.phicomm.r1.control.ACTION_SELECT_CHANNEL --es channel CHANNEL_0` | Switches runtime mono channel extraction strategy (`CHANNEL_0`, `CHANNEL_1`, `PASSTHROUGH_MONO`, `AVERAGE_MIX`) for empirical AEC verification |
| `adb shell am startservice -n com.phicomm.r1.control/.voice.VoiceEngineService -a com.phicomm.r1.control.ACTION_RUN_OPUS_BENCHMARK` | Runs a 50-frame 60 ms Opus encode/decode round-trip benchmark on-device and logs average/max ms to `logcat` (`R1VoiceEngine`) |
| `adb shell am startservice -n com.phicomm.r1.control/.voice.VoiceEngineService -a com.phicomm.r1.control.ACTION_STOP` | Performs clean HAL + `JniAsrFix` stop/close/release and stops foreground service |
