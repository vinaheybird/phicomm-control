---

description: "Actionable, dependency-ordered task list for 4-Mic Hardware AEC Capture & Offline Wake-Word Engine"
---

# Tasks: 4-Mic Hardware AEC Capture & Offline Wake-Word Engine

**Input**: Design documents from `/specs/002-4mic-wakeup-engine/`
**Prerequisites**: [plan.md](file:///D:/AGY/phicomm-control/specs/002-4mic-wakeup-engine/plan.md), [spec.md](file:///D:/AGY/phicomm-control/specs/002-4mic-wakeup-engine/spec.md), [research.md](file:///D:/AGY/phicomm-control/specs/002-4mic-wakeup-engine/research.md), [data-model.md](file:///D:/AGY/phicomm-control/specs/002-4mic-wakeup-engine/data-model.md), [voice-pipeline-interfaces.md](file:///D:/AGY/phicomm-control/specs/002-4mic-wakeup-engine/contracts/voice-pipeline-interfaces.md), [quickstart.md](file:///D:/AGY/phicomm-control/specs/002-4mic-wakeup-engine/quickstart.md)

**Tests**: Automated JVM unit tests (`.\gradlew.bat testDebugUnitTest`) are required by FR-007 and Constitution Principle V for all pure-Kotlin audio/wake-word/Opus logic, plus live Phicomm R1 on-device verification via ADB (`192.168.88.66:5555`).

**Organization**: Tasks are grouped by Setup, Foundational, User Stories (`US1`–`US3`), Service Integration, and On-Device Verification & Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story mapping (`US1`, `US2`, `US3`)

---

## Phase 1: Setup (Single-APK Gradle Android Project Scaffold)

**Purpose**: Initialize the clean-slate single-module Android Gradle project (`com.phicomm.r1.control`, `minSdk 21`, `targetSdk 22`, `compileSdk 34`, `armeabi-v7a`) using Gradle `8.7`, AGP `8.5.2`, and Kotlin `2.0.21`.

- [x] T001 Create root Gradle build files (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties` pointing to local Android SDK) and generate Gradle `8.7` wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`)
- [x] T002 [P] Create `app/build.gradle.kts` configuring `applicationId = "com.phicomm.r1.control"`, `minSdk = 21`, `targetSdk = 22`, `compileSdk = 34`, `abiFilters += "armeabi-v7a"`, coroutines, `io.github.jaredmdobson:concentus:1.0.2`, and `junit:junit:4.13.2`
- [x] T003 [P] Create `app/proguard-rules.pro` (explicit `-keep` rules for `com.unisound.jni.Uni4micHalJNI` and `cn.yunzhisheng.asrfix.JniAsrFix`), `app/src/main/AndroidManifest.xml` (`RECORD_AUDIO`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VoiceEngineService`, `BootCompletedReceiver`, `LaunchActivity`), and `app/src/main/res/values/strings.xml`

**Checkpoint**: Gradle project structure is initialized and ready for Kotlin source compilation.

---

## Phase 2: Foundational (Core Domain Models & Pipeline Contracts)

**Purpose**: Define all immutable domain entities with validation rules and shared module interfaces required across US1, US2, and US3.

**⚠️ CRITICAL**: All user story implementations depend on these core models and contracts.

- [x] T004 Implement canonical domain models and validation checks in `app/src/main/java/com/phicomm/r1/control/model/VoiceModels.kt` enforcing constraints from `data-model.md` for `VoiceEngineState`, `WakeWordEvent.phrase`, `WakeWordEvent.confidenceScore`, `WakeWordEvent.appliedThreshold`, `WakeWordEvent.doaDegrees`, `WakeWordEvent.utteranceStartMs`, `WakeWordEvent.utteranceEndMs`, `WakeWordEvent.timestampMs`, `AudioPipelineConfig.sampleRateHz`, `AudioPipelineConfig.captureChannelMode`, `AudioPipelineConfig.selectedMonoChannel`, `AudioPipelineConfig.readChunkMonoBytes`, `AudioPipelineConfig.idleThreshold`, `AudioPipelineConfig.playbackThreshold`, `AudioPipelineConfig.debounceMs`, `AudioPipelineConfig.postWakeCaptureWindowMs`, `AudioPipelineConfig.opusFrameDurationMs`, `AudioPipelineConfig.opusBitrateBps`, `AudioPipelineConfig.opusComplexity`, `AudioPipelineConfig.debugWavEnabled`, `ModelAssetManifest.sourceApkPath`, `ModelAssetManifest.targetDirName`, `ModelAssetManifest.versionEntryPath`, `ModelAssetManifest.entries`, `ModelAssetEntry.zipEntryPath`, `ModelAssetEntry.outputFileName`, `ModelAssetEntry.requireCrcCheck`, `AudioDiagnosticsSnapshot.state`, `AudioDiagnosticsSnapshot.boardVersion`, `AudioDiagnosticsSnapshot.asrFixVersion`, `AudioDiagnosticsSnapshot.channel0Rms`, `AudioDiagnosticsSnapshot.channel1Rms`, `AudioDiagnosticsSnapshot.framesCaptured`, `AudioDiagnosticsSnapshot.wakeWordCount`, `AudioDiagnosticsSnapshot.lastWakeWordEvent`, `AudioDiagnosticsSnapshot.lastOpusEncodeUs`, and `AudioDiagnosticsSnapshot.lastError`
- [x] T005 [P] Define shared pipeline listener and controller contracts (`VoicePipelineListener`, `VoiceEngineController`) in `app/src/main/java/com/phicomm/r1/control/voice/VoiceEngineContracts.kt` matching `contracts/voice-pipeline-interfaces.md`

**Checkpoint**: Core models and interfaces compile cleanly; US1, US2, and US3 can proceed in parallel.

---

## Phase 3: User Story 1 - Continuous 4-Mic Far-Field Capture with Hardware AEC (Priority: P1) 🎯 MVP

**Goal**: Directly bind stock `/system/lib/libUni4micHalJNI.so`, capture 4-mic hardware-AEC audio with `set4MicDebugMode(0)`, extract mono PCM with per-channel RMS diagnostics, and support beam lock & DOA retrieval.

**Independent Test**: `PcmChannelSelectorTest` passes on JVM; live R1 ADB deployment confirms `Uni4micHalJNI.init(1)=0`, `openAudioIn(2)` valid handle, continuous non-zero PCM frames, and zero debug WAV writes to `/sdcard/unidata/`.

### Implementation & Tests for User Story 1

- [x] T006 [P] [US1] Implement exact stock JNI bridge in `app/src/main/java/com/unisound/jni/Uni4micHalJNI.kt` matching `docs/re/4mic-hal-contract.md` (loading `"Uni4micHalJNI"` from `/system/lib/`, all 18 native methods, synchronized singleton + init/release guard)
- [x] T007 [P] [US1] Implement `FourMicCaptureSource` interface and `Uni4micCaptureSource` lifecycle wrapper in `app/src/main/java/com/phicomm/r1/control/audio/FourMicCaptureSource.kt` (`init(1)`, `set4MicDebugMode(0)`, `close4MicAlgorithm(0)`, `set4MicWakeUpStatus(0/1)`, `openAudioIn(channelMode)`, `startRecorder`, `readData`, `get4MicDoaResult`, `stopRecorder`, `closeAudioIn`, `release`)
- [x] T008 [P] [US1] Implement pure-Kotlin `PcmChannelSelector` (stereo de-interleaving for `CHANNEL_0`, `CHANNEL_1`, `AVERAGE_MIX`, `PASSTHROUGH_MONO` + per-channel 16-bit RMS computation) in `app/src/main/java/com/phicomm/r1/control/audio/PcmChannelSelector.kt`
- [x] T009 [P] [US1] Write automated JVM unit tests for stereo/mono channel extraction and RMS diagnostics in `app/src/test/java/com/phicomm/r1/control/audio/PcmChannelSelectorTest.kt`

**Checkpoint**: 4-mic HAL bridge and mono channel extractor are implemented and unit-tested.

---

## Phase 4: User Story 2 - Offline "Xiao Xun Xiao Xun" Wake-Word Detection with Git-Clean Model Extraction (Priority: P1)

**Goal**: Extract stock `"Xiao Xun Xiao Xun"` acoustic/wakeup models at runtime from `/system/app/Unisound/Unisound.apk` (`0644`), validate CRCs via `JniAsrFix.crcCheck`, run offline recognition with stock idle/playback thresholds (`-9.1f` / `-11.3f`), and debounce duplicate triggers.

**Independent Test**: `WakeWordResultParserTest`, `WakeWordDebouncerTest`, and `UnisoundModelProvisionerTest` pass on JVM; live R1 ADB deployment extracts models in `< 10s`, passes CRC checks, and detects `"Xiao Xun Xiao Xun"` offline with confidence score and DOA angle.

### Implementation & Tests for User Story 2

- [x] T010 [P] [US2] Implement exact stock JNI bridge in `app/src/main/java/cn/yunzhisheng/asrfix/JniAsrFix.kt` matching `docs/re/wakeup-engine-contract.md` (loading `"asrfix"` from `/system/lib/`, all native methods, public wrappers for `init`, `start`, `recognize`, `getResult`, `stop`, `cancel`, `release`, `setOptionInt`, `getOptionInt`, `setOptionString`, `getVersion`, `checkCrc`)
- [x] T011 [P] [US2] Implement Git-clean `UnisoundModelProvisioner` in `app/src/main/java/com/phicomm/r1/control/wakeup/UnisoundModelProvisioner.kt` reading `ModelAssetManifest` from `ZipFile("/system/app/Unisound/Unisound.apk")` with atomic `.tmp` rename, version stamp caching, and pluggable CRC verifier (`JniAsrFix.checkCrc`)
- [x] T012 [P] [US2] Implement `WakeWordEngine` interface and `AsrFixWakeWordEngine` in `app/src/main/java/com/phicomm/r1/control/wakeup/WakeWordEngine.kt` applying verified post-init options (`getOptionInt(100)`, `setOptionInt(16, i)`, `0->1`, `1->3000`, `6->8`, `9->appKey`) and `start("wakeup", modelId)`
- [x] T013 [P] [US2] Implement pure-Kotlin `WakeWordResultParser` (newline-delimited phrase + float score extraction & idle/playback threshold comparison) in `app/src/main/java/com/phicomm/r1/control/wakeup/WakeWordResultParser.kt` and `WakeWordDebouncer` in `app/src/main/java/com/phicomm/r1/control/wakeup/WakeWordDebouncer.kt`
- [x] T014 [P] [US2] Write automated JVM unit tests in `app/src/test/java/com/phicomm/r1/control/wakeup/WakeWordResultParserTest.kt`, `app/src/test/java/com/phicomm/r1/control/wakeup/WakeWordDebouncerTest.kt`, and `app/src/test/java/com/phicomm/r1/control/wakeup/UnisoundModelProvisionerTest.kt` (using temporary synthetic ZIP archives)

**Checkpoint**: Offline wake-word engine, runtime model extractor, result parser, and debouncer are implemented and unit-tested.

---

## Phase 5: User Story 3 - Deterministic 16 kHz Mono Opus Framing Pipeline (Priority: P2)

**Goal**: Accumulate arbitrary-sized mono 16 kHz 16-bit PCM chunks into exact 60 ms (`960`-sample / `1,920`-byte) frames and encode/decode RFC-6716 Opus packets via `Concentus` behind a clean `OpusCodec` interface.

**Independent Test**: `PcmFrameAccumulatorTest` and `ConcentusOpusCodecTest` pass on JVM; on-device `ACTION_RUN_OPUS_BENCHMARK` logs `< 20 ms/frame` average encode latency on RK3229.

### Implementation & Tests for User Story 3

- [x] T015 [P] [US3] Implement pure-Kotlin `PcmFrameAccumulator` in `app/src/main/java/com/phicomm/r1/control/audio/PcmFrameAccumulator.kt` emitting exact `frameBytes` (`1,920` bytes = 60 ms @ 16 kHz mono 16-bit) chunks across arbitrary upstream read sizes with zero sample loss
- [x] T016 [P] [US3] Implement `OpusCodec` interface in `app/src/main/java/com/phicomm/r1/control/opus/OpusCodec.kt` and `ConcentusOpusCodec` (16 kHz mono VOIP, 60 ms frames, complexity `2`, bitrate `24 kbps`, plus on-device round-trip benchmark helper) in `app/src/main/java/com/phicomm/r1/control/opus/ConcentusOpusCodec.kt`
- [x] T017 [P] [US3] Write automated JVM unit tests in `app/src/test/java/com/phicomm/r1/control/audio/PcmFrameAccumulatorTest.kt` and `app/src/test/java/com/phicomm/r1/control/opus/ConcentusOpusCodecTest.kt`

**Checkpoint**: PCM framing and Opus encode/decode pipeline compile and pass all JVM unit tests.

---

## Phase 6: Pipeline Integration & Persistent Foreground Service

**Purpose**: Wire US1 (4-mic HAL + channel selector), US2 (model provisioner + `AsrFixWakeWordEngine` + parser/debouncer), and US3 (`PcmFrameAccumulator` + `ConcentusOpusCodec`) into a boot-started foreground service with bounded error retry and ADB diagnostic controls.

- [x] T018 Implement `VoicePipelineCoordinator` in `app/src/main/java/com/phicomm/r1/control/voice/VoicePipelineCoordinator.kt` running the dedicated `THREAD_PRIORITY_URGENT_AUDIO` capture loop, managing `VoiceEngineState` transitions, wake-word beam lock (`set4MicWakeUpStatus(1)` + `get4MicDoaResult()`), post-wake 60 ms Opus streaming window, clean restart back to `LISTENING_WAKEWORD`, and `StateFlow<AudioDiagnosticsSnapshot>` publication
- [x] T019 Implement persistent foreground `VoiceEngineService` (handling `ACTION_SET_PLAYBACK_MODE`, `ACTION_SELECT_CHANNEL`, `ACTION_RUN_OPUS_BENCHMARK`, `ACTION_STOP` per `contracts/voice-pipeline-interfaces.md`, partial `WakeLock`, and clean teardown) in `app/src/main/java/com/phicomm/r1/control/voice/VoiceEngineService.kt`
- [x] T020 [P] Implement `BootCompletedReceiver` in `app/src/main/java/com/phicomm/r1/control/voice/BootCompletedReceiver.kt` and minimal headless `LaunchActivity` in `app/src/main/java/com/phicomm/r1/control/ui/LaunchActivity.kt`

**Checkpoint**: Complete standalone APK builds cleanly and wires all Feature `002` user stories end-to-end.

---

## Phase 7: Verification, On-Device Testing & Polish

**Purpose**: Verify automated JVM unit tests, APK contents, live Phicomm R1 hardware capture/wake-word/Opus performance, and memory budget (`< 80 MB`).

- [x] T021 Run `.\gradlew.bat testDebugUnitTest assembleDebug`, verify 100% JVM unit test pass rate and confirm APK contains only `armeabi-v7a` and zero proprietary `.dat`/stock `.so` assets
- [ ] T022 Deploy `app-debug.apk` to live Phicomm R1 (`192.168.88.66:5555`) after non-destructive `pm hide com.phicomm.speaker.device`, verify cold & warm model extraction (`< 10s` / `< 2s`), CRC check pass, 4-mic hardware AEC RMS on both channels, `JniAsrFix` listening state, on-device 60 ms Opus encode benchmark (`< 20 ms/frame`), clean stop/restart, and `dumpsys meminfo` `< 80 MB` PSS *(Blocked on live R1 SELinux `Enforcing` policy denying `u:r:untrusted_app:s0` access to `/dev/snd` `audio_device` — awaiting user decision on SELinux/device architecture)*
- [ ] T023 Update `README.md` roadmap status for `002-4mic-wakeup-engine` and verify `git status` is clean of any build artifacts or proprietary files

---

## Dependencies & Execution Order

- **Phase 1 (Setup)**: `T001` first, then `T002` || `T003` in parallel.
- **Phase 2 (Foundational)**: `T004` || `T005` in parallel (blocks US1–US3).
- **Phases 3–5 (US1, US2, US3)**: All tasks (`T006`–`T017`) target independent packages/files and can execute in parallel!
- **Phase 6 (Integration)**: `T018` depends on US1–US3; `T019` depends on `T018`; `T020` runs in parallel with `T019`.
- **Phase 7 (Verification)**: `T021` (JVM tests + APK build) $\rightarrow$ `T022` (live Phicomm R1 ADB verification) $\rightarrow$ `T023` (roadmap & Git hygiene check).
