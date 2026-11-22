<!--
Sync Impact Report
- Version change: [template] -> 1.0.0 (Initial Ratification)
- Modified principles: Replaced all template placeholders with 5 project-specific core principles.
- Added sections:
  - Core Principles (I. Spec-First Development, II. Evidence-Based Reverse Engineering, III. Single-APK Hardware-Native Efficiency, IV. Deterministic Audio & Bluetooth Coexistence, V. Testability, Observability & Clean Hygiene)
  - Target Platform & Technical Constraints
  - Development Workflow & Quality Gates
  - Governance
- Removed sections: None (template structure preserved).
- Follow-up TODOs: None.
-->

# Phicomm R1 Control Constitution

## Core Principles

### I. Spec-First Development (NON-NEGOTIABLE)

Every feature, architectural change, and reverse-engineering audit MUST follow the SpecKit Spec-Driven Development (SDD) workflow: `specify` -> `clarify` -> `plan` -> `tasks` -> `analyze` -> `implement`. Production code MUST NOT be written without a ratified specification, implementation plan, and dependency-ordered task list. Defects MUST follow the `speckit-bug-*` lifecycle (`assess` -> `fix` -> `test`). Rationale: Phicomm R1 integrates low-level JNI, ALSA, Android 5.1 hidden Bluetooth APIs, and real-time audio streaming; disciplined specs prevent ad-hoc regressions and undocumented coupling.

### II. Evidence-Based Reverse Engineering

Every assumption about stock Phicomm R1 firmware, JNI method signatures (`Uni4micHalJNI`, `JniAsrFix`), ALSA hardware routing (`RK_MA4`, `RK_AK7755`), Android 5.1 Bluetooth A2DP Sink / AVRCP APIs, or system package dependencies MUST be verified against live device dumps, symbol tables, decompiled stock APKs, or `logcat` captures before design approval. Unverified assumptions MUST be explicitly marked `NEEDS CLARIFICATION` and resolved during Phase 0 research. Rationale: Undocumented proprietary libraries fail silently or crash natively when called with guessed contracts.

### III. Single-APK Hardware-Native Efficiency

The deliverable MUST be a single, clean, standalone Android APK targeting Android 5.1.1 (API 22, `armeabi-v7a`) that directly invokes the speaker's on-device native 4-mic beamforming, hardware AEC, and offline `"Xiao Xun Xiao Xun"` wake-word libraries without running legacy Phicomm apps in parallel. Stock Phicomm apps MUST be disabled non-destructively (e.g., `pm hide`) so the device remains recoverable. Resource consumption MUST stay within the RK3229 Cortex-A7 / 512 MB RAM envelope (target `< 80 MB` RSS in idle listening). Rationale: Running parallel legacy services exhausts memory and causes `EchoService` to tear down Bluetooth connections.

### IV. Deterministic Audio & Bluetooth Coexistence

Audio routing across PC Bluetooth A2DP Sink, AI voice interaction (Xiaozhi Cloud), and internet music playback MUST be governed by a single authoritative Audio Arbitration State Machine (`IDLE`, `BT_PLAYING`, `AI_LISTENING`, `AI_SPEAKING`, `MUSIC_PLAYING`). When the wake-word triggers during Bluetooth playback, the system MUST hard-pause Bluetooth (AVRCP Pause + AudioFocus) and automatically resume Bluetooth once the AI interaction returns to idle (unless superseded by music playback). Rationale: Android 5.1 AudioFlinger and A2DP Sink concurrency breaks unpredictably without centralized state ownership.

### V. Testability, Observability & Clean Hygiene

Business logic, state machines, Xiaozhi WebSocket/MCP protocol framing, and audio packetization MUST be decoupled from Android framework and JNI bindings so they are covered by automated JVM unit tests. Runtime transitions MUST emit structured `logcat` telemetry for ADB diagnostics. All repository documentation, specs, code comments, and commit messages MUST be written in English. Proprietary Phicomm/Unisound binaries, pulled APKs, `.dex`/`.odex` files, decompiled source trees, and raw audio dumps MUST remain strictly gitignored and NEVER be committed to Git.

## Target Platform & Technical Constraints

- **Hardware:** Phicomm R1 (`rk322x-box`, Rockchip RK3229 quad-core ARM Cortex-A7, `armeabi-v7a`, 512 MB RAM, 4-mic circular array `RK_MA4` + `AK7755` DSP codec).
- **Operating System:** Stock Android `5.1.1` (API `22`); `minSdk 21`, native ABI `armeabi-v7a`.
- **Voice Frontend:** Direct invocation of `/system/lib/libUni4micHalJNI.so` (4-mic + 2-channel hardware AEC loopback, 16 kHz PCM output) and `/system/lib/libasrfix.so` (stock offline `"Xiao Xun Xiao Xun"` wake-word engine).
- **AI & Music Backend:** Xiaozhi Cloud (`https://api.tenclass.net/xiaozhi/ota/` activation + full-duplex WebSocket Opus streaming + MCP JSON-RPC 2.0) for Q&A and internet/server music playback.
- **Management Surface:** A clean-slate, minimal LAN Web Dashboard (`http://<speaker-ip>:8080`) limited to essential controls: live audio/AI state, Bluetooth pairing & connect/disconnect, Xiaozhi Cloud status & 6-digit activation code display, and master volume. Legacy features from prior iterations (Gemini API, DNS hijack VPN, CPU/RAM widgets) are explicitly excluded.

## Development Workflow & Quality Gates

- **Constitution Gate:** Every feature `plan.md` MUST pass a Constitution Check before Phase 0 research and again after Phase 1 design; any violation requires explicit justification in Complexity Tracking.
- **Automated Verification Gate:** `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` MUST pass with zero errors prior to feature completion.
- **Device Verification Gate:** Hardware-facing features (4-mic/AEC capture, wake-word triggering under Bluetooth playback, AVRCP pause/resume, Xiaozhi end-to-end streaming, music playback, and Basic Web Dashboard) MUST be validated on the live Phicomm R1 over ADB before closing tasks.
- **Clean Repository Gate:** `git status` and `git diff` MUST be verified before commits to ensure zero proprietary binaries, dumps, or secrets are staged.

## Governance

This constitution supersedes ad-hoc practices and guides all specification, planning, and implementation reviews. Amendments require updating `.specify/memory/constitution.md` via the `speckit-constitution` workflow with a semantic version bump (`MAJOR` for breaking principle changes or removals, `MINOR` for new principles or expanded guidance, `PATCH` for clarifications), an updated `Last Amended` date, and explicit user approval. Runtime operational guidance for AI agents is maintained in `AGENTS.md` at the repository root and MUST stay aligned with this constitution.

**Version**: 1.0.0 | **Ratified**: 2026-11-22 | **Last Amended**: 2026-11-22
