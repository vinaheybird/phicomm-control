---

description: "Actionable, dependency-ordered task list for Phicomm R1 Stock Firmware Reverse-Engineering Audit"
---

# Tasks: Phicomm R1 Stock Firmware Reverse-Engineering Audit

**Input**: Design documents from `/specs/001-r1-re-audit/`
**Prerequisites**: [plan.md](file:///D:/AGY/phicomm-control/specs/001-r1-re-audit/plan.md) (required), [spec.md](file:///D:/AGY/phicomm-control/specs/001-r1-re-audit/spec.md) (required), [research.md](file:///D:/AGY/phicomm-control/specs/001-r1-re-audit/research.md), [quickstart.md](file:///D:/AGY/phicomm-control/specs/001-r1-re-audit/quickstart.md)

**Tests**: Verification is performed by cross-checking every documented contract signature, constant, and path against empirical `dexdump.exe`, `aapt.exe`, ELF symbol, and live ADB outputs, plus running the `quickstart.md` verification gates.

**Organization**: Tasks are grouped by user story (`US1`–`US4`) so each engineering contract can be extracted, verified, and reviewed independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`US1`, `US2`, `US3`, `US4`)
- All proprietary dumps remain strictly inside gitignored `re_dumps/`; all tracked contracts live in `docs/re/`.

---

## Phase 1: Setup (Local RE Workspace)

**Purpose**: Create tracked `docs/re/` directory and gitignored `re_dumps/` directory tree.

- [x] T001 Create directory structure `docs/re/` and gitignored `re_dumps/{apks,libs,config,dumps}` at repository root

---

## Phase 2: Foundational (Read-Only Device Extraction)

**Purpose**: Pull required stock APKs, `.odex` files, native `.so` libraries, hardware configs, and live `dumpsys` snapshots from the Phicomm R1 (`192.168.88.66:5555`) into gitignored `re_dumps/` without modifying `/system`.

**⚠️ CRITICAL**: No contract authoring (US1–US4) can begin until the relevant raw artifacts are pulled locally.

- [x] T002 Pull core stock APKs and `.odex` files (`Unisound.apk`, `EchoService.apk`, `Bluetooth.apk`, `Bluetooth.odex`, `Launcher.apk`, `Launcher.odex`) and secondary `com.phicomm.speaker.*` APKs (`AirSkill.apk`, `NetControl.apk`, `IJetty.apk`, `SystemTool.apk`, `OtaService.apk`, `ProductionTest.apk`, `BugReport.apk`, `ExceptionReporter.apk`) into `re_dumps/apks/`
- [x] T003 [P] Pull stock 4-mic & wake-word native libraries (`/system/lib/libUni4micHalJNI.so`, `libuni4michal.so`, `libasrfix.so`, `libuscasr.so`, `libopus.so`) and `/system/framework/arm/boot.oat` (or `framework.jar`) Bluetooth classes into `re_dumps/libs/`
- [x] T004 [P] Pull stock hardware configuration files (`/system/usr/uni_4mic_config/*`, `/system/etc/audio_policy.conf`, `/system/etc/audio_effects.conf`) and capture live `dumpsys bluetooth_manager`, `dumpsys audio`, and `pm list packages -f` into `re_dumps/config/` and `re_dumps/dumps/`

**Checkpoint**: All raw stock artifacts reside in gitignored `re_dumps/` and `git status` shows zero proprietary files.

---

## Phase 3: User Story 1 - 4-Microphone Array & Hardware AEC Contract Extraction (Priority: P1) 🎯 MVP

**Goal**: Produce a complete, empirically verified contract for `com.unisound.jni.Uni4micHalJNI`, `FourMicAudioManager`, ALSA `RK_MA4` capture, hardware 2-channel AEC loopback, and DOA retrieval.

**Independent Test**: Every method signature, parameter type, return type, call sequence, buffer size, and config path in `docs/re/4mic-hal-contract.md` matches `dexdump.exe` output from `Unisound.apk` and `/system/usr/uni_4mic_config/*`.

### Implementation for User Story 1

