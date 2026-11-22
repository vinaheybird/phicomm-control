package com.phicomm.r1.control.audio

import android.util.Log
import com.unisound.jni.Uni4micHalJNI

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

class Uni4micCaptureSource(
    private val halProvider: () -> Uni4micHalJNI = { Uni4micHalJNI.getInstance() }
) : FourMicCaptureSource {

    private var hal: Uni4micHalJNI? = null
    @Volatile
    private var audioHandle: Long = -1L

    override fun initialize(debugWavEnabled: Boolean): Result<String?> = runCatching {
        val instance = halProvider()
        hal = instance
        val initRc = instance.init(MODE_FOUR_MIC)
        Log.i(TAG, "Uni4micHalJNI.init($MODE_FOUR_MIC) = $initRc")
        if (initRc != 0) {
            error("Uni4micHalJNI.init(1) failed with code $initRc")
        }

        val debugFlag = if (debugWavEnabled) 1 else 0
        val debugRc = instance.set4MicDebugMode(debugFlag)
        val algoRc = instance.close4MicAlgorithm(0)
        val wakeRc = instance.set4MicWakeUpStatus(0)
        val boardVer = runCatching { instance.get4MicBoardVersion() }.getOrNull()
        Log.i(
            TAG,
            "4-mic configured: debugMode=$debugFlag(rc=$debugRc), algoEnabled(rc=$algoRc), wakeStatus=0(rc=$wakeRc), boardVersion=$boardVer"
        )
        boardVer
    }

    override fun openAndStart(channelMode: Int): Result<Unit> = runCatching {
        val instance = hal ?: error("Uni4micHalJNI not initialized")
        val handle = instance.openAudioIn(channelMode)
        Log.i(TAG, "Uni4micHalJNI.openAudioIn($channelMode) handle=$handle")
        if (handle <= 0L) {
            error("Uni4micHalJNI.openAudioIn($channelMode) returned invalid handle $handle (is stock Unisound still holding pcmC1D0c?)")
        }
        audioHandle = handle

        val startRc = instance.startRecorder(handle)
        Log.i(TAG, "Uni4micHalJNI.startRecorder($handle) = $startRc")
        if (startRc != 0) {
            instance.closeAudioIn(handle)
            audioHandle = -1L
            error("Uni4micHalJNI.startRecorder failed with code $startRc")
        }
    }

    override fun read(buffer: ByteArray, length: Int): Int {
        val instance = hal ?: return -1
        val handle = audioHandle
        if (handle <= 0L) return -1
        return instance.readData(handle, buffer, length)
    }

    override fun setWakeUpStatus(wokenUp: Boolean): Int {
        val instance = hal ?: return -1
        val status = if (wokenUp) 1 else 0
        return instance.set4MicWakeUpStatus(status)
    }

    override fun getDoaDegrees(): Int {
        val instance = hal ?: return -1
        val raw = instance.get4MicDoaResult()
        return if (raw in 0..359) raw else -1
    }

    override fun setUtteranceTiming(delayMs: Int, utteranceLenMs: Int) {
        val instance = hal ?: return
        if (delayMs >= 0) instance.set4MicDelayTime(delayMs)
        if (utteranceLenMs >= 0) instance.set4MicUtteranceTimeLen(utteranceLenMs)
    }

    override fun stopAndClose() {
        val instance = hal ?: return
        val handle = audioHandle
        if (handle > 0L) {
            audioHandle = -1L
            val stopRc = runCatching { instance.stopRecorder(handle) }.getOrDefault(-1)
            val closeRc = runCatching { instance.closeAudioIn(handle) }.getOrDefault(-1)
            Log.i(TAG, "4-mic audio closed: stopRecorder=$stopRc, closeAudioIn=$closeRc")
        }
    }

    override fun release() {
        stopAndClose()
        val instance = hal ?: return
        val releaseRc = runCatching { instance.release() }.getOrDefault(-1)
        Log.i(TAG, "Uni4micHalJNI.release() = $releaseRc")
        hal = null
    }

    companion object {
        private const val TAG = "R1FourMic"
        private const val MODE_FOUR_MIC = 1
    }
}
