package com.phicomm.r1.control.voice

import android.os.Process
import android.util.Log
import cn.yunzhisheng.asrfix.JniAsrFix
import com.phicomm.r1.control.audio.FourMicCaptureSource
import com.phicomm.r1.control.audio.PcmChannelSelector
import com.phicomm.r1.control.audio.PcmFrameAccumulator
import com.phicomm.r1.control.model.AudioDiagnosticsSnapshot
import com.phicomm.r1.control.model.AudioPipelineConfig
import com.phicomm.r1.control.model.MonoChannelStrategy
import com.phicomm.r1.control.model.VoiceEngineState
import com.phicomm.r1.control.model.WakeWordEvent
import com.phicomm.r1.control.opus.ConcentusOpusCodec
import com.phicomm.r1.control.opus.OpusCodec
import com.phicomm.r1.control.wakeup.UnisoundModelProvisioner
import com.phicomm.r1.control.wakeup.WakeWordDebouncer
import com.phicomm.r1.control.wakeup.WakeWordEngine
import com.phicomm.r1.control.wakeup.WakeWordResultParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class VoicePipelineCoordinator(
    private val appFilesDir: File,
    private val config: AudioPipelineConfig = AudioPipelineConfig(),
    private val captureSource: FourMicCaptureSource,
    private val modelProvisioner: UnisoundModelProvisioner,
    private val wakeWordEngine: WakeWordEngine,
    private val opusCodec: OpusCodec = ConcentusOpusCodec(
        sampleRateHz = config.sampleRateHz,
        channels = 1,
        frameDurationMs = config.opusFrameDurationMs,
        bitrateBps = config.opusBitrateBps,
        complexity = config.opusComplexity
    )
) : VoiceEngineController {

    private val _diagnostics = MutableStateFlow(AudioDiagnosticsSnapshot())
    override val diagnostics: StateFlow<AudioDiagnosticsSnapshot> = _diagnostics.asStateFlow()

    private val running = AtomicBoolean(false)
    private val playbackActive = AtomicBoolean(false)
    private val returnToListeningFlag = AtomicBoolean(false)

    @Volatile
    private var selectedChannel: MonoChannelStrategy = config.selectedMonoChannel

    @Volatile
    private var listener: VoicePipelineListener? = null

    @Volatile
    private var workerThread: Thread? = null

    private val debouncer = WakeWordDebouncer(config.debounceMs)
    private val frameAccumulator = PcmFrameAccumulator(config.opusPcmBytesPerFrame)

    override fun setPlaybackActive(active: Boolean) {
        playbackActive.set(active)
        Log.i(
            TAG,
            "Playback mode changed: active=$active, activeThreshold=${activeThreshold()}"
        )
    }

    fun setChannelStrategy(strategy: MonoChannelStrategy) {
        selectedChannel = strategy
        Log.i(TAG, "Mono channel strategy updated: $strategy")
    }

    override fun setListener(listener: VoicePipelineListener?) {
        this.listener = listener
    }

    override fun returnToWakeWordListening() {
        returnToListeningFlag.set(true)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "VoicePipelineCoordinator already running")
            return
        }
        val thread = Thread(::runPipelineLoop, "R1AudioCapture").apply {
            isDaemon = false
        }
        workerThread = thread
        thread.start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        Log.i(TAG, "Stopping VoicePipelineCoordinator...")
        workerThread?.interrupt()
        captureSource.stopAndClose()
        runCatching { workerThread?.join(3000L) }
        workerThread = null
        teardownNativeResources()
        _diagnostics.update { it.copy(state = VoiceEngineState.STOPPED) }
        Log.i(TAG, "VoicePipelineCoordinator stopped cleanly")
    }

    private fun runPipelineLoop() {
        runCatching {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        }

        var retryDelayMs = INITIAL_RETRY_DELAY_MS
        while (running.get()) {
            try {
                // 1. Provision stock models from /system/app/Unisound/Unisound.apk
                updateState(VoiceEngineState.EXTRACTING_MODELS, lastError = null)
                val provisioned = modelProvisioner.provision(appFilesDir).getOrThrow()
                Log.i(
                    MODEL_TAG,
                    "Models ready in ${provisioned.elapsedMs}ms (extracted=${provisioned.extractedCount}, dir=${provisioned.modelDirPathWithSlash})"
                )

                // 2. Initialize 4-mic HAL + offline wake-word engine
                updateState(VoiceEngineState.INITIALIZING_HAL, lastError = null)
                val boardVersion = captureSource.initialize(config.debugWavEnabled).getOrThrow()
                captureSource.openAndStart(config.captureChannelMode).getOrThrow()

                val modelId = wakeWordEngine
                    .initialize(provisioned.modelDirPathWithSlash, provisioned.grammarPathsCsv)
                    .getOrThrow()
                wakeWordEngine.startWakeup(modelId).getOrThrow()

                _diagnostics.update {
                    it.copy(
                        state = VoiceEngineState.LISTENING_WAKEWORD,
                        boardVersion = boardVersion,
                        asrFixVersion = wakeWordEngine.getVersion(),
                        lastError = null
                    )
                }
                Log.i(
                    TAG,
                    "Voice pipeline active: state=LISTENING_WAKEWORD, captureCh=${config.captureChannelMode}, strategy=$selectedChannel, idleThr=${config.idleThreshold}, playbackThr=${config.playbackThreshold}"
                )

                retryDelayMs = INITIAL_RETRY_DELAY_MS
                runCaptureAndDetectLoop(modelId)
            } catch (t: Throwable) {
                if (!running.get()) break
                val msg = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "Voice pipeline error: $msg", t)
                updateState(VoiceEngineState.ERROR, lastError = msg)
                teardownNativeResources()
                try {
                    Thread.sleep(retryDelayMs)
                } catch (_: InterruptedException) {
                    break
                }
                retryDelayMs = (retryDelayMs * 2L).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
        teardownNativeResources()
    }

    private fun runCaptureAndDetectLoop(modelId: Int) {
        val rawBuffer = ByteArray(config.readBufferBytes)
        var framesRead = 0L
        var streamingActive = false
        var streamingDeadlineMs = 0L

        while (running.get()) {
            val readLen = captureSource.read(rawBuffer, rawBuffer.size)
            if (readLen <= 0) {
                if (!running.get()) break
                Log.w(TAG, "4-mic read returned $readLen; retrying...")
                Thread.sleep(20L)
                continue
            }

            framesRead++
            val extracted = PcmChannelSelector.extract(
                rawPcm = rawBuffer,
                length = readLen,
                captureChannelMode = config.captureChannelMode,
                strategy = selectedChannel
            )
            val monoPcm = extracted.monoPcm

            if (framesRead == 1L || framesRead == 10L || framesRead % 80L == 0L) {
                _diagnostics.update {
                    it.copy(
                        channel0Rms = extracted.channel0Rms,
                        channel1Rms = extracted.channel1Rms,
                        framesCaptured = framesRead
                    )
                }
                if (framesRead <= 10L || framesRead % 160L == 0L) {
                    Log.d(
                        TAG,
                        "4-mic telemetry: frames=$framesRead, rawBytes=$readLen, monoBytes=${monoPcm.size}, ch0Rms=${extracted.channel0Rms}, ch1Rms=${extracted.channel1Rms}, strategy=$selectedChannel"
                    )
                }
            }

            if (monoPcm.isEmpty()) continue

            if (!streamingActive) {
                val recStatus = wakeWordEngine.feedAudio(monoPcm, monoPcm.size)
                if (recStatus == JniAsrFix.RESULT_READY) {
                    val nowMs = System.currentTimeMillis()
                    val rawResult = wakeWordEngine.fetchRawResult()
                    val threshold = activeThreshold()
                    val parsed = WakeWordResultParser.parse(rawResult, threshold)
                    val (uttStart, uttEnd) = wakeWordEngine.getUtteranceBoundsMs()

                    Log.i(
                        WAKE_TAG,
                        "Wake-word candidate: raw='${rawResult?.replace("\n", "\\n")}', parsed=$parsed, bounds=${uttStart}..${uttEnd}ms"
                    )

                    if (parsed != null && parsed.accepted && debouncer.tryAcquire(nowMs)) {
                        captureSource.setUtteranceTiming(0, (uttEnd - uttStart).coerceAtLeast(0))
                        captureSource.setWakeUpStatus(true)
                        val doa = captureSource.getDoaDegrees()
                        wakeWordEngine.stopSession()

                        val event = WakeWordEvent(
                            phrase = parsed.phrase,
                            confidenceScore = parsed.confidenceScore,
                            appliedThreshold = parsed.appliedThreshold,
                            doaDegrees = doa,
                            utteranceStartMs = uttStart,
                            utteranceEndMs = uttEnd,
                            timestampMs = nowMs
                        )
                        Log.i(
                            WAKE_TAG,
                            "WAKE-WORD ACCEPTED: phrase='${event.phrase}', score=${event.confidenceScore}, thr=${event.appliedThreshold}, doa=${event.doaDegrees}deg"
                        )

                        frameAccumulator.reset()
                        returnToListeningFlag.set(false)
                        streamingActive = true
                        streamingDeadlineMs = nowMs + config.postWakeCaptureWindowMs

                        _diagnostics.update {
                            it.copy(
                                state = VoiceEngineState.WOKEN_STREAMING,
                                wakeWordCount = it.wakeWordCount + 1L,
                                lastWakeWordEvent = event
                            )
                        }
                        listener?.onWakeWordDetected(event)
                    } else {
                        // Rejected low-confidence or debounced candidate: reset recognition cleanly
                        wakeWordEngine.stopSession()
                        wakeWordEngine.startWakeup(modelId).getOrThrow()
                    }
                } else if (recStatus < 0) {
                    Log.w(WAKE_TAG, "JniAsrFix.recognize returned error $recStatus; restarting wakeup session")
                    wakeWordEngine.cancelSession()
                    wakeWordEngine.startWakeup(modelId).getOrThrow()
                }
            } else {
                // WOKEN_STREAMING: accumulate exact 60 ms mono frames and encode to Opus
                frameAccumulator.append(monoPcm) { frame60ms ->
                    val t0 = System.nanoTime()
                    val opusPacket = opusCodec.encode(frame60ms)
                    val encodeUs = (System.nanoTime() - t0) / 1000L
                    _diagnostics.update { it.copy(lastOpusEncodeUs = encodeUs) }
                    listener?.onOpusFrameEncoded(opusPacket, encodeUs)
                }

                val nowMs = System.currentTimeMillis()
                if (returnToListeningFlag.getAndSet(false) || nowMs >= streamingDeadlineMs) {
                    Log.i(TAG, "Streaming window ended; returning to LISTENING_WAKEWORD")
                    streamingActive = false
                    frameAccumulator.reset()
                    captureSource.setWakeUpStatus(false)
                    wakeWordEngine.startWakeup(modelId).getOrThrow()
                    updateState(VoiceEngineState.LISTENING_WAKEWORD, lastError = null)
                    listener?.onStreamingWindowEnded()
                }
            }
        }
    }

    private fun activeThreshold(): Float =
        if (playbackActive.get()) config.playbackThreshold else config.idleThreshold

    private fun updateState(state: VoiceEngineState, lastError: String?) {
        _diagnostics.update { it.copy(state = state, lastError = lastError) }
    }

    private fun teardownNativeResources() {
        runCatching { wakeWordEngine.release() }
        runCatching { captureSource.release() }
    }

    companion object {
        private const val TAG = "R1VoiceEngine"
        private const val MODEL_TAG = "R1ModelProvisioner"
        private const val WAKE_TAG = "R1WakeWord"
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 10000L
    }
}
