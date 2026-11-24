# Empirical Analysis & Optimization Guide: `Phicomm_R1_XiaoZhi_Client` (V2.0.6)

**Audit Date**: 2026-11-24  
**Analyzed Upstream**: `https://github.com/shj4588/Phicomm_R1_XiaoZhi_Client` (`V2.0.6` — device baseline `v1.3.360`, player baseline `pl-1.6.163`)  
**Target Device Tested**: Phicomm R1 (`192.168.88.66:5555`, stock firmware `3448`, Android `5.1.1` / API `22`, non-rooted)  
**License Note**: Upstream project code (`cn.workbuddy.r1client` / `cn.workbuddy.r1web` / build scripts) is licensed under **PolyForm Noncommercial 1.0.0** (personal / noncommercial use only).

---

## 1. Architecture Summary

Unlike a clean-slate standalone APK, `Phicomm_R1_XiaoZhi_Client` injects custom Java classes (`cn.workbuddy.r1client` and `cn.workbuddy.r1web`) alongside smali hooks into the stock Phicomm R1 system apps:

- **Device package (`com.phicomm.speaker.device` / `XiaoZhi_Unisound_V2.0.6.apk`)**: Runs stock Unisound (`ExampleApp`) for offline wake-word detection (`"小智小智"`), tees the 4-mic HAL PCM stream via a smali hook (`UscTap.feed()` inside `com/unisound/common/e.readData()`), encodes to Opus via native `libr1opus.so`, and streams full-duplex audio + MCP JSON-RPC to Xiaozhi Cloud (`wss://api.tenclass.net/xiaozhi/v1/`).
- **Player package (`com.phicomm.speaker.player` / `XiaoZhi_EchoService_V2.0.6.apk`)**: Hosts the LAN Web Console on port `8080` (proxying device settings on `8088`), DLNA/ExoPlayer playback, and Bluetooth A2DP Sink management (`Bt.java`).
- **Native library host (`XiaoZhi_SoHost_V2.0.1.apk`)**: Required once so Android PackageManager unpacks `libr1opus.so` into an SELinux-executable library path.
- **Canonical source tree for V2.0.6**: Active source code compiled by `build_rel_V206.sh` lives in `04-build-scripts/device/v2src/cn/workbuddy/r1client/` (newer than `01-parasite-src/device-v2src/`).

---

## 2. Empirical Root-Cause Analysis (Live Device `192.168.88.66`)

All findings below were verified directly against `files/r1client.log`, `files/r1-uplink.wav`, `dumpsys media.audio_flinger`, `dumpsys audio`, and `logcat` on the live Phicomm R1.

### 2.1 Choppy / Garbled / Missed Voice Recognition (Defaults vs Tuned)

- **Default config problem (`gain=16`, `alc=false`)**:
  - Live logs showed raw 4-mic HAL voice peaks reaching `raw = 150..10,398`.
  - Fixed `×16` digital gain pushed normal speech to severe square-wave clipping (`peak = 32,768`, `maxRms = 29,028..31,380`), causing cloud ASR to garble Vietnamese speech (e.g., `"nó không tự tắt đi"` misheard as `"Nó không trực tác đi."`) or return 0 STT for 25 seconds (420 frames sent, 0 replies).
  - Amplified room noise (`rms = 220..1,000`) also triggered Whisper/ASR hallucinations (e.g., `"Hãy subscribe cho kênh La La School..."`).
- **ALC test (`alc=true`, `ALC_MAX_GAIN = 10.0`, `ALC_ATTACK = 0.30`, `ALC_RELEASE = 0.02`)**:
  - Fast attack + very slow release caused ALC gain to pin near its ceiling (`alc = 9.66..9.99`) on minor room noise (`raw = 50..130`).
  - Sudden speech (`raw = 1,332`) hit the `tanh` soft-limiter (`peak = 30,000`), distorting Vietnamese tones/consonants (`"Mày biết làm toán không?"` → `"Mày mới làm quảm xong"` / `"Mày biết làm toản không?"`).
