# Phicomm R1 4-Microphone Array & Hardware AEC Contract

**Audit Date**: 2026-11-22  
**Target Device**: Phicomm R1 (`rk322x-box`, Rockchip RK3229 quad-core ARM Cortex-A7, `armeabi-v7a`, Android `5.1.1` / API `22`, `512 MB` RAM)  
**Primary Empirical Sources**:
- `/system/app/Unisound/Unisound.apk` (`com.unisound.jni.Uni4micHalJNI`, `com.unisound.client.FourMicAudioManager`, `com.unisound.sdk.x`, `com.unisound.common.e`, `com.unisound.sdk.ax`)
- `/system/lib/libUni4micHalJNI.so` (9,312 bytes) and `/system/lib/libuni4michal.so` (25,820 bytes)
- `/system/usr/uni_4mic_config/pcm_hw_config.txt`, `MicArrayConfig.ini`, `MicArray_config.txt`
- Live device `/dev/snd/` inspection via ADB (`192.168.88.66:5555`)

---

## 1. Architecture & Hardware Audio Topology

The Phicomm R1 uses a dedicated circular 4-microphone far-field array with 2-channel hardware Acoustic Echo Cancellation (AEC) loopback. All beamforming, noise suppression, Direction of Arrival (DOA), and echo cancellation execute natively inside the stock system libraries rather than in Android's `AudioFlinger` software effects pipeline.

### 1.1 ALSA Sound Cards & Device Permissions

Verified via `cat /proc/asound/cards` and `ls -la /dev/snd/` on the live device:

| ALSA Card | Driver Name | Device Nodes | Permissions | Role |
|---|---|---|---|---|
| Card 0 | `RKHDMII2S` | `/dev/snd/pcmC0D0p` | `0660` (`system:audio`) | HDMI I2S output (unused on R1) |
| Card 1 | `RKMA4` | `/dev/snd/pcmC1D0c` | **`0777`** (`root:root`) | **4-mic array + 2-ch hardware AEC loopback capture** |
| Card 2 | `RKAK7755` | `/dev/snd/pcmC2D0p`, `/dev/snd/pcmC2D0c` | **`0777`** (`root:root`) | **AK7755 DSP speaker playback & capture** |
| Card 3 | `RKSPDIFCARD` | `/dev/snd/pcmC3D0p` | `0660` (`system:audio`) | SPDIF output (unused on R1) |

> **CRITICAL SELinux Constraint (Verified Live on R1, 2026-11-22)**: Although `/dev/snd/pcmC1D0c` has Unix mode `0777` (`system:audio`) and `Uni4micHalJNI.init(1)` succeeds (`mic_num=4`, `echo_num=2`, `aec_on=1`, `boardVersion=UNI_4MIC_HAL_ANDROID_V1.1`), the stock Phicomm R1 firmware runs **SELinux in `Enforcing` mode** (`getenforce` = `Enforcing`). `/etc/security/mac_permissions.xml` assigns `seinfo=platform` (`domain=platform_app`) exclusively to APKs signed with Phicomm's `CN=R1` platform key (`Unisound.apk`). Standard third-party APKs run in `u:r:untrusted_app:s0`, which is blocked by SELinux when `libuni4michal.so` calls `open("/dev/snd/pcmC1D0c")` inside `openAudioIn(2)`:
> ```text
> avc: denied { search } for name="snd" dev="tmpfs" ino=7492 scontext=u:r:untrusted_app:s0 tcontext=u:object_r:audio_device:s0 tclass=dir permissive=0
> [UNI_4MIC]:<ERR> Unable to open PCM card=1,device=0, (cannot open device '/dev/snd/pcmC1D0c': Permission denied)
> ```
> Consequently, direct in-process `openAudioIn()` from a third-party APK requires either:
> 1. Running the speaker with a developer/rooted/permissive SELinux environment (or custom `sepolicy` allowing `untrusted_app` or a dedicated domain to access `audio_device`), **or**
> 2. Using a privileged/system-domain audio bridge or stock-signed `platform_app` frontend.

