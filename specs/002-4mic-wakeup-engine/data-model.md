# Data Model: Feature `002-4mic-wakeup-engine`

**Feature**: `002-4mic-wakeup-engine`  
**Date**: 2026-11-22

---

## 1. Entities & Field Constraints

### 1.1 `VoiceEngineState` (Enum)

Represents the deterministic lifecycle state of `VoiceEngineService` and its audio pipeline.

| Value | Meaning |
|---|---|
| `STOPPED` | Service created or stopped; 4-mic HAL and wake-word engine released |
| `EXTRACTING_MODELS` | Provisioning and CRC-verifying stock model assets from `/system/app/Unisound/Unisound.apk` |
| `INITIALIZING_HAL` | Initializing `Uni4micHalJNI` and `JniAsrFix` |
| `LISTENING_WAKEWORD` | Actively capturing 4-mic AEC PCM and feeding `JniAsrFix` for `"Xiao Xun Xiao Xun"` |
| `WOKEN_STREAMING` | Wake-word accepted; beamformer locked (`set4MicWakeUpStatus(1)`) and outputting 60 ms mono PCM / Opus frames |
| `ERROR` | Unrecoverable or retry-pending error (e.g., `/dev/snd/pcmC1D0c` busy or missing system APK) |

**Allowed State Transitions**:
- `STOPPED` $\rightarrow$ `EXTRACTING_MODELS` $\rightarrow$ `INITIALIZING_HAL` $\rightarrow$ `LISTENING_WAKEWORD`
- `LISTENING_WAKEWORD` $\leftrightarrow$ `WOKEN_STREAMING`
- Any active state $\rightarrow$ `ERROR` $\rightarrow$ `INITIALIZING_HAL` (bounded retry) or `STOPPED`
- Any state $\rightarrow$ `STOPPED` (clean teardown)

---

### 1.2 `WakeWordEvent` (Immutable Data Class)

Emitted when `JniAsrFix` detects a wake-word passing both the active confidence threshold and the debounce filter.

| Field | Type | Constraints |
|---|---|---|
| `phrase` | `String` | Non-blank trimmed phrase parsed from `JniAsrFix.getResult()` |
| `confidenceScore` | `Float` | Finite float (typical range `-20.0f..20.0f`); MUST satisfy `confidenceScore >= appliedThreshold` |
| `appliedThreshold` | `Float` | Finite float matching either `AudioPipelineConfig.idleThreshold` or `AudioPipelineConfig.playbackThreshold` |
| `doaDegrees` | `Int` | Range `-1..359` (`0..359` valid angle from `Uni4micHalJNI.get4MicDoaResult()`, `-1` if unavailable) |
| `utteranceStartMs` | `Int` | `>= 0` (from `JniAsrFix.getOptionInt(101)`) |
| `utteranceEndMs` | `Int` | `>= utteranceStartMs` (from `JniAsrFix.getOptionInt(102)`) |
| `timestampMs` | `Long` | `> 0L` (epoch milliseconds when event was accepted) |

---

### 1.3 `AudioPipelineConfig` (Immutable Data Class)

Configuration for 4-mic capture, wake-word scoring, debounce, and Opus framing.