- **Optimal measured config (`alc=false`, `gain=4`)**:
  - Verified on a 48-second captured uplink recording (`r1-uplink.wav`, 16 kHz mono PCM16):
    - **Dropouts**: `0` (`max_zero_run = 6 samples = 0.4 ms`, confirming `UscTap` ran continuously at `64 KB/s` with zero `usctap stalled` events).
    - **Clipping**: `10` samples out of `768,000` (`0.001%`, effectively zero).
    - **Dynamic range**: Quiet room floor `rms = 91..150`, active speech `rms = 1,100..3,133`, `peak = 8,600..25,660` (`+20..25 dB` SNR).
    - **Vietnamese ASR result**: 9/9 test turns recognized accurately on first try (including `"Mày biết làm toán không?"`, `"1 cộng 1 bằng mấy"`, `"3 cộng 4 bằng mấy?"`, `"Hôm nay là thứ mấy?"`, `"thời tiết hôm nay thế nào"`).

### 2.2 AI Session Failing to Auto-Disconnect

1. **Default idle timeout is 90 seconds**: `auto_disconnect` defaults to `false` (`SessionController.idleWindowMs()` returns `90,000 ms` and sets `ws.setIdleCloseDelayMs(24h)`).
2. **Noise-triggered reset loop**: With `gain=16`, ASR hallucinations on room noise triggered TTS replies, and every `onTtsStop()` re-sent `listen start` and reset `noTtsWatchdog` back to 90s (observed sessions staying alive for `294+ s`).
3. **Hardcoded lower bound of 20 seconds**: `SessionController.filesIdleMs()` enforces `if (v >= 20L && v <= 600L)`, so `files/idle10` is silently ignored and falls back to `60s` (`auto_disconnect`) or `90s`.
4. **Chinese-only voice quit commands**: `XiaoZhiWs.isQuitCommand()` and MCP tool `self.r1session.quit` only match Chinese phrases (`"退下"`, `"安静"`, `"再见"`, `"拜拜"`, `"下线"`, etc.). Vietnamese quit commands are not recognized locally.

### 2.3 Post-TTS Opening-Word Clipping (`echoGate`)

- After every `onTtsStop()`, `SessionController` holds uplink muted for `echoGateHoldMs = 1200L` plus `echoClearFrames = 8` frames (`480 ms`) = **~1.65 s minimum** (`max 4.0 s`), hardcoded without runtime flags in `XzBootstrap.java`.
- Live proof (`09:00:16.376 TTS stop` → user started speaking at `09:00:17.447` → `echoGate OPEN` at `09:00:18.041`): the first 4 words (`"Mày biết làm"`) were dropped, leaving only `"Toàn không."`.

### 2.4 AI TTS Volume Louder Than PC Bluetooth A2DP Sink Music

- Verified via live `dumpsys media.audio_flinger`:
  - `com.android.bluetooth` (A2DP Sink, `pid 768`): `Type 3` (`STREAM_MUSIC`), `44100 Hz` stereo, mixer gain **`0 dB / 0 dB`**.
  - `com.phicomm.speaker.device` (`TtsPlayer`, `pid 7236`): `Type 3` (`STREAM_MUSIC`), `16000 Hz` mono, mixer gain **`0 dB / 0 dB`**.
- Both render onto the same `STREAM_MUSIC` bus with zero per-track attenuation in `TtsPlayer.java`.
- Because Xiaozhi Cloud TTS is heavily loudness-compressed (near `0 dBFS`) while PC/YouTube/Spotify stereo music averages `-14 LUFS`, AI speech sounds ~8–12 dB louder than Bluetooth music even when Windows volume is at 100%.

### 2.5 Unused Chinese NAS MediaServer Overhead

