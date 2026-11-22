# Feature Specification: Phicomm R1 Stock Firmware Reverse-Engineering Audit

**Feature Branch**: `001-r1-re-audit`

**Created**: 2026-11-22

**Status**: Completed

**Input**: User description: "Reverse-engineer the stock Phicomm R1 system packages, native 4-mic/AEC libraries, offline wake-word engine, and Bluetooth/audio services from the live speaker via ADB to produce verified, English technical contracts under docs/re/ before building the clean-slate single APK."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 4-Microphone Array & Hardware AEC Contract Extraction (Priority: P1)

As the project developer building a clean-slate voice assistant APK, I need a complete, empirically verified technical contract describing how the Phicomm R1 initializes, configures, reads from, and releases its 4-microphone far-field array with 2-channel hardware Acoustic Echo Cancellation (AEC), so that downstream audio capture preserves full far-field recognition quality without running legacy Phicomm applications.

**Why this priority**: The primary reason for rewriting the Phicomm R1 software instead of using generic open-source clients is preserving 4-mic beamforming and hardware AEC. Without exact verified call sequences, parameters, and audio formats, downstream voice capture cannot be built reliably.

**Independent Test**: Can be fully verified by inspecting the resulting 4-mic contract document and cross-checking every documented method signature, parameter order, return type, configuration file path, and PCM format against the extracted stock binaries and decompiled classes from the live speaker.

**Acceptance Scenarios**:

1. **Given** a live Phicomm R1 accessible over ADB, **When** the stock voice package and on-device 4-mic native libraries are audited, **Then** a contract document specifies the exact class name, method signatures, initialization order, sample rate, channel layout, buffer sizing, DOA retrieval, and teardown lifecycle required to capture AEC-cleaned PCM audio.
2. **Given** the on-device hardware configuration files for the 4-mic array, **When** those configuration files and ALSA device nodes are inspected, **Then** the contract documents every required file path, permission constraint, and hardware loopback assumption needed for AEC to operate during Bluetooth and local speaker playback.

---

### User Story 2 - Offline "Xiao Xun Xiao Xun" Wake-Word Contract Extraction (Priority: P1)

As the project developer, I need a verified technical contract for the stock offline wake-word engine and its acoustic/grammar assets, so that a standalone clean APK can continuously listen for `"Xiao Xun Xiao Xun"` with minimal CPU and memory overhead and without any cloud dependency or package-signature failure.

**Why this priority**: Offline wake-word detection is the entry trigger for every AI interaction. Discovering any hidden package-name check, asset checksum validation, initialization sequence, or audio framing requirement up front prevents fatal native crashes in Feature `002`.

**Independent Test**: Can be fully verified by reviewing the wake-word contract document and confirming every documented native method, asset file, extraction/loading path, initialization flag, and wakeup result format matches the stock decompiled classes and native symbol tables.

**Acceptance Scenarios**:

1. **Given** the stock voice package pulled from the live Phicomm R1, **When** its wake-word engine wrapper and native library are decompiled and inspected, **Then** the contract documents the exact native bridge class, method signatures, required model asset files, runtime file paths, and initialization parameters.
2. **Given** the native wake-word library's internal validation checks, **When** package-name, signature, and acoustic-model integrity checks are audited, **Then** the contract explicitly states all constraints a new standalone APK must satisfy (including whether specific asset files must be copied from the device or bundled, and whether any package ID constraint applies).

---

### User Story 3 - Bluetooth A2DP Sink & AVRCP Coexistence Contract Extraction (Priority: P2)

As the project developer, I need a verified contract detailing how the Phicomm R1's Android 5.1.1 Bluetooth stack and legacy media services manage Bluetooth A2DP Sink connections, playback state detection, and AVRCP Pause/Play commands, so that the new application can deterministically hard-pause PC audio on wake-word and auto-resume after AI responses finish.

**Why this priority**: Running PC Bluetooth speaker mode alongside AI Q&A and music playback requires reliable playback detection and remote pause/resume commands on Android 5.1.1 where A2DP Sink and AVRCP Controller APIs are hidden system APIs.

