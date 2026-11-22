# Phicomm R1 Stock Package Inventory & Non-Destructive Disable Plan

**Audit Date**: 2026-11-22  
**Target Device**: Phicomm R1 (`rk322x-box`, Android `5.1.1` / API `22`, `512 MB` RAM)  
**Primary Empirical Sources**:
- Live `adb shell pm list packages -f -u` (`192.168.88.66:5555`)
- Decoded `AndroidManifest.xml` for all 11 preinstalled `com.phicomm.speaker.*` APKs plus `com.android.bluetooth` via Android SDK `aapt.exe dump xmltree`
- Disassembled DEX of `Unisound.apk`, `EchoService.apk`, `Bluetooth.odex`, and `Launcher.odex`

---

## 1. Complete Stock Package Inventory (All 11 `com.phicomm.speaker.*` + Core Dependencies)

Every preinstalled Phicomm package and relevant system/user-space package on the live speaker is classified below.

| # | Package Name | APK Path on Device | `sharedUserId` | `persistent` | Boot / Key Receivers & Services | Classification | Evidence & Rationale |
|---|---|---|---|---|---|---|---|
| 1 | `com.android.bluetooth` | `/system/app/Bluetooth/Bluetooth.apk` | `android.uid.bluetooth` | `false` | `AdapterService`, `A2dpSinkService`, `AvrcpControllerService`, `HeadsetService`, `GattService` | **KEEP ACTIVE** | Provides Android A2DP Sink (Profile 10) and AVRCP Controller (Profile 11) required for PC Bluetooth audio & Pause/Play coexistence. |
| 2 | `com.phicomm.speaker.launcher` | `/system/app/Launcher/Launcher.apk` | *(none)* | `false` | `MainActivity` (`MAIN` / `HOME`), registers dynamic `PACKAGE_*` receiver only while Activity lives | **KEEP ACTIVE** | Minimal 9.9 KB AOSP-style Home stub (verified via `Launcher.odex`). Zero background services, zero boot receivers, zero hardware interference; keeping it prevents `ActivityManager` missing-HOME warnings on boot. |
| 3 | `com.phicomm.speaker.device` | `/system/app/Unisound/Unisound.apk` | *(none)* | `false` | `MessageReceiver` (`BOOT_COMPLETED`), `WindowsService`, `MusicService`, `MemoRingingService`, `com.baidu.location.f` (`:remote`), `NetworkConnectChangedReceiver` | **REPLACE (HIDE ONLY — DO NOT DELETE APK)** | Replaced by our standalone clean APK. Must be hidden so it releases exclusive lock on `/dev/snd/pcmC1D0c` (`Uni4micHalJNI`) and stops dead Phicomm/Unisound cloud polling. **Keep `/system/app/Unisound/Unisound.apk` intact on `/system`** (`0644`) so our APK extracts `asrfix/data`, `wakeup/data`, and `net/data` at runtime. |
| 4 | `com.phicomm.speaker.player` | `/system/app/EchoService/EchoService.apk` | `android.uid.system` | **`true`** | `EchoReceiver` (`BOOT_COMPLETED`, **`PAIRING_REQUEST`**), `EchoService`, `:AirPlayer`, `:DlnaPlayer`, `:NetPlayer` | **SAFE TO HIDE (CRITICAL)** | Actively disconnects PC Bluetooth A2DP Sink devices (`BluetoothPlayer.bootInit()` and `BluetoothPlayer$2.run()`), forces `SCAN_MODE_NONE`, hijacks `PAIRING_REQUEST`, and wastes RAM across 4 processes. |
| 5 | `com.phicomm.speaker.airskill` | `/system/app/AirSkill/AirSkill.apk` | `android.uid.system` | `false` | `BootCompleteReceiver` (`BOOT_COMPLETED`), `AirService` | **SAFE TO HIDE** | Legacy Phicomm cloud/skill bridge (`7.1 KB`). Dead backend; no OS Wi-Fi/BT/audio dependency. |
| 6 | `com.phicomm.speaker.netctl` | `/system/app/NetControl/NetControl.apk` | `android.uid.system` | `false` | `BootCompleteReceiver` (`BOOT_COMPLETED`), `MainService` (`smartsound.intent.action.*`), `org.mortbay.ijetty.IJettyService` | **SAFE TO HIDE (VERIFY WI-FI/BT POST-REBOOT)** | Legacy Phicomm BLE/Wi-Fi provisioning daemon & embedded Jetty helper. Standard Android `WifiService` and `BluetoothManagerService` manage saved Wi-Fi SSIDs and Bluetooth independently once provisioned. Verify Wi-Fi + ADB + BT auto-connect after reboot. |
| 7 | `com.phicomm.speaker.ijetty` | `/system/app/IJetty/IJetty.apk` | `android.uid.system` | `false` | `IJettyService` (exported) | **SAFE TO HIDE** | Legacy Phicomm embedded HTTP server (replaced by our minimal LAN Web Dashboard in Feature `005`). |
| 8 | `com.phicomm.speaker.systemtool` | `/system/app/SystemTool/SystemTool.apk` | `android.uid.system` | **`true`** | `BootCompleteReceiver` (`BOOT_COMPLETED`), `MainService`, `MqttService`, `BleToolService`, `StorageMonitorService`, `com.baidu.location.f` (`:remote`) | **SAFE TO HIDE** | Persistent telemetry/MQTT/BLE provisioning/Baidu Location service. Consumes RAM and connects to dead Phicomm servers. |
| 9 | `com.phicomm.speaker.otaservice` | `/system/app/OtaService/OtaService.apk` | `android.uid.system` | **`true`** | `BootCompleteReceiver` (`BOOT_COMPLETED`), `SysOTAService`, `MqttService`, `MqttOtaClientService` | **SAFE TO HIDE** | Persistent Phicomm firmware OTA polling via MQTT. Phicomm OTA servers are permanently shut down. |
| 10 | `com.phicomm.speaker.productiontest` | `/system/app/ProductionTest/ProductionTest.apk` | `android.uid.system` | **`true`** | `BootCompleteReceiver` (`BOOT_COMPLETED`), `MainService` (`SETUP_PRODUCTION_TEST_MODE`) | **SAFE TO HIDE** | Factory assembly-line test harness (`9.47 MB`, persistent process). Unnecessary in normal operation. |
| 11 | `com.phicomm.speaker.bugreport` | `/system/app/BugReport/BugReport.apk` | `android.uid.system` | **`true`** | `BootCommpleteReceiver` (`BOOT_COMPLETED`), `BugReportService`, `ExcuteUploadReceiver` | **SAFE TO HIDE** | Persistent crash-upload daemon targeting dead Phicomm servers. |
| 12 | `com.phicomm.speaker.exceptionreporter` | `/system/app/ExceptionReporter/ExceptionReporter.apk` | `android.uid.system` | `false` | `BootCompleteReceiver` (`BOOT_COMPLETED`), `ExReporterService` | **SAFE TO HIDE** | Legacy exception reporter targeting dead Phicomm servers. |
| 13 | `com.phicomm.gemini` *(user-space legacy)* | `/data/app/com.phicomm.gemini-1/base.apk` | *(none)* | `false` | User-installed legacy APK from previous project | **UNINSTALL IN FEATURE `003`** | Legacy app superseded by this clean-slate rewrite. Uninstall cleanly via `adb uninstall com.phicomm.gemini` when deploying the new APK. |