### 1.2 Native Library Dependency Chain

Verified via ELF `DT_NEEDED` / strings inspection on `/system/lib/`:

1. `/system/lib/libUni4micHalJNI.so` (9,312 bytes) — JNI wrapper bound to Java class `com.unisound.jni.Uni4micHalJNI`.
2. `/system/lib/libuni4michal.so` (25,820 bytes, `UNI_4MIC_HAL_ANDROID_V1.1`) — Core HAL managing ALSA TinyALSA (`pcm_open`, `pcm_read`, `pcm_close`), I2C mic-board init (`uni_4mic_board_init`), ring buffers (`initRingBuf`, `readBuf`, `writeBuf`), and capture/processing worker threads (`pcm_read_thread_proc`, `mic_array_thread_proc`).
3. `/system/lib/libUniMicArray.so` (535 KB) & `/system/lib/libUniLinearMicArray.so` (711 KB) — Unisound beamforming, DOA (`Unisound_MicArray_Compute_DOA`), and 2-ch AEC (`Unisound_MicArray_Process`).
4. `/system/lib/libaec.so` (152 KB) & `/system/lib/libtinyalsa.so` — Underlying AEC DSP and ALSA interface.

### 1.3 Why Hardware AEC Works Across All Playback Sources

In `libuni4michal.so`, `pcm_read_thread_proc` captures raw 32-bit I2S frames from `pcmC1D0c` containing both the 4 microphone signals (`file_4mic`) and the 2 hardware speaker loopback reference channels (`file_2aec`) simultaneously, then feeds them to `uni_hal_4mic_array_process`. Because the 2 AEC reference channels are tapped at the hardware I2S/DSP level (AK7755 output path), **echo cancellation operates identically regardless of whether speaker audio originates from Bluetooth A2DP Sink, local TTS `AudioTrack`, or streamed music (`MediaPlayer`/`ExoPlayer`)**.

### 1.4 SELinux Enforcing Boundary, Firmware Prerequisites & Architectural Analysis (Verified 2026-11-22)

Live deployment of our clean standalone APK (`com.phicomm.r1.control`) onto stock Phicomm R1 firmware (`ro.build.version.incremental=3448`, `LMY49F release-keys`, `ro.build.type=user`, `ro.debuggable=1`, `ro.secure=1`) empirically revealed a kernel-level SELinux boundary:

- **What succeeds in `u:r:untrusted_app:s0`**:
  - Loading `/system/lib/libUni4micHalJNI.so` and `/system/lib/libuni4michal.so`.
  - `Uni4micHalJNI.init(1)` returns `0`, reporting `UNI_4MIC_HAL_ANDROID_V1.1`, `uni_hal_4mic_array_version=v2.3.0`, `mic_num=4`, `echo_num=2`, `aec_on=1`.
  - `set4MicDebugMode(0)`, `close4MicAlgorithm(0)`, and `set4MicWakeUpStatus(0)` all return `0`.
- **What is blocked by SELinux `Enforcing` in `u:r:untrusted_app:s0`**:
  - `Uni4micHalJNI.openAudioIn(2)` calls TinyALSA `open("/dev/snd/pcmC1D0c")`. Although Unix permissions on `/dev/snd/pcmC1D0c` are `0777` (`system:audio`), the kernel enforces SELinux (`getenforce` = `Enforcing`) and denies `untrusted_app` directory traversal on `/dev/snd`:
    ```text
    avc: denied { search } for name="snd" dev="tmpfs" ino=7492 scontext=u:r:untrusted_app:s0 tcontext=u:object_r:audio_device:s0 tclass=dir permissive=0
    [UNI_4MIC]:<ERR> Unable to open PCM card=1,device=0, (cannot open device '/dev/snd/pcmC1D0c': Permission denied)
    ```
  - Inspection of `/seapp_contexts` and `/etc/security/mac_permissions.xml` confirms that `<seinfo value="platform"/>` (`domain=platform_app`, which has SELinux permission to access `audio_device`) is mapped **exclusively** to APKs signed with Phicomm's private RSA key (`CN=R1`, SHA-256 `34:A2:D2:FE:89:96:56:AF:5D:F3:FB:F6:8E:80:54:77:B3:DF:37:08:CA:65:C8:42:BA:EC:2D:E8:E5:78:BC:33`).
  - ADB shell on stock `3448` runs as `uid=2000(shell)` (`u:r:shell:s0`), so it does not hold root privilege to change SELinux state at runtime.

