package com.phicomm.r1.control.model

enum class VoiceEngineState {
    STOPPED,
    EXTRACTING_MODELS,
    INITIALIZING_HAL,
    LISTENING_WAKEWORD,
    WOKEN_STREAMING,
    ERROR
}

enum class MonoChannelStrategy {
    PASSTHROUGH_MONO,
    CHANNEL_0,
    CHANNEL_1,
    AVERAGE_MIX
}

data class WakeWordEvent(
    val phrase: String,
    val confidenceScore: Float,
    val appliedThreshold: Float,
    val doaDegrees: Int,
    val utteranceStartMs: Int,
    val utteranceEndMs: Int,
    val timestampMs: Long
) {
    init {
        require(phrase.isNotBlank()) { "WakeWordEvent.phrase must not be blank" }
        require(confidenceScore.isFinite()) { "WakeWordEvent.confidenceScore must be finite" }
        require(appliedThreshold.isFinite()) { "WakeWordEvent.appliedThreshold must be finite" }
        require(confidenceScore >= appliedThreshold) {
            "WakeWordEvent.confidenceScore ($confidenceScore) must be >= appliedThreshold ($appliedThreshold)"
        }
        require(doaDegrees in -1..359) { "WakeWordEvent.doaDegrees ($doaDegrees) must be in -1..359" }
        require(utteranceStartMs >= 0) { "WakeWordEvent.utteranceStartMs must be >= 0" }
        require(utteranceEndMs >= utteranceStartMs) {
            "WakeWordEvent.utteranceEndMs ($utteranceEndMs) must be >= utteranceStartMs ($utteranceStartMs)"
        }
        require(timestampMs > 0L) { "WakeWordEvent.timestampMs must be > 0" }
    }
}

data class AudioPipelineConfig(
    val sampleRateHz: Int = 16000,
    val captureChannelMode: Int = 2,
    val selectedMonoChannel: MonoChannelStrategy = MonoChannelStrategy.CHANNEL_0,
    val readChunkMonoBytes: Int = 1200,
    val idleThreshold: Float = -9.1f,
    val playbackThreshold: Float = -11.3f,
    val debounceMs: Long = 1500L,
    val postWakeCaptureWindowMs: Long = 3000L,
    val opusFrameDurationMs: Int = 60,
    val opusBitrateBps: Int = 24000,
    val opusComplexity: Int = 2,
    val debugWavEnabled: Boolean = false
) {
    init {
        require(sampleRateHz == 16000) { "AudioPipelineConfig.sampleRateHz must equal 16000" }
        require(captureChannelMode == 1 || captureChannelMode == 2) {
            "AudioPipelineConfig.captureChannelMode must be 1 or 2"
        }
        require(readChunkMonoBytes in 320..6400 && readChunkMonoBytes % 2 == 0) {
            "AudioPipelineConfig.readChunkMonoBytes must be an even integer in 320..6400"
        }
        require(idleThreshold.isFinite() && idleThreshold in -20.0f..10.0f) {
            "AudioPipelineConfig.idleThreshold must be in -20.0..10.0"
        }
        require(playbackThreshold.isFinite() && playbackThreshold in -20.0f..10.0f) {
            "AudioPipelineConfig.playbackThreshold must be in -20.0..10.0"
        }
        require(debounceMs in 0L..10000L) { "AudioPipelineConfig.debounceMs must be in 0..10000" }
        require(postWakeCaptureWindowMs in 500L..30000L) {
            "AudioPipelineConfig.postWakeCaptureWindowMs must be in 500..30000"
        }
        require(opusFrameDurationMs == 20 || opusFrameDurationMs == 40 || opusFrameDurationMs == 60) {
            "AudioPipelineConfig.opusFrameDurationMs must be 20, 40, or 60"
        }
        require(opusBitrateBps in 8000..64000) {
            "AudioPipelineConfig.opusBitrateBps must be in 8000..64000"
        }
        require(opusComplexity in 0..10) {
            "AudioPipelineConfig.opusComplexity must be in 0..10"
        }
    }

    val readBufferBytes: Int
        get() = readChunkMonoBytes * captureChannelMode

    val opusSamplesPerFrame: Int
        get() = (sampleRateHz * opusFrameDurationMs) / 1000

    val opusPcmBytesPerFrame: Int
        get() = opusSamplesPerFrame * 2
}

data class ModelAssetEntry(
    val zipEntryPath: String,
    val outputFileName: String,
    val requireCrcCheck: Boolean = true
) {
    init {
        require(zipEntryPath.isNotBlank()) { "ModelAssetEntry.zipEntryPath must not be blank" }
        require(outputFileName.isNotBlank()) { "ModelAssetEntry.outputFileName must not be blank" }
    }
}

data class ModelAssetManifest(
    val sourceApkPath: String = DEFAULT_UNISOUND_APK_PATH,
    val targetDirName: String = DEFAULT_MODEL_DIR_NAME,
    val versionEntryPath: String = DEFAULT_VERSION_ENTRY_PATH,
    val entries: List<ModelAssetEntry> = DEFAULT_ENTRIES
) {
    init {
        require(sourceApkPath.isNotBlank()) { "ModelAssetManifest.sourceApkPath must not be blank" }
        require(targetDirName.isNotBlank()) { "ModelAssetManifest.targetDirName must not be blank" }
        require(versionEntryPath.isNotBlank()) { "ModelAssetManifest.versionEntryPath must not be blank" }
        require(entries.isNotEmpty()) { "ModelAssetManifest.entries must not be empty" }
    }

    companion object {
        const val DEFAULT_UNISOUND_APK_PATH = "/system/app/Unisound/Unisound.apk"
        const val DEFAULT_MODEL_DIR_NAME = "asrfix_models"
        const val DEFAULT_VERSION_ENTRY_PATH = "assets/version/data"

        val DEFAULT_ENTRIES = listOf(
            // Stock Unisound.apk v3.10.77 keeps ab.h=false (native crcCheck is unused for this format).
            // Integrity is enforced via ZIP entry exact byte length + IEEE CRC-32 verification in UnisoundModelProvisioner.
            ModelAssetEntry("assets/asrfix/data", "asrfix.dat", requireCrcCheck = false),
            ModelAssetEntry("assets/wakeup/data", "wakeup.dat", requireCrcCheck = false),
            ModelAssetEntry("assets/net/data", "net.dat", requireCrcCheck = false)
        )
    }
}

data class AudioDiagnosticsSnapshot(
    val state: VoiceEngineState = VoiceEngineState.STOPPED,
    val boardVersion: String? = null,
    val asrFixVersion: String? = null,
    val channel0Rms: Int = 0,
    val channel1Rms: Int = 0,
    val framesCaptured: Long = 0L,
    val wakeWordCount: Long = 0L,
    val lastWakeWordEvent: WakeWordEvent? = null,
    val lastOpusEncodeUs: Long = 0L,
    val lastError: String? = null
) {
    init {
        require(channel0Rms >= 0) { "AudioDiagnosticsSnapshot.channel0Rms must be >= 0" }
        require(channel1Rms >= 0) { "AudioDiagnosticsSnapshot.channel1Rms must be >= 0" }
        require(framesCaptured >= 0L) { "AudioDiagnosticsSnapshot.framesCaptured must be >= 0" }
        require(wakeWordCount >= 0L) { "AudioDiagnosticsSnapshot.wakeWordCount must be >= 0" }
        require(lastOpusEncodeUs >= 0L) { "AudioDiagnosticsSnapshot.lastOpusEncodeUs must be >= 0" }
    }
}
