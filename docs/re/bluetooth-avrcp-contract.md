# Phicomm R1 Bluetooth A2DP Sink & AVRCP Coexistence Contract

**Audit Date**: 2026-11-22  
**Target Device**: Phicomm R1 (`rk322x-box`, Android `5.1.1` / API `22`)  
**Primary Empirical Sources**:
- `/system/app/Bluetooth/Bluetooth.apk` & `/system/app/Bluetooth/arm/Bluetooth.odex` (carved DEX disassembled via `dexdump.exe`: `A2dpSinkService`, `A2dpSinkStateMachine`, `AvrcpControllerService`)
- `/system/app/EchoService/EchoService.apk` (`com.phicomm.speaker.player.model.BluetoothPlayer`, `BluetoothPlayer$1`–`$5`, `AudioManagerHelper`)
- `/system/etc/audio_policy.conf` and `/system/etc/audio_effects.conf`
- Live device `dumpsys bluetooth_manager` and `dumpsys audio` (`192.168.88.66:5555`)

---

## 1. Bluetooth Stack & Enabled Profiles on Phicomm R1

Live `dumpsys bluetooth_manager` confirms that `/system/app/Bluetooth/Bluetooth.apk` (`com.android.bluetooth`, `sharedUserId="android.uid.bluetooth"`) runs with both **`A2dpSinkService`** and **`AvrcpControllerService`** active out of the box (Bluetooth adapter address `FC:7C:02:A0:CD:32`, default name `Phicomm_R1_CD31`).

On Android 5.1.1 (API 22), `android.bluetooth.BluetoothA2dpSink` and `android.bluetooth.BluetoothAvrcpController` are `@hide` system classes inside `framework.jar`, so user-space apps bind to them via `BluetoothAdapter.getProfileProxy(context, listener, profileId)` and invoke their methods via Java/Kotlin reflection.

### 1.1 Verified Profile ID Constants

Confirmed in `com.phicomm.speaker.player.model.BluetoothPlayer.onServiceConnected` and `destroy`:

| Profile | Hidden Framework Class | Profile Constant (`int`) | System Service in `com.android.bluetooth` |
|---|---|---|---|
| **A2DP Sink** | `android.bluetooth.BluetoothA2dpSink` | **`10`** (`0x0a`) | `com.android.bluetooth.a2dp.A2dpSinkService` |
| **AVRCP Controller** | `android.bluetooth.BluetoothAvrcpController` | **`11`** (`0x0b`) | `com.android.bluetooth.avrcp.AvrcpControllerService` |

---

## 2. Permission & Reflection Contract (Accessible to Clean User-Space APK)

Carved DEX inspection of `A2dpSinkService` and `AvrcpControllerService` in `Bluetooth.odex` confirms that **NEITHER service requires `BLUETOOTH_PRIVILEGED` or `android.uid.system`** for connection management or AVRCP pass-through commands!

### 2.1 `BluetoothA2dpSink` (Profile ID = `10`)

Verified from `com.android.bluetooth.a2dp.A2dpSinkService` & `A2dpSinkService$BluetoothA2dpSinkBinder`:

| Method Signature | Required Permission | Return Type | Notes |
|---|---|---|---|
| `connect(BluetoothDevice device)` | `android.permission.BLUETOOTH_ADMIN` | `boolean` | Initiates A2DP Sink connection to a bonded PC/phone |
| `disconnect(BluetoothDevice device)` | `android.permission.BLUETOOTH_ADMIN` | `boolean` | Disconnects active A2DP Sink device |
| `getConnectedDevices()` | `android.permission.BLUETOOTH` | `List<BluetoothDevice>` | Returns currently connected A2DP Sink devices |
| `getDevicesMatchingConnectionStates(int[] states)` | `android.permission.BLUETOOTH` | `List<BluetoothDevice>` | Queries devices in specified `BluetoothProfile.STATE_*` states |
| `getConnectionState(BluetoothDevice device)` | `android.permission.BLUETOOTH` | `int` | `0`=DISCONNECTED, `1`=CONNECTING, `2`=CONNECTED, `3`=DISCONNECTING |
| `getAudioConfig(BluetoothDevice device)` | `android.permission.BLUETOOTH` | `BluetoothAudioConfig` | Returns negotiated A2DP sample rate / channel config |

### 2.2 `BluetoothAvrcpController` (Profile ID = `11`)

Verified from `com.android.bluetooth.avrcp.AvrcpControllerService`:

| Method Signature | Required Permission | Return Type | Notes |
|---|---|---|---|
| `getConnectedDevices()` | `android.permission.BLUETOOTH` | `List<BluetoothDevice>` | Returns AVRCP-connected remote devices |
| `getConnectionState(BluetoothDevice device)` | `android.permission.BLUETOOTH` | `int` | AVRCP profile connection state |
| `sendPassThroughCmd(BluetoothDevice device, int keyCode, int keyState)` | **`android.permission.BLUETOOTH`** | `void` | Sends AVRCP 1.3+ pass-through remote control command to PC/phone |