### 1.2 Hardware Dependencies Confirmed Independent of Hidden Packages

- **4-Mic Array & AEC**: Handled by `/system/lib/libUni4micHalJNI.so`, `/system/lib/libuni4michal.so`, `/system/usr/uni_4mic_config/*`, and kernel ALSA driver `RKMA4` (`/dev/snd/pcmC1D0c`). Independent of all 10 hidden Phicomm apps.
- **Wake-Word Engine**: Handled by `/system/lib/libasrfix.so` + model assets read from `/system/app/Unisound/Unisound.apk` (`0644`). Independent of running `com.phicomm.speaker.device`.
- **Bluetooth A2DP Sink & AVRCP**: Handled by `com.android.bluetooth` (kept active).
- **Speaker Audio Output (AK7755 DSP)**: Handled by Android `AudioFlinger` + kernel driver `RKAK7755` (`/dev/snd/pcmC2D0p`).
- **Wi-Fi & ADB over TCP**: Handled by Android `SystemServer` (`WifiService`, `wpa_supplicant`) and `adbd`.
- **LED Ring & Hardware Buttons**: `/system/lib/libledLight-jni.so` / `/system/lib/libledjni.so` and Linux input events (`/dev/input/event*`) remain available directly to our clean APK if needed in future enhancements.