- [x] T005 [US1] Disassemble `com.unisound.jni.Uni4micHalJNI`, `com.unisound.client.FourMicAudioManager`, `com.unisound.client.IAudioSourceAEC`, and `com.unisound.vui.engine.ANTAudioSourceImpl` from `re_dumps/apks/Unisound.apk` using `dexdump.exe` into `re_dumps/dumps/unisound_4mic_dex.txt`
- [x] T006 [P] [US1] Inspect all config files in `re_dumps/config/uni_4mic_config/` (`pcm_hw_config.txt`, `MicArrayConfig.ini`, `MicArray_config.txt`) and `libuni4michal.so` symbols to confirm PCM format, channel count, AEC loopback flags, and error codes
- [x] T007 [US1] Author verified English contract in `docs/re/4mic-hal-contract.md` covering exact JNI signatures, Kotlin declaration template, init/open/start/read/stop/release lifecycle, DOA retrieval, hardware AEC loopback behavior, and error handling

**Checkpoint**: `docs/re/4mic-hal-contract.md` is complete with zero guessed signatures and ready to drive Feature `002`.

---

## Phase 4: User Story 2 - Offline "Xiao Xun Xiao Xun" Wake-Word Contract Extraction (Priority: P1)

**Goal**: Produce a complete, empirically verified contract for `cn.yunzhisheng.asrfix.JniAsrFix` (`libasrfix.so`), stock `"Xiao Xun Xiao Xun"` model assets, and a Git-clean on-device asset loading strategy.

**Independent Test**: Every native method signature, initialization option, asset path, MD5/package constraint, and wakeup result parser detail in `docs/re/wakeup-engine-contract.md` matches `dexdump.exe` output from `Unisound.apk` and `libasrfix.so`.

### Implementation for User Story 2

- [x] T008 [US2] Disassemble `cn.yunzhisheng.asrfix.JniAsrFix` and all `cn.yunzhisheng.asrfix.*` / wakeup engine caller classes from `re_dumps/apks/Unisound.apk` using `dexdump.exe` into `re_dumps/dumps/unisound_asrfix_dex.txt`
- [x] T009 [P] [US2] Inspect `assets/asrfix/data`, `assets/wakeup/data`, `assets/version/data`, `assets/config/config.mg`, and `libasrfix.so` initialization/validation strings to verify asset layout, permissions on `/system/app/Unisound/Unisound.apk` (`0644`), and any package/checksum constraints
- [x] T010 [US2] Author verified English contract in `docs/re/wakeup-engine-contract.md` covering exact `JniAsrFix` JNI signatures, initialization options, wakeup feeding/polling loop, result JSON/string format, and Git-clean runtime extraction of model assets from `/system/app/Unisound/Unisound.apk`

**Checkpoint**: `docs/re/wakeup-engine-contract.md` is complete and confirms zero proprietary model files need to be committed to Git.

---

## Phase 5: User Story 3 - Bluetooth A2DP Sink & AVRCP Coexistence Contract Extraction (Priority: P2)

**Goal**: Produce a verified contract for A2DP Sink (Profile 10), playback state detection, AVRCP Pause/Play remote commands, and AudioFocus / `audio_policy.conf` behavior on Phicomm R1 Android 5.1.1.

**Independent Test**: Every profile constant, hidden reflection method signature, broadcast action, and AVRCP pass-through key code in `docs/re/bluetooth-avrcp-contract.md` matches carved DEX from `Bluetooth.odex`/`boot.oat`, `EchoService.apk`, and live `dumpsys bluetooth_manager` / `dumpsys audio`.

### Implementation for User Story 3