- `myMusic` defaults to `true`, advertising **37 Chinese MCP tools** on every WebSocket handshake and attempting to fetch the author's hardcoded LAN URL (`http://192.168.2.189:8100/index.json`, causing a 6-second `SocketTimeoutException`).
- Disabling via `files/my_music_off` reduces the MCP tool list to **9 tools** (lights, alarms, session quit).

### 2.6 Wake-Word Latency & Coexistence Notes

- Wake-word is `"小智小智"` (verified in `logcat`, achieving strong scores such as `-4.33` vs `-9.1` threshold).
- Cold restart of `com.phicomm.speaker.device` takes ~5–10 seconds for Unisound to reload `asrfix.dat` (28.8 MB) and compile the wakeup grammar.
- On wake from `IDLE`, `UscTap.onWakeup()` waits a hardcoded `ASR_ENTER_DELAY_MS = 400 ms` after stock Unisound's `closeIn` before invoking `engine.enterASR()`, plus ~500–700 ms WebSocket handshake.
- `MusicResume.java` only pauses/resumes internal/DLNA playback via `msgcenter` (`EchoService`); it does **not** send Bluetooth AVRCP `PAUSE (70)` / `PLAY (68)` to pause/resume a connected PC.

---

## 3. Current Tuned Runtime State on Live Speaker (`192.168.88.66`)

Already applied and verified persistent in `/data/data/com.phicomm.speaker.device/files/`:

| Flag / Setting | Active Value | Effect |
|---|---|---|
| `gain4` (with `alc_on` removed) | `gain = 4`, `alc = false` | Clean voice pickup (`0.001%` clipping, `+20 dB` SNR, 9/9 Vietnamese STT pass) |
| `idle20` + `auto_disconnect` | `20s` + disconnect beep | Auto-closes session after 20s silence (minimum allowed by stock V2.0.6 binary) |
| `my_music_off` | `myMusic = false` | Drops 28 Chinese NAS tools (37 → 9 tools) and removes `192.168.2.189` timeout |
| `music_pause_tts` | `musicPause = true` | Pauses internal/DLNA playback during TTS |
| `uplink_dump` | Removed | Cleaned up after WAV capture to avoid flash writes |

---

## 4. Planned Source-Code Modifications for Forked Repo

All target source files live under `04-build-scripts/device/v2src/cn/workbuddy/r1client/` (and `04-build-scripts/player/` for Web UI localization if desired).

### Priority 1 — Directly Fixes Remaining User Pain Points

| # | Goal | Exact File & Symbol | Current Behavior | Proposed Change |
|---|---|---|---|---|
| **P1** | **Allow 10s (5–600s) idle auto-disconnect** | `SessionController.java` → `filesIdleMs()` (line ~217) & `idleWindowMs()` (line ~243) | Rejects `files/idle<N>` if `v < 20L`; default `auto_disconnect` is `60s`. | Lower minimum bound to **`v >= 5L && v <= 600L`** (and optionally default `auto_disconnect` to `10s` or `15s` when no `idle<N>` file exists). |
| **P2** | **Independent AI TTS volume scaling** | `TtsPlayer.java` → `start()` (line ~137) + `WebConsole.java` + `XzBootstrap.java` | `AudioTrack` created on `STREAM_MUSIC` at fixed `1.0f` gain (`0 dB`), louder than PC Bluetooth music. | Add configurable `public static volatile float ttsGain = 0.5f;` (persisted via `files/tts_vol`, range `10..100`%), applied live via `track.setStereoVolume(g, g)` (API 21/22 safe) or `setVolume(g)`. Expose slider/input in Web Console `/api/set?ttsVol=50`. *(Note: keep `wakeAck` / disconnect cue scaled consistently or separately).* |
| **P3** | **Clean default audio & server config** | `XzBootstrap.java` (lines ~394–440) | Defaults to `gain=16`, `myMusic=true`, `auto_disconnect=false`, hardcoded NAS IP `192.168.2.189`. | Change defaults to **`gain=4`**, `alc=false`, **`auto_disconnect=true` (`idle=10s`)**, **`myMusic=false`**. |
| **P4** | **Vietnamese voice quit commands** | `XiaoZhiWs.java` → `isQuitCommand()` (line ~903) & `addSessionTools()` | Only matches Chinese phrases (`退下`, `再见`, `安静`...). | Add Vietnamese quit phrases (`"tạm biệt"`, `"im đi"`, `"thôi nhé"`, `"ngủ đi"`, `"tắt đi"`, `"không cần nữa"`, `"nghỉ đi"`, `"dừng lại"`) with blockers for music/volume queries, and update `self.r1session.quit` tool description to mention Vietnamese/English commands. |