---

## 2. Reversible Non-Destructive Deployment & Rollback Procedures

> **IMPORTANT**: Feature `001-r1-re-audit` is strictly read-only. Execute the commands below only during Feature `002` / `003` deployment — NEVER delete files under `/system/app/`.

### 2.1 Non-Destructive Hide Procedure (10 Stock Phicomm Packages)

```powershell
$R1 = "192.168.88.66:5555"
adb connect $R1

$packagesToHide = @(
    "com.phicomm.speaker.device",
    "com.phicomm.speaker.player",
    "com.phicomm.speaker.airskill",
    "com.phicomm.speaker.netctl",
    "com.phicomm.speaker.ijetty",
    "com.phicomm.speaker.systemtool",
    "com.phicomm.speaker.otaservice",
    "com.phicomm.speaker.productiontest",
    "com.phicomm.speaker.bugreport",
    "com.phicomm.speaker.exceptionreporter"
)

foreach ($pkg in $packagesToHide) {
    adb -s $R1 shell "pm hide $pkg"
}

# Remove legacy user-space APK from prior project (when ready in Feature 002/003):
# adb -s $R1 uninstall com.phicomm.gemini

# Reboot so all persistent android.uid.system processes terminate cleanly:
adb -s $R1 reboot
```

### 2.2 Full Rollback / Restore Procedure

Because `pm hide` only updates `/data/system/users/0/package-restrictions.xml` without touching `/system`, every stock package can be restored instantaneously:

```powershell
$R1 = "192.168.88.66:5555"
adb connect $R1

$packagesToRestore = @(
    "com.phicomm.speaker.device",
    "com.phicomm.speaker.player",
    "com.phicomm.speaker.airskill",
    "com.phicomm.speaker.netctl",
    "com.phicomm.speaker.ijetty",
    "com.phicomm.speaker.systemtool",
    "com.phicomm.speaker.otaservice",
    "com.phicomm.speaker.productiontest",
    "com.phicomm.speaker.bugreport",
    "com.phicomm.speaker.exceptionreporter"
)

foreach ($pkg in $packagesToRestore) {
    adb -s $R1 shell "pm unhide $pkg"
}

adb -s $R1 reboot
```

### 2.3 Post-Reboot Verification Checklist

After executing the hide procedure and rebooting (during Feature `002`/`003`):

1. **ADB Wi-Fi & Network**: `adb connect 192.168.88.66:5555` succeeds and `adb shell ping -c 2 api.tenclass.net` succeeds.
2. **Bluetooth Stack Healthy**: `adb shell dumpsys bluetooth_manager | grep -E "enabled:|Profile: A2dpSinkService|Profile: AvrcpControllerService"` shows Bluetooth enabled with both profiles active.
3. **Zero Legacy Phicomm Daemons Running**: `adb shell ps | grep phicomm` shows only `com.phicomm.speaker.launcher` (and our new APK once launched); zero `EchoService`, `Unisound`, `OtaService`, or `SystemTool` processes.
4. **Asset Source File Intact**: `adb shell ls -l /system/app/Unisound/Unisound.apk` still shows `-rw-r--r--` (`24936387` bytes).
5. **Memory Headroom Reclaimed**: `adb shell free -m` / `cat /proc/meminfo` confirms reclaimed RAM from terminating the 5 persistent `android.uid.system` apps + multi-process `EchoService` + `Unisound`.