**Independent Test**: Can be fully verified by inspecting the Bluetooth contract document and confirming all documented profile IDs, hidden API signatures, broadcast intents, AudioFocus behaviors, and audio policy constraints match the live speaker's Bluetooth package, legacy media service, and audio policy configuration.

**Acceptance Scenarios**:

1. **Given** the stock Bluetooth package and legacy player package on the live Phicomm R1, **When** their Bluetooth A2DP Sink and AVRCP control paths are audited, **Then** the contract documents the exact profile constants, hidden reflection methods (with parameter and return types), and broadcast actions used to detect connection/playback state and send Pause/Play commands to a connected PC.
2. **Given** the device's audio policy configuration and AudioFlinger behavior, **When** audio stream routing and focus behavior are audited, **Then** the contract documents any hardware or policy limitations affecting simultaneous or sequential A2DP Sink and local audio output.

---

### User Story 4 - Stock Package Inventory & Safe Non-Destructive Disable Plan (Priority: P3)

As the project developer, I need a complete inventory of all preinstalled Phicomm packages and their runtime responsibilities (especially `EchoService`, `Unisound`, `Launcher`, `NetControl`, and auxiliary services), along with a verified, reversible disable and rollback procedure, so that conflicting legacy apps can be safely hidden without breaking Wi-Fi, Bluetooth, ADB, or audio hardware.

**Why this priority**: Legacy Phicomm services consume RAM and actively interfere with Bluetooth connections (e.g., `EchoService` disconnecting A2DP), but blindly disabling system packages risks losing Wi-Fi connectivity, boot stability, or hardware audio initialization.

**Independent Test**: Can be fully verified by reviewing the package inventory contract and confirming every preinstalled `com.phicomm.speaker.*` package on the device is classified (Hide / Keep / Replace), with its boot receivers, hardware dependencies, and exact reversible ADB commands documented.

**Acceptance Scenarios**:

1. **Given** the complete list of packages installed on the live Phicomm R1, **When** each Phicomm package's manifest, services, receivers, and native libraries are audited, **Then** a system inventory report classifies every package as Safe to Hide, Keep Active, or Replaced by New APK, with explicit evidence for each classification.
2. **Given** the legacy test application currently installed in user space, **When** cleanup and rollback procedures are documented, **Then** the contract includes exact non-destructive hide commands, full rollback/restore commands, and post-reboot verification steps.

---

### Edge Cases

- What if the decompiled stock wake-word library requires an acoustic model file (`28.8 MB`) that cannot be redistributed in Git? The audit must document a clean on-device extraction/reuse path (reading directly from `/system/app/Unisound/Unisound.apk` or device storage at runtime/install time) so proprietary models are never committed to Git.
- What if stock `Bluetooth.apk` on Android 5.1.1 is odexed (`.odex` without `classes.dex`)? The audit must handle odexed system APKs cleanly (e.g., via `oat2dex` / `baksmali` / live `dumpsys` + `BluetoothAdapter`/`BluetoothProfile` reflection inspection on device) rather than failing on missing `classes.dex`.
- What if ADB Wi-Fi disconnects or the speaker IP changes during extraction? All audit steps must be idempotent and re-runnable against a specified ADB target address.
- What if proprietary APKs, `.dex`/`.odex`, `.so`, or decompiled Java files are generated locally during analysis? Every proprietary dump must remain strictly inside gitignored `re_dumps/` or `decompiled/` directories so `git status` stays completely clean of proprietary assets.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The audit MUST pull the relevant stock Phicomm R1 packages and configuration files from the live device into a gitignored local workspace (`re_dumps/`) without modifying `/system` on the speaker.
- **FR-002**: The audit MUST produce a verified English contract document (`docs/re/4mic-hal-contract.md`) documenting the exact 4-mic hardware array JNI class, every native method signature, initialization/read/stop/release sequence, PCM audio format (sample rate, bit depth, channel count, frame size), DOA angle API, and hardware AEC loopback configuration.
- **FR-003**: The audit MUST produce a verified English contract document (`docs/re/wakeup-engine-contract.md`) documenting the exact offline `"Xiao Xun Xiao Xun"` wake-word native bridge class, method signatures, required model/grammar assets, initialization options, wakeup callback/result payload format, and any package-name, checksum, or runtime path constraints.
- **FR-004**: The audit MUST produce a verified English contract document (`docs/re/bluetooth-avrcp-contract.md`) documenting A2DP Sink profile management, playback-state detection broadcasts/callbacks, AVRCP Pause/Play command invocation methods available on the R1's Android 5.1.1 stack, and AudioFocus / audio policy interactions.
- **FR-005**: The audit MUST produce a verified English system inventory and deployment safety report (`docs/re/stock-packages-inventory.md`) classifying every `com.phicomm.speaker.*` package on the device, documenting its boot receivers, native libraries, and hardware dependencies, and providing exact non-destructive `pm hide` and rollback (`pm unhide`) procedures.
- **FR-006**: Every documented contract item (class name, method signature, constant, file path, broadcast action, package classification) MUST cite its empirical source (specific stock APK class, native library symbol, system config file, or live ADB command output).
- **FR-007**: All generated documentation MUST be written in clear technical English and zero proprietary binaries, pulled APKs, `.so` files, `.dex`/`.odex` files, or decompiled Java source trees may be tracked by Git.

