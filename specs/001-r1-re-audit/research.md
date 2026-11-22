# Research: Phicomm R1 Stock Firmware Reverse-Engineering Audit

**Feature**: `001-r1-re-audit` | **Date**: 2026-11-22

## 1. Reverse-Engineering Tooling Selection

- **Decision**: Use local Android SDK Build-Tools `34.0.0` (`aapt.exe` and `dexdump.exe`), `adb.exe`, and Python 3 standard-library scripts (`zipfile`, `struct`, `re`) without downloading third-party decompilers.
- **Rationale**:
  - `dexdump.exe -d -f -h` directly decodes Dalvik `.dex` headers, exact JNI method descriptors (parameter and return types), class fields, and full Dalvik bytecode instructions from un-odexed stock APKs (`Unisound.apk`, `EchoService.apk`).
  - `aapt.exe dump xmltree <apk> AndroidManifest.xml` extracts exact package names, permissions, exported services, broadcast receivers, and `BOOT_COMPLETED` triggers cleanly without needing `apktool`.
  - Python ELF/string parsing + live ADB inspection covers native `.so` libraries (`/system/lib/*.so`) and odexed system APKs (`Bluetooth.apk`, `Launcher.apk`).
- **Alternatives Considered**: Downloading portable `jadx` or `apktool` — rejected as unnecessary overhead given `dexdump.exe` and `aapt.exe` are already installed locally and provide exact bytecode/descriptor ground truth.

## 2. Live Phicomm R1 Baseline (`192.168.88.66:5555`)

Empirical findings collected during initial read-only ADB reconnaissance:

- **Platform**: Android `5.1.1` (API `22`), model `rk322x-box`, ABI `armeabi-v7a`, `MemTotal: 501,444 kB` (~512 MB RAM).
- **ALSA Audio Devices**:
  - Card 1 (`RK_MA4`): `/dev/snd/pcmC1D0c` (mode `0777`) — 4-mic + 2-channel hardware AEC capture.
  - Card 2 (`RK_AK7755`): `/dev/snd/pcmC2D0p` & `/dev/snd/pcmC2D0c` (mode `0777`) — AK7755 speaker DAC/DSP.
- **Key Native Libraries in `/system/lib/`**:
  - `libUni4micHalJNI.so`, `libuni4michal.so`, `libuni4michalchance.so`, `libUniMicArray.so`, `libUniLinearMicArray.so`, `libaec.so`, `libasrfix.so`, `libuscasr.so`, `libopus.so`, `libtinyalsa.so`, `libledLight-jni.so`, `libledjni.so`.
- **Hardware 4-Mic Configuration (`/system/usr/uni_4mic_config/`)**:
  - `pcm_hw_config.txt`: `pcm_card 1`, `pcm_device 0`, `i2s_bits 32`, `linear_4mic 0`, `no_vad 0`, `high_priority 1`, `mic_array_rw_path /system/usr/uni_4mic_config/`, `aec_enlarge_4mic 2`.
  - Additional config/weight files: `MicArrayConfig.ini`, `MicArray_config.txt`, `mic4_uni_test_config.txt`, `pcm_hw_config_chance.txt`, `wopt.bin`.

## 3. Handling Odexed System APKs (`Bluetooth.apk` & `Launcher.apk`)

- **Challenge**: On Android 5.1.1 (ART), `/system/app/Bluetooth/Bluetooth.apk` and `/system/app/Launcher/Launcher.apk` store compiled bytecode in `/system/app/<App>/arm/*.odex` (ELF OAT files containing embedded DEX payloads) rather than `classes.dex` inside the APK.
- **Decision**:
  1. Extract `AndroidManifest.xml` directly from `Bluetooth.apk` and `Launcher.apk` via `aapt.exe`.
  2. Extract embedded `dex\n035\0` payloads directly from `Bluetooth.odex` and `/system/framework/arm/boot.oat` (specifically `android.bluetooth.BluetoothA2dpSink` and `android.bluetooth.BluetoothAvrcpController`) using a small Python OAT DEX-carver script, then run `dexdump.exe` on the carved DEX to obtain 100% exact hidden API signatures on this exact Android 5.1.1 build!
  3. Cross-check live Bluetooth service state via `adb shell dumpsys bluetooth_manager` and `adb shell dumpsys audio`.
- **Rationale**: Carving embedded DEX sections from Android 5.1 ART `.odex`/`.oat` files is deterministic (standard DEX magic `dex\n035\0` + `file_size` header field at offset `0x20`) and reveals exact hidden framework methods (`BluetoothA2dpSink`, `BluetoothAvrcpController.sendPassThroughCmd`) present on the speaker.

## 4. Proprietary Model Asset Hygiene Strategy

- **Finding**: `Unisound.apk` (at `/system/app/Unisound/Unisound.apk`, readable `0644` by all Android apps!) bundles `assets/asrfix/data` (28.8 MB) and `assets/wakeup/data` (647 bytes).
- **Strategy to Verify in US2**: Confirm during `JniAsrFix` bytecode audit whether our standalone APK can extract/load `assets/asrfix/data` and `assets/wakeup/data` directly from `/system/app/Unisound/Unisound.apk` into app-private storage (`Context.getFilesDir()`) on first launch—meaning **zero proprietary model assets ever need to be bundled in our repository or Git history**!