#### Why Smali Patching or Java-Level Signature Hooking Does NOT Bypass This Boundary

Evaluated against existing public GitHub Phicomm R1 projects:
1. **Smali-patching `Unisound.apk` (e.g., `VRISE/feixun-r1`)**: Recompiling a patched `Unisound.apk` requires re-signing it with a new certificate (since Phicomm's private `CN=R1` key is unavailable). On unrooted stock `3448`, keeping package `com.phicomm.speaker.device` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, while renaming the package still places the process in `u:r:untrusted_app:s0` and triggers the exact same SELinux `avc: denied` error. Smali-patched APKs therefore still require a rooted / custom firmware environment—where our clean Kotlin single-APK already works directly without smali maintenance debt.
2. **Java `PackageManager` signature hooking (e.g., `sallaixu/R1-APP` `PmsHookBinderInvocationHandler`)**: Spoofing `PackageInfo.signatures` in Java only affects in-process Java SDK checks; it cannot alter the Linux kernel SELinux domain (`u:r:untrusted_app:s0`).

#### Architectural Options & Project Decision

| Option | Requires Rooted / Permissive `3448` Boot Image? | Preserves 4-Mic + Hardware AEC? | Summary |
|---|---|---|---|
| **Option A (Selected Architecture)** | **Yes** (one-time USB Loader/Maskrom flash of a rooted/permissive Android 5.1.1 `3448` boot image such as community `R1_rooted_boot.img` preserving `/system` Unisound `.so` libraries) | **Yes (100%)** | Clean single APK (`com.phicomm.r1.control`) directly invokes `libUni4micHalJNI.so` + `libasrfix.so` and streams 4-mic AEC-cleaned Opus directly to Xiaozhi Cloud. *(Do NOT flash Linux/Armbian if keeping Unisound 4-mic AEC, since `libUni4micHalJNI.so` and `libasrfix.so` require Android ARMv7 Bionic.)* |
| **Option B (Stock-Signed `Unisound.apk` Frontend)** | No | Partial (via Unisound NLU protocol only) | Keeps unmodified stock `Unisound.apk` (`platform_app`) running and proxies its NLU HTTP/MQTT layer in LAN (similar to `r1-webui`), but cannot stream raw 4-mic PCM/Opus directly to Xiaozhi Cloud. |
| **Option C (Standard `AudioRecord` via `mediaserver`)** | No | **No** | Works immediately on unrooted stock `3448` (similar to community `r1-xiaozhi` APKs), but loses 4-mic beamforming and 2-channel hardware AEC. |

---

## 2. On-Device Hardware Configuration Files

All configuration files reside in `/system/usr/uni_4mic_config/` (world-readable) and are read directly by `libuni4michal.so` during `uni_4mic_hal_init`. The clean APK does NOT need to bundle or copy these files.

### 2.1 `/system/usr/uni_4mic_config/pcm_hw_config.txt`

```ini
pcm_card 1
pcm_device 0
i2s_bits 32
linear_4mic 0
no_vad 0
high_priority 1
mic_array_rw_path /system/usr/uni_4mic_config/
aec_enlarge_4mic 2
debug_file_path /sdcard/unidata/
```

### 2.2 `/system/usr/uni_4mic_config/MicArrayConfig.ini`

```ini
[Engine]
ArrayType = circle
MicNum = 4
MicDistance = 3.5
HasCentre = 0
SpeakerNum = 2
Amplify = 1

[AEC]
AecFrameShift = 256
AecFilterNum = 12
```

### 2.3 `/system/usr/uni_4mic_config/MicArray_config.txt`

```text
mic_num 4
speaker_num 2
aec_filter_num 12
aec_nshift 256
amplify 1

micarray_pos
0.035000 0.000000
0.000000 0.035000
-0.035000 0.000000
0.000000 -0.035000
```

Geometry: 4 microphones arranged on a circle of radius `3.5 cm` (`0°`, `90°`, `180°`, `270°`), paired with 2 speaker AEC reference channels (`AecFrameShift = 256`, `AecFilterNum = 12`).

---

## 3. Exact JNI Contract: `com.unisound.jni.Uni4micHalJNI`

### 3.1 Package & Class Binding Requirement

Because `libUni4micHalJNI.so` exports static JNI symbol names (`Java_com_unisound_jni_Uni4micHalJNI_*`), the standalone APK **MUST** declare the exact class name **`com.unisound.jni.Uni4micHalJNI`** (and protect it from ProGuard/R8 obfuscation or shrinking). The APK's Android `applicationId` is completely independent and unrestricted.

### 3.2 Native Symbol & Method Signature Table

Every entry below was cross-verified between `dexdump.exe` disassembly of `Unisound.apk` (`com.unisound.jni.Uni4micHalJNI`) and exported symbols in `/system/lib/libUni4micHalJNI.so`:

| Java Method | JNI Descriptor | Visibility | Exported `.so` Symbol | Underlying `libuni4michal.so` Call |
|---|---|---|---|---|
| `initHal(int mode)` | `(I)I` | `private native` | `Java_com_unisound_jni_Uni4micHalJNI_initHal` | `uni_4mic_hal_init` |
| `releaseHal()` | `()I` | `private native` | `Java_com_unisound_jni_Uni4micHalJNI_releaseHal` | `uni_4mic_hal_release` |
| `openAudioIn(int ch)` | `(I)J` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_openAudioIn` | `uni_4mic_pcm_open` |
| `startRecorder(long handle)` | `(J)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_startRecorder` | `uni_4mic_pcm_start` |
| `readData(long handle, byte[] buf, int len)` | `(J[BI)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_readData` | `uni_4mic_pcm_read` |
| `stopRecorder(long handle)` | `(J)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_stopRecorder` | `uni_4mic_pcm_stop` |
| `closeAudioIn(long handle)` | `(J)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_closeAudioIn` | `uni_4mic_pcm_close` |
| `close4MicAlgorithm(int flag)` | `(I)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_close4MicAlgorithm` | `close4MicAlgorithm` |
| `set4MicWakeUpStatus(int status)` | `(I)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_set4MicWakeUpStatus` | `set4MicWakeUpStatus` |
| `get4MicDoaResult()` | `()I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_get4MicDoaResult` | `get4MicDoaResult` |
| `get4MicBoardVersion()` | `()Ljava/lang/String;` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_get4MicBoardVersion` | `get4MicBoardVersion` |
| `set4MicDebugMode(int mode)` | `(I)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_set4MicDebugMode` | `set4MicDebugMode` |
| `set4MicDelayTime(int ms)` | `(I)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_set4MicDelayTime` | `set4MicDelayTime` |
| `set4MicUtteranceTimeLen(int ms)` | `(I)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_set4MicUtteranceTimeLen` | `set4MicUtteranceTimeLen` |
| `set4MicWakeupStartLen(int len)` | `(I)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_set4MicWakeupStartLen` | `set4MicWakeupStartLen` |
| `set4MicOneShotStartLen(int len)` | `(I)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_set4MicOneShotStartLen` | `set4MicOneShotStartLen` |
| `set4MicOneShotReady(int ready)` | `(I)I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_set4MicOneShotReady` | `set4MicOneShotReady` |
| `get4MicOneShotReady()` | `()I` | `public native` | `Java_com_unisound_jni_Uni4micHalJNI_get4MicOneShotReady` | `get4MicOneShotReady` |

### 3.3 Static Initializer & Wrapper Methods

Verified from `com.unisound.jni.Uni4micHalJNI.<clinit>`, `getInstance()`, `init(int)`, and `release()`:

- Static initializer loads `"Uni4micHalJNI"` via `System.loadLibrary("Uni4micHalJNI")`. Because `/system/lib/libUni4micHalJNI.so` is in the Android system library search path, the APK MUST NOT bundle a duplicate copy.
- `init(int mode)` guards re-initialization via an internal boolean flag; if already initialized, it returns `0` immediately without calling `initHal(mode)` again (matching `<ERR> %s: hal is already inited.` in `libuni4michal.so`).
- `release()` returns `-1` if not initialized; otherwise invokes `releaseHal()` and clears the initialized flag when `releaseHal() == 0`.

### 3.4 Recommended Kotlin Declaration Template (Feature `002`)

```kotlin
package com.unisound.jni

class Uni4micHalJNI private constructor() {
    @Volatile
    private var isInitialized: Boolean = false

    fun init(mode: Int): Int {
        if (isInitialized) return 0
        val status = initHal(mode)
        if (status == 0) isInitialized = true
        return status
    }

    fun release(): Int {
        if (!isInitialized) return -1
        val status = releaseHal()
        if (status == 0) isInitialized = false
        return status
    }

    private external fun initHal(mode: Int): Int
    private external fun releaseHal(): Int

    external fun openAudioIn(ch: Int): Long
    external fun closeAudioIn(handle: Long): Int
    external fun startRecorder(handle: Long): Int
    external fun stopRecorder(handle: Long): Int
    external fun readData(handle: Long, buf: ByteArray, len: Int): Int

    external fun close4MicAlgorithm(flag: Int): Int
    external fun set4MicWakeUpStatus(status: Int): Int
    external fun get4MicDoaResult(): Int
    external fun get4MicBoardVersion(): String?
    external fun set4MicDebugMode(mode: Int): Int
    external fun set4MicDelayTime(ms: Int): Int
    external fun set4MicUtteranceTimeLen(ms: Int): Int
    external fun set4MicWakeupStartLen(len: Int): Int
    external fun set4MicOneShotStartLen(len: Int): Int
    external fun set4MicOneShotReady(ready: Int): Int
    external fun get4MicOneShotReady(): Int

    companion object {
        init {
            System.loadLibrary("Uni4micHalJNI")
        }

        @Volatile
        private var instance: Uni4micHalJNI? = null

        @JvmStatic
        fun getInstance(): Uni4micHalJNI =
            instance ?: synchronized(this) {
                instance ?: Uni4micHalJNI().also { instance = it }
            }
    }
}
```

---

## 4. Verified Capture Lifecycle & Parameters

Verified from `com.unisound.sdk.x` (`FourMicAudioManager` base class), `com.unisound.common.e` (`AudioSourceImpl`), and `com.unisound.sdk.ax` (`RecordingThread`):

### 4.1 Startup Sequence

1. `val hal = Uni4micHalJNI.getInstance()`
2. `val initStatus = hal.init(1)` — `mode = 1` selects 4-mic mode (`<INFO> use_4mic=1`). Returns `0` on success.
3. Configure HAL state (verified in `com.unisound.sdk.x.c()`):
   - `hal.set4MicDebugMode(0)` — **Important**: `com.unisound.sdk.x` defaults `m = 1` (debug WAV dumping to `/sdcard/unidata/file_4mic.wav` & `file_2aec.wav`), which rapidly fills flash storage! Production clean APK MUST set `0` (disabled) unless explicit diagnostic capture is enabled.
   - `hal.close4MicAlgorithm(0)` — `0` keeps 4-mic beamforming + AEC active (`1` bypasses algorithm).
   - `hal.set4MicWakeUpStatus(0)` — `0` places the beamformer in omni/wakeup-listening state.
4. `val handle = hal.openAudioIn(2)` — Both `com.unisound.sdk.x.a()` (`l = 2`) and `com.unisound.common.e.c()` (`v4 = 2`) pass `ch = 2` (`<INFO> ch_num=2`). Returns a non-zero native pointer cast to `long` (`<= 0` indicates failure).
5. `val startStatus = hal.startRecorder(handle)` — Starts `pcm_read_thread` and `mic_array_thread` inside `libuni4michal.so`. Returns `0` on success.

### 4.2 Streaming Read Loop & PCM Format

- **Sample Rate**: `16,000 Hz` (confirmed in `com.unisound.sdk.x.<clinit>`, `com.unisound.common.e.a = 16000`).
- **Bit Depth**: `16-bit signed little-endian PCM` (`ENCODING_PCM_16BIT = 2`).
- **Channel Parameter & Frame Sizing**:
  - Stock `com.unisound.sdk.x.run()` defines base mono chunk `s = 1200` bytes (`37.5 ms` @ `16 kHz, 16-bit, 1-ch`) and multiplies by `2` (`2400` bytes) when 4-mic mode (`j = true`, `openAudioIn(2)`) is active.
  - Stock `com.unisound.sdk.ax.a()` likewise allocates `baseBufferSize * 2` when 4-mic mode (`s.s() == true`) is active without stereo-to-mono downmix (`s.t() == false`).
  - `readData(handle, buf, buf.size)` blocks on the internal `MicArray` ring buffer and returns the number of bytes read (`> 0` on success; `<= 0` on error/stop).
  - **Downstream Feature `002` Verification Note**: Because `openAudioIn(2)` outputs `2`-channel interleaved 16 kHz 16-bit PCM (64 bytes/ms, 2400 bytes per 37.5 ms or 3840 bytes per 60 ms), Feature `002` MUST verify on-device which channel(s) carry the beamformed AEC-cleaned voice stream (and whether `openAudioIn(1)` directly yields 1-channel mono 32 bytes/ms) before feeding mono 16 kHz PCM into Xiaozhi Opus (`960 samples = 1920 bytes / 60 ms`).
- **Thread Priority**: `com.unisound.sdk.z.run()` elevates the Java reader thread to `Process.THREAD_PRIORITY_URGENT_AUDIO` (`-19`).

### 4.3 Wake-Word Beam Lock & DOA Retrieval

Verified from `com.unisound.sdk.j.a(String, Z)` and `com.unisound.common.s`:

- When `"Xiao Xun Xiao Xun"` triggers:
  1. Pass wake-word utterance timing if needed (`set4MicDelayTime(ms)`, `set4MicUtteranceTimeLen(ms)`).
  2. Call `hal.set4MicWakeUpStatus(1)` to lock the directional beamformer onto the speaker's angle (`1` = awake/ASR mode).
  3. Read `val doaDegrees = hal.get4MicDoaResult()` (angle in degrees, `0`–`359`, usable for LED ring direction or diagnostics).
- When the AI turn completes and the system returns to idle listening:
  - Call `hal.set4MicWakeUpStatus(0)` to unlock the beamformer back to 360° wake-word detection.

### 4.4 Teardown Sequence

Verified from `com.unisound.sdk.x.b()` and `com.unisound.sdk.x.d()`:

1. Stop read loop (set atomic running flag to `false`).
2. `hal.stopRecorder(handle)` — signals `pcm_read_thread` and `mic_array_thread` to exit and resets ring buffers.
3. `hal.closeAudioIn(handle)` — joins worker threads, destroys ring buffers, and closes ALSA `pcmC1D0c`.
4. `hal.release()` — releases `Unisound_MicArray_Release` and `uni_4mic_board_deinit`.

### 4.5 Exclusive Hardware Ownership Constraint

Only **one process** may open `/dev/snd/pcmC1D0c` via `Uni4micHalJNI` at any time. Stock `com.phicomm.speaker.device` (`Unisound.apk`) MUST be disabled (`pm hide com.phicomm.speaker.device`) before our clean APK starts `Uni4micHalJNI`, otherwise `pcm_open` fails with `Device or resource busy`.