### Key Entities

- **Hardware Audio Contract (`4mic-hal-contract.md`)**: Authoritative specification of the 4-mic + 2-channel hardware AEC capture pipeline, configuration files, and lifecycle state transitions.
- **Wake-Word Engine Contract (`wakeup-engine-contract.md`)**: Authoritative specification of the offline `"Xiao Xun Xiao Xun"` recognition engine, model asset sourcing strategy, and wakeup event format.
- **Bluetooth Coexistence Contract (`bluetooth-avrcp-contract.md`)**: Authoritative specification of A2DP Sink + AVRCP Controller hidden APIs, broadcast intents, and AudioFocus behavior on Phicomm R1 Android 5.1.1.
- **Stock Package Inventory (`stock-packages-inventory.md`)**: Complete classification table of all preinstalled Phicomm packages with boot/hardware dependencies and reversible disable/rollback commands.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of native bridge methods required for 4-mic capture (`Uni4micHalJNI`) and offline wake-word detection (`JniAsrFix`) have verified parameter types, return types, and call ordering documented with zero `TBD` or guessed signatures.
- **SC-002**: A clear, Git-clean strategy for loading the stock `"Xiao Xun Xiao Xun"` acoustic and wakeup models on-device without committing proprietary model files is documented and verified against device permissions.
- **SC-003**: At least one empirically verified mechanism for detecting PC Bluetooth playback state and issuing AVRCP Pause/Play commands on the R1's Android 5.1.1 stack is documented with exact API signatures and broadcast actions.
- **SC-004**: 100% of `com.phicomm.speaker.*` packages present on the live device (11 stock packages) are inventoried, classified, and paired with reversible disable and rollback commands.
- **SC-005**: `git status` confirms zero proprietary binaries, pulled APKs, `.dex`/`.odex` files, or decompiled trees are staged or untracked outside `.gitignore`, and all produced reports under `docs/re/` are written in English.

## Assumptions

- The live Phicomm R1 speaker remains reachable over ADB Wi-Fi (currently discovered at `192.168.88.66:5555`) during the audit.
- This feature is strictly a **read-only reverse-engineering and contract-documentation audit**; it does NOT modify `/system`, hide stock packages, or scaffold production Android application code (those belong to Features `002` and `003`).
- Offline decompilation and symbol inspection can be performed using locally available tooling (Java/Python DEX/ELF inspectors and/or portable JADX CLI downloaded into a gitignored tool cache if needed).
- Stock `Unisound.apk` and `EchoService.apk` remain intact on `/system/app/` so runtime model assets can be inspected and, if appropriate, loaded directly from device storage without redistributing proprietary files in Git.
