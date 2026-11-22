# Implementation Plan: 4-Mic Hardware AEC Capture & Offline Wake-Word Engine

**Branch**: `002-4mic-wakeup-engine` | **Date**: 2026-11-22 | **Spec**: [spec.md](file:///D:/AGY/phicomm-control/specs/002-4mic-wakeup-engine/spec.md)  
**Input**: Feature specification from `/specs/002-4mic-wakeup-engine/spec.md` and empirically verified RE contracts under `docs/re/`.

## Summary

Scaffold the clean-slate standalone Android application (`com.phicomm.r1.control`, `minSdk 21`, `targetSdk 22`, `armeabi-v7a`) and implement its core audio foundation:
1. Native 4-microphone hardware AEC capture via the stock `/system/lib/libUni4micHalJNI.so` bridge (`com.unisound.jni.Uni4micHalJNI`),
2. Git-clean runtime provisioning of the stock `"Xiao Xun Xiao Xun"` acoustic/wakeup models from `/system/app/Unisound/Unisound.apk` (`0644`) with CRC verification,
3. Continuous offline wake-word detection via `/system/lib/libasrfix.so` (`cn.yunzhisheng.asrfix.JniAsrFix`) with stock idle/playback thresholds (`-9.1f` / `-11.3f`), debounce, and DOA beam locking,
4. Deterministic 16 kHz mono PCM channel selection, 60 ms (`960`-sample / `1,920`-byte) frame accumulation, and bidirectional RFC-6716 Opus framing (`Concentus`) behind a clean `OpusCodec` interface, all orchestrated by a lightweight boot-started foreground `VoiceEngineService`.

## Technical Context

**Language/Version**: Kotlin `2.0.21` (JVM 17 toolchain), Gradle `8.7`, Android Gradle Plugin `8.5.2`  
**Primary Dependencies**:
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1` & `kotlinx-coroutines-android:1.8.1`
- `io.github.jaredmdobson:concentus:1.0.2` (pure-JVM/Android RFC-6716 Opus codec, zero NDK required)
- `junit:junit:4.13.2` (JVM unit tests)
- Stock on-device native libraries loaded dynamically from `/system/lib/`: `libUni4micHalJNI.so`, `libasrfix.so` (never bundled in APK)  
**Storage**: App-private `context.filesDir/asrfix_models/` for runtime-extracted `.dat` models (atomic write + CRC check)  
**Testing**: Automated JVM unit tests (`.\gradlew.bat testDebugUnitTest`) + live on-device verification on Phicomm R1 (`192.168.88.66:5555`) via ADB  
**Target Platform**: Phicomm R1 (`rk322x-box`, Rockchip RK3229 Cortex-A7, `armeabi-v7a`, Android `5.1.1` / API `22`, `512 MB` RAM)  
**Project Type**: Single-module headless Android application (`:app`)  
**Performance Goals**: Idle wake-word RSS/PSS `< 80 MB`; cold model extraction `< 10 s`, warm restart `< 2 s`; wake-word detection latency `< 500 ms`; 60 ms Opus encode `< 20 ms/frame` on RK3229  
**Constraints**: Zero proprietary binaries/models in Git; zero `AudioRecord` fallback for primary voice capture; `set4MicDebugMode(0)` by default to avoid filling flash storage; non-destructive `pm hide` only  
**Scale/Scope**: Core audio + wake-word + Opus foundation for 1 standalone speaker APK (~15 Kotlin source files + 5 unit test suites)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance Check | Status |
|---|---|---|
| **I. Spec-First Development (SDD)** | Active SpecKit feature `002-4mic-wakeup-engine` with completed `spec.md`, passing `checklists/requirements.md`, and complete `plan.md`/`research.md`/`data-model.md`/`contracts/` before any production code is written. All artifacts in English. | PASS |
| **II. Evidence-Based Reverse Engineering (RE-First)** | Every JNI signature (`Uni4micHalJNI`, `JniAsrFix`), option ID, threshold (`-9.1f`/`-11.3f`), asset path (`assets/asrfix/data`, `assets/wakeup/data`, `assets/net/data`), and hardware config cites verified contracts in `docs/re/4mic-hal-contract.md` and `docs/re/wakeup-engine-contract.md`. Zero proprietary files in Git. | PASS |
| **III. Single-APK, Hardware-Native Efficiency** | Single standalone APK (`minSdk 21`, `armeabi-v7a`), zero UI/DI bloat, direct 4-mic hardware AEC via `Uni4micHalJNI` + stock offline `"Xiao Xun Xiao Xun"` via `libasrfix.so`, keeping idle RSS well under `80 MB`. | PASS |
| **IV. Deterministic Audio & Bluetooth Coexistence** | Exposes clean `VoiceEngineController.setPlaybackActive(Boolean)` and `VoicePipelineListener` hooks so Feature `003` can switch wake-word thresholds and arbitrate Bluetooth A2DP/AVRCP deterministically without re-architecting the audio loop. | PASS |
| **V. Testability, Observability & Clean Hygiene** | Pure-Kotlin wake-word parser, debouncer, PCM channel selector, 60 ms frame accumulator, and Opus round-trip codec have automated JVM unit tests; `VoiceEngineService` publishes `StateFlow<AudioDiagnosticsSnapshot>` + structured ADB logcat metrics; non-destructive `pm hide` only. | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/002-4mic-wakeup-engine/
├── plan.md                                 # This file
├── research.md                             # Toolchain, JNI, asset extraction, and Opus decisions
├── data-model.md                           # Entities, state transitions, and field constraints
├── quickstart.md                           # Build, unit test, ADB deployment, and live R1 verification
├── checklists/
│   └── requirements.md                     # Passed specification quality checklist
└── contracts/
    └── voice-pipeline-interfaces.md        # Kotlin module contracts & ADB diagnostic intents
```

### Source Code (repository root)

```text
build.gradle.kts                            # Root Gradle config (AGP 8.5.2, Kotlin 2.0.21)
settings.gradle.kts                         # Single-module :app configuration
gradle.properties                           # JVM args, AndroidX disabled/minimal, Kotlin style
gradle/wrapper/
├── gradle-wrapper.jar                      # Standard Gradle 8.7 wrapper
└── gradle-wrapper.properties               # Points to cached Gradle 8.7 distribution
gradlew / gradlew.bat                       # Wrapper entrypoints
app/
├── build.gradle.kts                        # com.phicomm.r1.control, minSdk 21, targetSdk 22, compileSdk 34, armeabi-v7a
├── proguard-rules.pro                      # Explicit -keep rules for Uni4micHalJNI & JniAsrFix
└── src/
    ├── main/
    │   ├── AndroidManifest.xml             # RECORD_AUDIO, RECEIVE_BOOT_COMPLETED, WAKE_LOCK, VoiceEngineService, BootCompletedReceiver
    │   ├── java/
    │   │   ├── com/unisound/jni/
    │   │   │   └── Uni4micHalJNI.kt        # Exact stock JNI bridge for /system/lib/libUni4micHalJNI.so
    │   │   ├── cn/yunzhisheng/asrfix/
    │   │   │   └── JniAsrFix.kt            # Exact stock JNI bridge for /system/lib/libasrfix.so
    │   │   └── com/phicomm/r1/control/
    │   │       ├── model/
    │   │       │   └── VoiceModels.kt      # VoiceEngineState, WakeWordEvent, AudioPipelineConfig, ModelAssetManifest, AudioDiagnosticsSnapshot
    │   │       ├── audio/
    │   │       │   ├── FourMicCaptureSource.kt       # Interface + Uni4micCaptureSource implementation
    │   │       │   ├── PcmChannelSelector.kt         # Stereo/mono extraction + per-channel RMS calculation
    │   │       │   └── PcmFrameAccumulator.kt        # Exact 60 ms (1,920-byte) mono frame framer
    │   │       ├── wakeup/
    │   │       │   ├── UnisoundModelProvisioner.kt   # Git-clean ZipFile extractor from /system/app/Unisound/Unisound.apk + CRC check
    │   │       │   ├── WakeWordEngine.kt             # Interface + AsrFixWakeWordEngine implementation
    │   │       │   ├── WakeWordResultParser.kt       # Pure-Kotlin result/score parser & threshold evaluator
    │   │       │   └── WakeWordDebouncer.kt          # Pure-Kotlin duplicate-trigger cooldown filter
    │   │       ├── opus/
    │   │       │   ├── OpusCodec.kt                  # Bidirectional 16 kHz mono Opus interface
    │   │       │   └── ConcentusOpusCodec.kt         # Concentus RFC-6716 implementation + benchmark helper
    │   │       ├── voice/
    │   │       │   ├── VoicePipelineCoordinator.kt   # Dedicated urgent-audio loop connecting 4-mic -> WakeWord -> Opus
    │   │       │   ├── VoiceEngineService.kt         # Persistent foreground Android Service + ADB diagnostic intents
    │   │       │   └── BootCompletedReceiver.kt      # Auto-starts VoiceEngineService on BOOT_COMPLETED
    │   │       └── ui/
    │   │           └── LaunchActivity.kt             # Minimal headless trigger activity (starts service & finishes)
    │   └── res/
    │       └── values/strings.xml
    └── test/java/com/phicomm/r1/control/
        ├── audio/
        │   ├── PcmChannelSelectorTest.kt
        │   └── PcmFrameAccumulatorTest.kt
        ├── wakeup/
        │   ├── WakeWordResultParserTest.kt
        │   ├── WakeWordDebouncerTest.kt
        │   └── UnisoundModelProvisionerTest.kt
        └── opus/
            └── ConcentusOpusCodecTest.kt
```

**Structure Decision**: Single clean `:app` Gradle module with strict package separation between thin stock JNI bridges (`com.unisound.jni`, `cn.yunzhisheng.asrfix`), pure-Kotlin testable DSP/parsing units (`audio`, `wakeup`, `opus`), and Android service lifecycle (`voice`, `ui`).

## Complexity Tracking

No Constitution violations or complexity exceptions required.
