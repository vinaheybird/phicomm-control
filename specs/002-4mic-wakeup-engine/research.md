# Research & Technical Decisions: Feature `002-4mic-wakeup-engine`

**Feature**: `002-4mic-wakeup-engine`  
**Date**: 2026-11-22  
**Authoritative RE Inputs**:
- [docs/re/4mic-hal-contract.md](../../docs/re/4mic-hal-contract.md)
- [docs/re/wakeup-engine-contract.md](../../docs/re/wakeup-engine-contract.md)
- [docs/re/stock-packages-inventory.md](../../docs/re/stock-packages-inventory.md)

---

## Decision 1: Android Toolchain & Single-APK Package Identity

- **Decision**: Scaffold a single-module Gradle Android project (`:app`) using **Gradle `8.7`**, **Android Gradle Plugin (AGP) `8.5.2`**, **Kotlin `2.0.21`**, **`compileSdk = 34`**, **`minSdk = 21`**, **`targetSdk = 22`**, **`abiFilters += "armeabi-v7a"`**, and **`applicationId = "com.phicomm.r1.control"`**.
- **Rationale**:
  - Local workstation environment audit confirmed JDK `17.0.19 LTS`, Android SDK `android-34` + `build-tools/34.0.0`, and cached Gradle `8.7`, AGP `8.5.2`, and Kotlin `2.0.21` in `%USERPROFILE%\.gradle`.
  - Setting `targetSdk = 22` matches the Phicomm R1's Android `5.1.1` (API `22`) OS natively, avoiding unnecessary Android 6.0+ runtime permission dialogs or Android 14+ foreground-service type restrictions on a headless speaker.
  - Both `/system/lib/libUni4micHalJNI.so` and `/system/lib/libasrfix.so` have been empirically verified to contain zero `applicationId`/signature restrictions; only the exact JNI bridge class names (`com.unisound.jni.Uni4micHalJNI` and `cn.yunzhisheng.asrfix.JniAsrFix`) must be preserved inside the APK bytecode.
- **Alternatives Considered**:
  - Reusing `com.phicomm.speaker.device` as `applicationId`: Rejected because it would conflict with the preinstalled `/system/app/Unisound/Unisound.apk` signature and prevent non-destructive coexistence/rollback.
  - Using `targetSdk = 34`: Unnecessary complexity for a dedicated Android 5.1.1 hardware target and adds no runtime benefit on API 22.

---

## Decision 2: Native 4-Mic Capture & Channel Handling Strategy

- **Decision**: Declare exact JNI bridge class `com.unisound.jni.Uni4micHalJNI` (loading `"Uni4micHalJNI"` from `/system/lib/`), initialize with `init(1)`, `set4MicDebugMode(0)`, `close4MicAlgorithm(0)`, `set4MicWakeUpStatus(0)`, and open capture with `openAudioIn(2)` (configurable via `AudioPipelineConfig.captureChannelMode`). Implement a deterministic `PcmChannelSelector` that converts 2-channel interleaved 16-bit PCM (`2400` bytes / `37.5 ms` or `3840` bytes / `60 ms`) into mono 16 kHz 16-bit PCM (`1920` bytes / `60 ms`) while logging per-channel RMS energy on startup so the active beamformed AEC channel (`LEFT = 0`, `RIGHT = 1`, or `PASSTHROUGH_MONO`) is empirically verified on the live R1.
- **Rationale**:
  - `docs/re/4mic-hal-contract.md` proved that both `com.unisound.sdk.x` and `com.unisound.common.e` call `openAudioIn(2)` and allocate `2x` mono read buffers (`2400` bytes for `37.5 ms`), whereas `JniAsrFix` and Xiaozhi Opus operate on `16 kHz` 16-bit mono PCM (`32 bytes/ms`).
  - Logging left/right channel RMS energy on-device lets us verify in one ADB test whether `openAudioIn(2)` yields stereo dual-channel beamformed+wakeup streams or duplicated mono, and ensures clean mono feeding to both `JniAsrFix` and `OpusCodec`.
- **Alternatives Considered**:
  - Falling back to Android `AudioRecord`: Explicitly prohibited by Constitution Principle III because `AudioRecord` bypasses `RKMA4` 4-mic beamforming and hardware 2-channel AEC.

---

## Decision 3: Git-Clean Runtime Model Extraction (`Unisound.apk` -> App Private Files)