- [x] T011 [US3] Carve embedded DEX from `re_dumps/apks/Bluetooth.odex` and framework Bluetooth classes (`BluetoothA2dpSink`, `BluetoothAvrcpController`) and disassemble with `dexdump.exe` into `re_dumps/dumps/bluetooth_dex.txt`
- [x] T012 [P] [US3] Inspect `re_dumps/apks/EchoService.apk` Bluetooth/AudioFocus classes via `dexdump.exe`, plus `re_dumps/config/audio_policy.conf` and `re_dumps/dumps/dumpsys_bluetooth.txt`, into `re_dumps/dumps/echoservice_bt_dex.txt`
- [x] T013 [US3] Author verified English contract in `docs/re/bluetooth-avrcp-contract.md` documenting A2DP Sink Profile ID & methods, AVRCP Controller Profile ID & `sendPassThroughCmd` (Pause/Play keycodes) signatures, broadcast intents for connection/playing state, AudioFocus interactions, and `EchoService` BT interference root cause

**Checkpoint**: `docs/re/bluetooth-avrcp-contract.md` is complete and ready to drive Feature `003`.

---

## Phase 6: User Story 4 - Stock Package Inventory & Safe Non-Destructive Disable Plan (Priority: P3)

**Goal**: Classify all 11 preinstalled `com.phicomm.speaker.*` packages plus legacy `com.phicomm.gemini` with manifest evidence and reversible `pm hide` / `pm unhide` scripts.

**Independent Test**: All 11 `com.phicomm.speaker.*` packages present on the live device appear in `docs/re/stock-packages-inventory.md` with manifest-backed responsibilities, boot receivers, native libs, and verified hide/restore commands.

### Implementation for User Story 4

- [x] T014 [US4] Extract decoded `AndroidManifest.xml` (package name, permissions, services, boot receivers, exported components) for all 11 stock `com.phicomm.speaker.*` APKs in `re_dumps/apks/` using `aapt.exe` into `re_dumps/dumps/stock_manifests.txt`
- [x] T015 [US4] Author verified English inventory & safety plan in `docs/re/stock-packages-inventory.md` classifying every stock package (Hide / Keep / Replace), noting Wi-Fi/Bluetooth/audio/LED/button dependencies, documenting safe non-destructive `pm hide` and `pm unhide` rollback procedures, and noting cleanup of legacy `com.phicomm.gemini`

**Checkpoint**: All 4 engineering contracts under `docs/re/` are complete and independently verified.

---

## Phase 7: Polish & Cross-Cutting Verification

**Purpose**: Validate all `quickstart.md` gates, cross-check consistency across all 4 `docs/re/*.md` contracts, and confirm strict Git hygiene and device read-only integrity.

- [x] T016 Run all `specs/001-r1-re-audit/quickstart.md` verification commands (file existence, zero `TBD`/`TODO` placeholders, `git status` clean of `re_dumps/` proprietary files, live device package state unchanged)
- [x] T017 Update `README.md` roadmap status for `001-r1-re-audit` to `Completed` once all contracts pass verification

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Starts immediately (`T001`).
- **Foundational (Phase 2)**: Depends on `T001`; `T002`, `T003`, `T004` can execute in parallel.
- **User Stories (Phases 3–6)**: All depend on Phase 2 completion; `US1` (`T005`–`T007`), `US2` (`T008`–`T010`), `US3` (`T011`–`T013`), and `US4` (`T014`–`T015`) target independent output files under `docs/re/` and can execute in parallel or priority order (`US1`/`US2` $\rightarrow$ `US3` $\rightarrow$ `US4`).
- **Polish (Phase 7)**: Depends on Phases 3–6 (`T016`–`T017`).

### Parallel Example

```text
# Phase 2 parallel extraction:
T002 (Pull APKs & .odex) || T003 (Pull .so & boot.oat) || T004 (Pull configs & dumpsys)

# Phases 3-6 parallel disassembly:
T005 (Uni4mic DEX) || T008 (JniAsrFix DEX) || T011 (Bluetooth OAT DEX) || T014 (aapt manifests)
```

### Implementation Strategy

- **MVP First**: Complete Phase 1 + Phase 2 + US1 (`4mic-hal-contract.md`) + US2 (`wakeup-engine-contract.md`) to lock down the core 4-mic & wake-word contracts.
- **Full Audit Completion**: Complete US3 (`bluetooth-avrcp-contract.md`) + US4 (`stock-packages-inventory.md`) + Phase 7 verification so Features `002` and `003` have 100% empirical contracts before any Android code is scaffolded.