### Priority 2 — Quality-of-Life & Coexistence Enhancements

| # | Goal | Exact File & Symbol | Current Behavior | Proposed Change |
|---|---|---|---|---|
| **P5** | **Reduce post-TTS dead zone (`echoGate`)** | `SessionController.java` → `echoGateHoldMs` (line ~426), `echoClearFrames` (line ~406), `XzBootstrap.java` | Hardcoded `1200 ms` hold + `8 × 60 ms = 480 ms` (~1.66 s total lock after every TTS). | Make `echoGateHoldMs` and `echoClearFrames` configurable via `files/echoholdms<N>` (e.g. test `500–800 ms` + `4` frames = `~0.75–1.0 s` with lower TTS volume from P2, since lower TTS loudness reduces acoustic echo tail!). |
| **P6** | **PC Bluetooth AVRCP Hard-Pause / Auto-Resume** | `MusicResume.java` + Bluetooth bridge (see `docs/re/bluetooth-avrcp-contract.md`) | Only pauses internal/DLNA player via `msgcenter`; ignores Bluetooth A2DP Sink playback from PC. | Request `AUDIOFOCUS_GAIN_TRANSIENT` + send AVRCP `PAUSE (70)` (`keyState 0→1`) via `BluetoothAvrcpController` (Profile ID `11`) when AI session starts while A2DP Sink is `STATE_PLAYING (10)`; abandon focus + send AVRCP `PLAY (68)` when session returns to `IDLE`. |
| **P7** | **Web Console localization (VI / EN)** | `WebConsole.java` & `04-build-scripts/device/ui/ui.html` + `04-build-scripts/player/` | Entire UI on `:8080` / `:8088` is in Chinese. | Localize core settings, Bluetooth panel, activation card, and controls (including the new **TTS Volume** and **Idle Timeout** controls) to Vietnamese or English. |

---

## 5. Transferable Hardware Findings for `phicomm-control`

Even if focusing on the forked Xiaozhi client first, three empirical hardware facts discovered during this audit should be preserved for any standalone Android work on Phicomm R1:

1. **ARM32 Sign-Extended Handle in `Uni4micHalJNI.openAudioIn(2)`**: Returns a 32-bit native pointer sign-extended to a negative signed `long` (e.g. `0xFFFFFFFFB7D3E438`). Code **must** check `handle == 0L` (not `handle <= 0L`) and determine success via `startRecorder(handle) == 0`. *(Affects `app/src/main/java/com/phicomm/r1/control/audio/FourMicCaptureSource.kt` lines 50, 67, 92.)*
2. **User-Space Busy-Spin in `Uni4micHalJNI.readData()`**: Blocks by polling in user-space (~98% of 1 CPU core) unless paced by the caller at the 2-channel 16 kHz PCM16 production rate (`64,000 B/s`, `2,400 B` per `37.5 ms`).
3. **Audio HAL Standby Keep-Alive**: Without an active output stream, the R1 Audio HAL enters standby after `3000 ms`, which can power down input paths; keeping a lightweight silence `AudioTrack` active during capture prevents standby dropouts.