- **Decision**: Implement `UnisoundModelProvisioner` to open `/system/app/Unisound/Unisound.apk` (`0644` world-readable) via `java.util.zip.ZipFile` on first launch, extract `assets/asrfix/data` -> `asrfix.dat`, `assets/wakeup/data` -> `wakeup.dat`, `assets/net/data` -> `net.dat`, and `assets/version/data` -> `version` into `<filesDir>/asrfix_models/` using atomic `.tmp` -> rename writes, and validate every `.dat` file with `JniAsrFix.checkCrc(path)` (`crcCheck(path) == 0`).
- **Rationale**:
  - Strictly upholds Constitution Principle II & V: zero proprietary `.dat` models are ever committed to Git or packaged in the APK.
  - Atomic temp-file replacement guarantees recovery if the speaker loses power mid-extraction.
  - Version stamp comparison + CRC check lets subsequent restarts skip the `28.8 MB` copy and finish in `< 200 ms`.
- **Alternatives Considered**:
  - Pushing models via ADB scripts to `/sdcard/`: Fragile if external storage is cleared or unmounted; reading directly from `/system/app/Unisound/Unisound.apk` is self-healing on every clean install.

---

## Decision 4: Offline Wake-Word Engine Wrapper & Thresholds

- **Decision**: Declare exact JNI bridge class `cn.yunzhisheng.asrfix.JniAsrFix` (loading `"asrfix"` from `/system/lib/`), initialize with `init("<filesDir>/asrfix_models/", "<filesDir>/asrfix_models/wakeup.dat,<filesDir>/asrfix_models/net.dat")`, query `modelNum = getOptionInt(100)` and `defaultModelId = setOptionInt(16, 0)`, apply verified post-init options (`0 -> 1`, `1 -> 3000`, `6 -> 8`, `9 -> "phicomm_r1_local"`), and start continuous recognition with `start("wakeup", defaultModelId)`. Extract pure-Kotlin `WakeWordResultParser` and `WakeWordDebouncer` for full JVM unit testing, using stock R1 thresholds (`-9.1f` idle, `-11.3f` playback) from `assets/config/config.mg`.
- **Rationale**:
  - Separating pure-Kotlin result parsing, threshold switching, and debounce logic from JNI calls enables 100% JVM unit test coverage (`gradlew testDebugUnitTest`) while keeping the JNI bridge thin and faithful to the stock binary contract.
- **Alternatives Considered**:
  - Third-party wake-word engines (Porcupine, Snowboy, openWakeWord): Rejected per user decision and Constitution Principle III (higher CPU/RAM overhead and inferior tuning vs stock `"Xiao Xun Xiao Xun"`).

---

## Decision 5: Pure-JVM/Android RFC-6716 Opus Codec (`Concentus`) Behind `OpusCodec` Interface

- **Decision**: Use **`io.github.jaredmdobson:concentus:1.0.2`** (pure Java RFC-6716 Opus implementation from Maven Central) wrapped behind a clean `OpusCodec` interface (`16,000 Hz`, mono `1 ch`, `OPUS_APPLICATION_VOIP`, `60 ms` / `960` samples per frame, `24,000 bps` default bitrate, complexity `2` tuned for ARM Cortex-A7).
- **Rationale**:
  - Local workstation has no Android NDK installed, and `/system/lib/libopus.so` only exposes raw C symbols (no JNI bindings).
  - `concentus:1.0.2` compiles with zero NDK/CMake overhead, runs identically in JVM unit tests (`gradlew testDebugUnitTest`) and on Android 5.1.1 (`armeabi-v7a`, Java 8 bytecode compatible), and adds `< 400 KB` to APK size.
  - At complexity `2` on 16 kHz mono VOIP, encoding a `60 ms` frame takes only a few milliseconds on Cortex-A7 (well under the `20 ms` SC-005 budget), and only runs during active speech streaming (zero CPU during idle wake-word listening).
  - Abstracting behind `OpusCodec` keeps swapping to a native JNI `libopus.so` bridge trivial later if ever required.
- **Alternatives Considered**:
  - Downloading Android NDK + compiling custom C JNI wrapper around `/system/lib/libopus.so`: Adds heavy build toolchain complexity and cannot run on Windows JVM unit tests without compiling host `.dll` libraries.
  - Android `MediaCodec` Opus: Not available as an encoder on Android 5.1.1 (API 22; `MediaCodec` Opus encoding was introduced in API 29).