### 2.3 Exact AVRCP Pass-Through Key Codes & Press/Release Protocol

Verified from `com.phicomm.speaker.player.model.BluetoothPlayer.sendCommand(int)`, `start()`, `pause()`, `playNext()`, and `playPre()`:

- **Mandatory Two-Step Sequence**: Every command MUST send **`keyState = 0` (KEY_PRESSED)** immediately followed by **`keyState = 1` (KEY_RELEASED)** on the target `BluetoothDevice`:
  ```kotlin
  sendPassThroughCmdMethod.invoke(avrcpProxy, device, keyCode, 0) // PRESSED
  sendPassThroughCmdMethod.invoke(avrcpProxy, device, keyCode, 1) // RELEASED
  ```
- **Target Device Fallback**: Query `avrcpController.getConnectedDevices()` first; if null or empty, fall back to `a2dpSink.getConnectedDevices()` (verified verbatim in `BluetoothPlayer.sendCommand`, offsets `0009`–`001e`).
- **Verified AVRCP Pass-Through Key Codes**:

| Command | Decimal `keyCode` | Hex | Verified Caller in `EchoService.apk` |
|---|---|---|---|
| **PLAY** | **`68`** | `0x44` | `BluetoothPlayer.start()` (and `disconnectDevice()`) |
| **PAUSE** | **`70`** | `0x46` | `BluetoothPlayer.pause()` |
| **NEXT / FORWARD** | **`75`** | `0x4b` | `BluetoothPlayer.playNext(boolean)` |
| **PREV / BACKWARD** | **`76`** | `0x4c` | `BluetoothPlayer.playPre(boolean)` |

### 2.4 Bluetooth Discoverability / Scan Mode Control

Verified from `BluetoothPlayer.startServer()` and `disconnectDevice()`:
- `BluetoothAdapter.setScanMode(int mode)` (hidden reflection method, requires `BLUETOOTH_ADMIN`):
  - `23` (`0x17`, `BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE`) — makes the R1 visible for PC Bluetooth pairing (used by the Web Dashboard "Enable Pairing" button in Feature `005`).
  - `21` (`0x15`, `BluetoothAdapter.SCAN_MODE_CONNECTABLE`) — allows already-paired PCs to reconnect automatically without advertising to new devices (recommended default steady state!).
  - `20` (`0x14`, `BluetoothAdapter.SCAN_MODE_NONE`) — blocks even paired devices from reconnecting (do NOT use in steady state).

---

## 3. Broadcast Actions for Connection & Playback State Detection

Verified from `com.android.bluetooth.a2dp.A2dpSinkStateMachine.broadcastAudioState`, `broadcastConnectionState`, and `BluetoothPlayer$1.onReceive`:

### 3.1 A2DP Sink Connection State Broadcast

- **Action**: `"android.bluetooth.a2dp.sink.profile.action.CONNECTION_STATE_CHANGED"`
- **Extras**:
  - `"android.bluetooth.device.extra.DEVICE"` (`BluetoothDevice`)
  - `"android.bluetooth.profile.extra.STATE"` (`int`): `0` = `STATE_DISCONNECTED`, `1` = `STATE_CONNECTING`, `2` = `STATE_CONNECTED`, `3` = `STATE_DISCONNECTING`
  - `"android.bluetooth.profile.extra.PREVIOUS_STATE"` (`int`)

### 3.2 A2DP Sink Playing State Broadcast

- **Action**: `"android.bluetooth.a2dp.sink.profile.action.PLAYING_STATE_CHANGED"`
- **Broadcast Permission**: `"android.permission.BLUETOOTH"` (normal permission)
- **Extras**:
  - `"android.bluetooth.device.extra.DEVICE"` (`BluetoothDevice`)
  - `"android.bluetooth.profile.extra.STATE"` (`int`):
    - **`10`** (`0x0a`, `STATE_PLAYING`) — PC is actively streaming A2DP audio to the R1
    - **`11`** (`0x0b`, `STATE_NOT_PLAYING`) — PC A2DP stream is paused or idle
  - `"android.bluetooth.profile.extra.PREVIOUS_STATE"` (`int`)

---

## 4. AudioFocus & Audio Policy Interaction

### 4.1 `A2dpSinkStateMachine` AudioFocus Behavior

Verified directly from carved `Bluetooth.odex` class `com.android.bluetooth.a2dp.A2dpSinkStateMachine$1.onAudioFocusChange(int focusChange)`:

```text
if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT (-2) ||
    focusChange == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK (-3)) {
    // Invokes A2dpSinkStateMachine.access$7800() -> pauses local A2DP Sink AudioTrack
} else if (focusChange == AUDIOFOCUS_GAIN (1)) {
    // Invokes A2dpSinkStateMachine.access$7700() -> resumes local A2DP Sink AudioTrack
}
```

**Critical Coexistence Implication**:
1. `A2dpSinkStateMachine` renders incoming PC Bluetooth audio via its own local `AudioTrack` on `STREAM_MUSIC` and listens to `AudioManager` focus changes.
2. When our clean APK requests **`AudioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)`** upon wake-word detection:
   - `A2dpSinkStateMachine` receives `AUDIOFOCUS_LOSS_TRANSIENT (-2)` and **immediately mutes/pauses its local A2DP `AudioTrack`**, guaranteeing instant silence on the speaker even before the PC reacts over Bluetooth.
   - Simultaneously sending **AVRCP `PAUSE (70)`** (`keyState = 0`, then `1`) pauses the actual media player on the PC (YouTube, Spotify, Foobar2000, Windows Media, etc.) so playback position does not advance while the user talks to Xiaozhi.
3. When the AI finishes speaking (or when a non-music Q&A turn ends):
   - Our APK calls `AudioManager.abandonAudioFocus(listener)`, delivering `AUDIOFOCUS_GAIN (1)` back to `A2dpSinkStateMachine` to reopen the local A2DP `AudioTrack`.
   - If the PC was `STATE_PLAYING (10)` prior to wake-word AND the user did NOT request Xiaozhi internet music playback, our APK sends **AVRCP `PLAY (68)`** (`keyState = 0`, then `1`) to deterministically resume PC audio.
   - If the user asked Xiaozhi to play internet/server music, our APK retains permanent `AUDIOFOCUS_GAIN` and does NOT send AVRCP `PLAY (68)` until the user stops AI music or manually resumes PC playback.

### 4.2 `/system/etc/audio_policy.conf` & Volume Constraints

Verified from `/system/etc/audio_policy.conf` and `dumpsys audio`:
- Primary speaker output (`AUDIO_DEVICE_OUT_SPEAKER`) supports `16000|32000|44100|48000|96000 Hz`, stereo, `PCM_16_BIT`, `AUDIO_OUTPUT_FLAG_PRIMARY`.
- A2DP Sink input (`AUDIO_DEVICE_IN_BLUETOOTH_A2DP`) is configured at `44100 Hz`, stereo, `PCM_16_BIT` and mixed by `AudioFlinger` to `AUDIO_DEVICE_OUT_SPEAKER` (AK7755 DSP, Card 2 `pcmC2D0p`).
- `STREAM_MUSIC` has volume range **`0..15`** (`Max: 15`, verified in `dumpsys audio`). Master volume controls in both Xiaozhi MCP tools and the LAN Web Dashboard (`005-basic-web-dashboard`) should target `AudioManager.STREAM_MUSIC` (`0..15`, mapped to `0%..100%`).
- `audio_effects.conf` confirms **no HAL software AEC/NS pre-processing** is enabled in Android `AudioRecord` (`pre_processing` section is commented out), reinforcing why 4-mic hardware capture via `Uni4micHalJNI` is mandatory.

---

## 5. Root Cause of Stock `EchoService` Bluetooth Disconnect Bug

Empirical disassembly of `EchoService.apk` (`com.phicomm.speaker.player.model.BluetoothPlayer`) confirms why Bluetooth connections drop whenever `EchoService` (`com.phicomm.speaker.player`) is allowed to run:

1. **`BluetoothPlayer.bootInit()`** (invoked inside `onServiceConnected` for Profile `10`): iterates through `mA2dpSink.getConnectedDevices()` and unconditionally calls `mA2dpSink.disconnect(device)` on every connected device.
2. **`BluetoothPlayer$2.run()`** (invoked whenever `CONNECTION_STATE_CHANGED` fires `STATE_CONNECTED`): checks `Utils.isSystemInNormalMode(mContext)` — if the speaker was not explicitly switched into Phicomm's proprietary "Bluetooth Mode", it immediately calls **`mA2dpSink.disconnect(device)`**.
3. **`BluetoothPlayer.disconnectDevice()`**: forces `mBluetoothAdapter.setScanMode(20)` (`SCAN_MODE_NONE`), preventing automatic PC reconnection.
4. **`EchoReceiver`** (`AndroidManifest.xml`): intercepts `android.bluetooth.device.action.PAIRING_REQUEST`, interfering with standard Bluetooth pairing flows.

**Resolution (to be applied in Feature `003`)**: Non-destructively disable `com.phicomm.speaker.player` via `pm hide com.phicomm.speaker.player` and reboot. Once hidden, `com.android.bluetooth` maintains persistent PC A2DP Sink + AVRCP connections cleanly under our APK's sole control.
