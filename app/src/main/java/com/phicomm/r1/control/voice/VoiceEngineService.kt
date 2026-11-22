package com.phicomm.r1.control.voice

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.phicomm.r1.control.R
import com.phicomm.r1.control.audio.Uni4micCaptureSource
import com.phicomm.r1.control.model.AudioPipelineConfig
import com.phicomm.r1.control.model.MonoChannelStrategy
import com.phicomm.r1.control.model.WakeWordEvent
import com.phicomm.r1.control.opus.ConcentusOpusCodec
import com.phicomm.r1.control.wakeup.AsrFixWakeWordEngine
import com.phicomm.r1.control.wakeup.UnisoundModelProvisioner

class VoiceEngineService : Service() {

    inner class LocalBinder : Binder() {
        fun getController(): VoiceEngineController = coordinator
    }

    private val binder = LocalBinder()
    private lateinit var coordinator: VoicePipelineCoordinator
    private lateinit var opusCodec: ConcentusOpusCodec
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VoiceEngineService.onCreate")

        val config = AudioPipelineConfig()
        opusCodec = ConcentusOpusCodec(
            sampleRateHz = config.sampleRateHz,
            channels = 1,
            frameDurationMs = config.opusFrameDurationMs,
            bitrateBps = config.opusBitrateBps,
            complexity = config.opusComplexity
        )
        coordinator = VoicePipelineCoordinator(
            appFilesDir = filesDir,
            config = config,
            captureSource = Uni4micCaptureSource(),
            modelProvisioner = UnisoundModelProvisioner(),
            wakeWordEngine = AsrFixWakeWordEngine(),
            opusCodec = opusCodec
        )
        coordinator.setListener(object : VoicePipelineListener {
            private var streamFrames = 0

            override fun onWakeWordDetected(event: WakeWordEvent) {
                streamFrames = 0
                Log.i(
                    TAG,
                    "Listener onWakeWordDetected: phrase='${event.phrase}', score=${event.confidenceScore}, doa=${event.doaDegrees}deg"
                )
            }

            override fun onOpusFrameEncoded(opusPacket: ByteArray, encodeDurationUs: Long) {
                streamFrames++
                if (streamFrames == 1 || streamFrames % 10 == 0) {
                    Log.i(
                        OPUS_TAG,
                        "Encoded 60ms Opus frame #$streamFrames: bytes=${opusPacket.size}, encodeUs=$encodeDurationUs"
                    )
                }
            }

            override fun onStreamingWindowEnded() {
                Log.i(TAG, "Listener onStreamingWindowEnded (total Opus frames=$streamFrames)")
            }
        })

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        coordinator.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_PLAYBACK_MODE -> {
                val active = intent.getBooleanExtra(EXTRA_PLAYBACK, false)
                coordinator.setPlaybackActive(active)
            }
            ACTION_SELECT_CHANNEL -> {
                val channelName = intent.getStringExtra(EXTRA_CHANNEL)
                val strategy = runCatching {
                    MonoChannelStrategy.valueOf(channelName.orEmpty())
                }.getOrNull()
                if (strategy != null) {
                    coordinator.setChannelStrategy(strategy)
                } else {
                    Log.w(TAG, "Unknown channel strategy: $channelName")
                }
            }
            ACTION_RUN_OPUS_BENCHMARK -> {
                Thread({
                    val report = opusCodec.runBenchmark(50)
                    Log.i(
                        OPUS_TAG,
                        "Opus 60ms benchmark (${report.framesTested} frames): avgEncode=${report.avgEncodeUs}us (${report.avgEncodeUs / 1000.0}ms), maxEncode=${report.maxEncodeUs}us, avgDecode=${report.avgDecodeUs}us, avgPacketBytes=${report.avgPacketBytes}"
                    )
                }, "R1OpusBench").start()
            }
            ACTION_STOP -> {
                Log.i(TAG, "ACTION_STOP received; shutting down service")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(TAG, "VoiceEngineService.onDestroy")
        coordinator.stop()
        opusCodec.close()
        releaseWakeLock()
        stopForeground(true)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun buildForegroundNotification(): Notification {
        return Notification.Builder(this)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_listening))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhicommR1:VoiceEngine").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    companion object {
        private const val TAG = "R1VoiceEngine"
        private const val OPUS_TAG = "R1Opus"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_SET_PLAYBACK_MODE = "com.phicomm.r1.control.ACTION_SET_PLAYBACK_MODE"
        const val ACTION_SELECT_CHANNEL = "com.phicomm.r1.control.ACTION_SELECT_CHANNEL"
        const val ACTION_RUN_OPUS_BENCHMARK = "com.phicomm.r1.control.ACTION_RUN_OPUS_BENCHMARK"
        const val ACTION_STOP = "com.phicomm.r1.control.ACTION_STOP"

        const val EXTRA_PLAYBACK = "playback"
        const val EXTRA_CHANNEL = "channel"
    }
}