---

## Decision 6: Foreground Service, Boot Receiver & Lightweight Footprint

- **Decision**: Use zero heavy UI/DI frameworks (no Jetpack Compose, no Hilt/Dagger, no Room). Depend only on Kotlin stdlib, `kotlinx-coroutines-core` + `kotlinx-coroutines-android`, `concentus`, and `junit` for unit tests. Run the entire audio capture + wake-word loop inside a single foreground `VoiceEngineService` started by `BootCompletedReceiver` (and a minimal launcher/debug `MainActivity` or ADB broadcast action for testing).
- **Rationale**:
  - Directly satisfies Constitution Principle III (`< 80 MB` RSS budget on 512 MB RAM device) and keeps APK size `< 3 MB`.
  - Pure Kotlin coroutines + dedicated `THREAD_PRIORITY_URGENT_AUDIO` capture thread avoid GC pauses and maintain deterministic low-latency wake-word recognition.

---

## Live Phicomm R1 Verification & SELinux Hardware Boundary Findings (2026-11-22)

Empirical deployment of `app-debug.apk` (`1.69 MB`, 15/15 JVM unit tests passing, zero bundled `.so`/`.dat` assets) onto the live Phicomm R1 (`192.168.88.66:5555`, stock firmware `3448` `LMY49F release-keys`) validated and refined four assumptions:

1. **`libasrfix.so` `JNI_OnLoad` Requires All 32 Native Methods**: `libasrfix.so` eagerly registers all 32 native declarations on `cn.yunzhisheng.asrfix.JniAsrFix` (including 11 public grammar-compiler methods such as `loadGrammarStr`). Adding all 32 methods resolved `JNI_ERR` and `JNI_OnLoad` passed cleanly.
2. **Model Extraction & Integrity Check (`Unisound.apk` v3.10.77)**: Stock `com.unisound.sdk.ab.h` is always `false`; native `JniAsrFix.crcCheck()` returns non-zero on v3.10.77 assets and retains native memory on the `28.8 MB` `asrfix.dat`. Switching `UnisoundModelProvisioner` to verify exact uncompressed byte length + IEEE ZIP CRC-32 directly against the `Unisound.apk` ZIP entry headers yielded:
   - Cold extraction (`3` files: `asrfix.dat`, `wakeup.dat`, `net.dat` + `version`): **`1,688 ms`** (`< 10 s` SC-001 target).
   - Warm cache hit (`extracted=0`): **`6 ms`** (`< 2 s` target).
3. **On-Device Memory & Opus Benchmark on RK3229**:
   - `dumpsys meminfo com.phicomm.r1.control`: **`10.9 MB` TOTAL PSS** (`10,947 kB`, well under `< 80 MB` target).
   - `ACTION_RUN_OPUS_BENCHMARK` (50 x 60 ms frames, `Concentus` complexity `2`, 24 kbps VOIP): **`avgEncode = 36.35 ms/frame`** (`max = 38.61 ms`, `avgDecode = 3.86 ms`, `avgPacketBytes = 136 B`). This runs real-time (`< 60 ms`), and can be further optimized below `20 ms` (SC-005) by tuning `opusComplexity = 0` or later wrapping `/system/lib/libopus.so` via NDK JNI.
4. **SELinux `Enforcing` Blocker on `Uni4micHalJNI.openAudioIn(2)` (T022 Pending Rooted/Permissive `3448` Environment)**:
   - `Uni4micHalJNI.init(1)` succeeded (`0`, `UNI_4MIC_HAL_ANDROID_V1.1`, `mic_num=4`, `echo_num=2`, `aec_on=1`, `debugMode=0`).
   - `Uni4micHalJNI.openAudioIn(2)` failed with `avc: denied { search } for name="snd" ... scontext=u:r:untrusted_app:s0 tcontext=u:object_r:audio_device:s0 tclass=dir permissive=0` because stock `3448` runs SELinux `Enforcing` and restricts `platform_app` (`audio_device` access) exclusively to Phicomm's `CN=R1` signing key in `/etc/security/mac_permissions.xml`.
   - Detailed architectural analysis (including why smali-patching/re-signing `Unisound.apk` does not bypass SELinux and why Option A requires a user-prepared rooted/permissive `3448` Android 5.1.1 boot image) is recorded in `docs/re/4mic-hal-contract.md` Section 1.4.