| Field | Type | Default | Constraints |
|---|---|---|---|
| `sampleRateHz` | `Int` | `16000` | MUST equal `16000` (required by `Uni4micHalJNI`, `JniAsrFix`, and Xiaozhi Opus) |
| `captureChannelMode` | `Int` | `2` | Allowed values: `1` (mono HAL) or `2` (stock 2-ch HAL `openAudioIn(2)`) |
| `selectedMonoChannel` | `MonoChannelStrategy` | `CHANNEL_0` | Enum: `PASSTHROUGH_MONO`, `CHANNEL_0`, `CHANNEL_1`, `AVERAGE_MIX` |
| `readChunkMonoBytes` | `Int` | `1200` | Must be positive even integer (`>= 320`, `<= 6400`; `1200` = 37.5 ms mono, `2400` bytes read when `captureChannelMode == 2`) |
| `idleThreshold` | `Float` | `-9.1f` | Finite float (`-20.0f..10.0f`); stock `wakeup_benchmark` |
| `playbackThreshold` | `Float` | `-11.3f` | Finite float (`-20.0f..10.0f`); stock `wakeup_benchmark_for_music_playing` |
| `debounceMs` | `Long` | `1500L` | `>= 0L`, `<= 10000L` |
| `postWakeCaptureWindowMs` | `Long` | `3000L` | `>= 500L`, `<= 30000L` (standalone Feature `002` diagnostic streaming window before returning to idle listening; overridden by Feature `004` session control) |
| `opusFrameDurationMs` | `Int` | `60` | Allowed RFC-6716 values for Xiaozhi: `20`, `40`, or `60` (`60 ms` = `960` samples / `1920` bytes) |
| `opusBitrateBps` | `Int` | `24000` | Range `8000..64000` |
| `opusComplexity` | `Int` | `2` | Range `0..10` (`2` optimized for RK3229 Cortex-A7) |
| `debugWavEnabled` | `Boolean` | `false` | MUST default to `false` (`set4MicDebugMode(0)`) to prevent filling `/sdcard/unidata/` |

---

### 1.4 `ModelAssetManifest` & `ModelAssetEntry`

Describes the stock Unisound model files extracted at runtime from `/system/app/Unisound/Unisound.apk`.

| Entity.Field | Type | Constraints |
|---|---|---|
| `ModelAssetManifest.sourceApkPath` | `String` | Non-blank absolute path; default `"/system/app/Unisound/Unisound.apk"` |
| `ModelAssetManifest.targetDirName` | `String` | Non-blank relative dir inside `context.filesDir`; default `"asrfix_models"` |
| `ModelAssetManifest.versionEntryPath` | `String` | Default `"assets/version/data"` |
| `ModelAssetManifest.entries` | `List<ModelAssetEntry>` | Non-empty; default 3 entries (`asrfix.dat`, `wakeup.dat`, `net.dat`) |
| `ModelAssetEntry.zipEntryPath` | `String` | Non-blank path inside source APK (e.g., `"assets/asrfix/data"`) |
| `ModelAssetEntry.outputFileName` | `String` | Non-blank filename (e.g., `"asrfix.dat"`, `"wakeup.dat"`, `"net.dat"`) |
| `ModelAssetEntry.requireCrcCheck` | `Boolean` | `true` for all `.dat` files; `false` for plain-text `version` |

---

### 1.5 `AudioDiagnosticsSnapshot` (Immutable Data Class)

Live telemetry snapshot published via `StateFlow<AudioDiagnosticsSnapshot>` for ADB inspection and Feature `005` dashboard consumption.

| Field | Type | Constraints |
|---|---|---|
| `state` | `VoiceEngineState` | Non-null current engine state |
| `boardVersion` | `String?` | Nullable hardware version string from `Uni4micHalJNI.get4MicBoardVersion()` |
| `asrFixVersion` | `String?` | Nullable engine version from `JniAsrFix.getVersion()` |
| `channel0Rms` | `Int` | `>= 0` (16-bit PCM RMS amplitude of channel 0) |
| `channel1Rms` | `Int` | `>= 0` (16-bit PCM RMS amplitude of channel 1; `0` in mono mode) |
| `framesCaptured` | `Long` | `>= 0L` |
| `wakeWordCount` | `Long` | `>= 0L` |
| `lastWakeWordEvent` | `WakeWordEvent?` | Nullable most recent accepted `WakeWordEvent` |
| `lastOpusEncodeUs` | `Long` | `>= 0L` (microseconds to encode last 60 ms Opus frame) |
| `lastError` | `String?` | Nullable human-readable error message when `state == ERROR` |
