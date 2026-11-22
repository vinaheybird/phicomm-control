# Quickstart Validation Guide: Feature `002-4mic-wakeup-engine`

## Prerequisites

- JDK 17 (`java -version` = `17.x`) and Android SDK (`platforms/android-34`, `build-tools/34.0.0`).
- Live Phicomm R1 reachable over ADB Wi-Fi at `192.168.88.66:5555`.
- Stock `/system/app/Unisound/Unisound.apk` intact (`0644`).

---

## 1. Run Automated JVM Unit Tests & Assemble Debug APK

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Expected:
- All JVM unit tests in `app/src/test/java/com/phicomm/r1/control/` pass (`BUILD SUCCESSFUL`).
- APK generated at `app/build/outputs/apk/debug/app-debug.apk` (only `armeabi-v7a` ABI, zero proprietary `.dat` or stock `.so` files bundled).

---

## 2. Prepare Phicomm R1 (Non-Destructive Disable of Conflicting Stock Voice App)

> Only `com.phicomm.speaker.device` MUST be hidden to release `/dev/snd/pcmC1D0c` for Feature `002` verification. Full 10-package cleanup and `com.phicomm.gemini` removal can be applied now or in Feature `003`.

```powershell
$R1 = "192.168.88.66:5555"
adb connect $R1
adb -s $R1 shell "pm hide com.phicomm.speaker.device"
adb -s $R1 shell "am force-stop com.phicomm.speaker.device"
```

*(Rollback at any time: `adb -s $R1 shell "pm unhide com.phicomm.speaker.device"`)*

---

## 3. Install APK & Verify Cold-Start Model Extraction + 4-Mic Capture

```powershell
$R1 = "192.168.88.66:5555"
adb -s $R1 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s $R1 logcat -c
adb -s $R1 shell "am startservice -n com.phicomm.r1.control/.voice.VoiceEngineService"
adb -s $R1 logcat -d -s R1VoiceEngine:D R1ModelProvisioner:D R1FourMic:D R1WakeWord:D
```

Expected log sequence:
1. `R1ModelProvisioner`: Extracted `asrfix.dat`, `wakeup.dat`, `net.dat`, `version` from `/system/app/Unisound/Unisound.apk` in `< 10s`, all CRC checks passed (`crc=0`).
2. `R1FourMic`: `Uni4micHalJNI.init(1)=0`, `set4MicDebugMode(0)=0`, `openAudioIn(2)` valid handle, `startRecorder=0`, non-zero RMS on active channel(s).
3. `R1WakeWord`: `JniAsrFix.init=0`, `modelId=<id>`, `start("wakeup")=0`, state = `LISTENING_WAKEWORD`.

---

## 4. Verify Offline `"Xiao Xun Xiao Xun"` Detection & On-Device Opus Benchmark

```powershell
# Run on-device 60ms Opus encode/decode timing benchmark (must be < 20 ms/frame):
adb -s $R1 shell "am startservice -n com.phicomm.r1.control/.voice.VoiceEngineService -a com.phicomm.r1.control.ACTION_RUN_OPUS_BENCHMARK"

# Tail live wake-word detections while speaking "Xiao Xun Xiao Xun":
adb -s $R1 logcat -s R1VoiceEngine:I R1WakeWord:I R1Opus:I
```

---

## 5. Verify Memory Budget (`< 80 MB` RSS/PSS) & Clean Teardown

```powershell
adb -s $R1 shell "dumpsys meminfo com.phicomm.r1.control | grep TOTAL"
adb -s $R1 shell "am startservice -n com.phicomm.r1.control/.voice.VoiceEngineService -a com.phicomm.r1.control.ACTION_STOP"
```

Expected:
- `TOTAL` PSS `< 80,000 kB` (`< 80 MB`) during continuous idle wake-word listening.
- Clean `stopRecorder=0`, `closeAudioIn=0`, `release=0`, and subsequent restart succeeds cleanly.
