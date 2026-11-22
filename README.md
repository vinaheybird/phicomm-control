# Phicomm R1 Control — 4-Mic Xiaozhi AI + Bluetooth Coexistence

A clean-slate, single-APK Android application for the **Phicomm R1** smart speaker (Rockchip RK3229, Android 5.1.1 / API 22, 512 MB RAM).

This project replaces the discontinued stock Phicomm services while preserving the speaker's native **4-microphone far-field array, hardware Acoustic Echo Cancellation (AEC), and offline `"Xiao Xun Xiao Xun"` wake-word engine**, connecting directly to **Xiaozhi Cloud** for voice Q&A and internet music playback alongside **PC Bluetooth A2DP Sink** audio.

---

## Architecture Highlights

- **Native 4-Mic + Hardware AEC:** Directly invokes the stock `/system/lib/libUni4micHalJNI.so` pipeline (`RK_MA4` 4-mic + 2-channel hardware AEC loopback) to produce clean 16 kHz PCM even while music is playing loudly.
- **Stock Offline Wake-Word:** Reuses `/system/lib/libasrfix.so` and the stock `"Xiao Xun Xiao Xun"` acoustic/wakeup models for low-CPU, far-field wake-word detection.
- **Xiaozhi Cloud Integration:** Full-duplex Opus WebSocket streaming (`api.tenclass.net` OTA activation + MCP JSON-RPC 2.0) for conversational AI and voice-commanded internet/server music playback.
- **Bluetooth Coexistence:** PC Bluetooth A2DP Sink playback with deterministic AVRCP hard-pause when invoking AI and automatic resume once AI finishes responding.
- **Basic LAN Web Dashboard:** Minimal clean-slate HTTP interface for live status, Bluetooth pairing/connect, Xiaozhi Cloud activation code & status, and master volume.

### Hardware & Firmware Prerequisite (Direct 4-Mic HAL)

On stock Phicomm R1 firmware (`3448`, Android 5.1.1, SELinux `Enforcing`), direct access to `/dev/snd/pcmC1D0c` via `libUni4micHalJNI.so` is restricted by SELinux policy (`u:r:platform_app:s0` signed with Phicomm's `CN=R1` platform key). Running this standalone APK with full 4-mic hardware AEC requires a user-prepared rooted / SELinux-permissive Android 5.1.1 `3448` boot environment that preserves the stock `/system` Unisound libraries and `/system/app/Unisound/Unisound.apk`. See [docs/re/4mic-hal-contract.md](docs/re/4mic-hal-contract.md) (Section 1.4) for the full empirical analysis.

---

## Spec-Driven Development (SpecKit)

This repository is standardized using **SpecKit**. All project rules, governance principles, and feature specifications are maintained in English:

- **Workspace Rules:** [AGENTS.md](AGENTS.md)
- **Project Constitution:** [.specify/memory/constitution.md](.specify/memory/constitution.md)
- **Feature Specifications:** `specs/NNN-<feature-name>/`
- **Reverse-Engineering Contracts:** `docs/re/`

### Feature Roadmap

| Phase / Feature | Description | Status |
| :--- | :--- | :--- |
| **Phase 0** | Governance (`AGENTS.md`, `constitution.md`, `.gitignore`, `README.md`) | Completed |
| `001-r1-re-audit` | Reverse-engineering audit of stock `Unisound.apk`, `EchoService.apk`, `Bluetooth.apk`, and audio/BT contracts | Completed |
| `002-4mic-wakeup-engine` | Single-APK scaffold + `Uni4micHalJNI` 4-mic/AEC + `JniAsrFix` wake-word + Opus 16 kHz encoder | Implemented (`T001`–`T021` verified; `T022` awaiting rooted/permissive `3448` env) |
| `003-bluetooth-audio-arbitration` | Bluetooth A2DP Sink + AVRCP Pause/Resume + Audio Arbitration State Machine + non-destructive stock app disabling | Planned |
| `004-xiaozhi-cloud-client` | Xiaozhi Cloud OTA activation + full-duplex Opus WebSocket + TTS playback + barge-in | Planned |
| `005-basic-web-dashboard` | Minimal clean-slate LAN Web Dashboard (status, BT pair/connect, Xiaozhi activation code, volume) | Planned |
| `006-music-playback-mcp` | Xiaozhi MCP tools + internet/server music playback integrated with Bluetooth arbitration | Planned |
