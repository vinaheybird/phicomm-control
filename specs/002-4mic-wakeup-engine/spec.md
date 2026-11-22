# Feature Specification: 4-Mic Hardware AEC Capture & Offline Wake-Word Engine

**Feature Branch**: `002-4mic-wakeup-engine`

**Created**: 2026-11-22

**Status**: Draft

**Input**: User description: "Build the clean-slate standalone Android application foundation for the Phicomm R1 speaker with native 4-microphone array capture and hardware Acoustic Echo Cancellation (AEC), offline \"Xiao Xun Xiao Xun\" wake-word detection using runtime-extracted on-device models, and 16 kHz mono Opus audio framing ready for Xiaozhi Cloud streaming."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Continuous 4-Mic Far-Field Capture with Hardware AEC (Priority: P1)

As a Phicomm R1 speaker owner, I want the clean standalone application to run persistently in the background and continuously capture far-field voice through the speaker's 4-microphone array with hardware echo cancellation active, so that my voice is picked up cleanly across the room even while music or Bluetooth audio is playing loudly through the speaker.

**Why this priority**: Reliable 4-mic far-field capture with hardware AEC is the core reason for rebuilding the Phicomm R1 software instead of using generic open-source voice clients. Everything downstream (wake-word detection and cloud speech streaming) depends on receiving clean 16 kHz PCM frames.

**Independent Test**: Can be fully tested by deploying the standalone application to the live Phicomm R1 (with the stock voice service disabled), starting the persistent audio engine service, verifying continuous non-zero 16 kHz audio frames and Direction of Arrival (DOA) readings via diagnostics, and confirming zero flash-filling debug recordings and stable memory usage.

**Acceptance Scenarios**:

1. **Given** the standalone application is installed on the Phicomm R1 and conflicting legacy voice services are disabled, **When** the background voice service starts (manually or after device boot), **Then** the 4-microphone array initializes with hardware AEC enabled and continuously streams clean 16 kHz 16-bit PCM audio frames without crashing or writing unbounded debug files to storage.
2. **Given** the speaker is actively playing audio while the 4-microphone capture loop is running, **When** the user speaks from across the room, **Then** the captured stream reflects hardware echo cancellation and exposes a valid Direction of Arrival (DOA) angle (`0°`–`359°`) upon wake-word beam lock.
3. **Given** the background voice service is stopped or restarted, **When** shutdown occurs, **Then** the 4-microphone capture handle and hardware session are cleanly stopped, closed, and released so subsequent restarts succeed without device-busy errors.

---

### User Story 2 - Offline "Xiao Xun Xiao Xun" Wake-Word Detection with Git-Clean Model Extraction (Priority: P1)

As a Phicomm R1 speaker owner, I want to say `"Xiao Xun Xiao Xun"` offline from anywhere in the room and have the speaker detect the wake word reliably within a fraction of a second—even while music is playing—without requiring any cloud wake-word service or bundling proprietary models in the source repository.

**Why this priority**: Offline wake-word detection is the hands-free entry point for every AI interaction. Reusing the stock `"Xiao Xun Xiao Xun"` acoustic model directly from the speaker's read-only system partition delivers maximum accuracy, minimal idle CPU/RAM overhead, and strict intellectual-property hygiene.

**Independent Test**: Can be fully tested on a fresh install of the standalone APK by verifying first-launch extraction and integrity validation of the stock acoustic and wakeup models from the speaker's system storage, speaking `"Xiao Xun Xiao Xun"` in both quiet and music-playing conditions, and observing wake-word detection events with confidence scores, timestamps, and DOA angles in the live diagnostic log.

**Acceptance Scenarios**:

1. **Given** a fresh installation of the standalone application on the Phicomm R1 with no models in app-private storage, **When** the voice service launches for the first time, **Then** it extracts the required acoustic and wakeup model files from the on-device system package into app-private storage, validates their integrity, and skips redundant re-extraction on subsequent launches unless a file is missing or corrupted.
2. **Given** the offline wake-word engine is actively listening to the 4-microphone stream in a quiet room, **When** the user speaks `"Xiao Xun Xiao Xun"`, **Then** a wake-word detection event is emitted with the parsed phrase, confidence score meeting the active threshold, and locked DOA angle, and the engine automatically resumes listening after the capture window completes.
3. **Given** music or speaker audio is playing, **When** the wake-word detector operates in playback-aware mode, **Then** it applies the appropriate playback confidence threshold so `"Xiao Xun Xiao Xun"` remains reliably detectable while rejecting normal speech and background noise.
4. **Given** the wake-word engine receives a low-confidence false trigger below the configured threshold, **When** the result is evaluated, **Then** the trigger is rejected and continuous listening resumes without interrupting playback or emitting a wake-word event.

---

### User Story 3 - Deterministic 16 kHz Mono Opus Framing Pipeline (Priority: P2)

As the developer preparing downstream Xiaozhi Cloud streaming, I want the clean audio pipeline to convert captured 4-microphone PCM into mono 16 kHz 16-bit PCM and encode/decode 60 ms Opus frames reliably, so that Feature `004` can stream voice packets directly over the Xiaozhi WebSocket without audio resampling or framing bugs.

**Why this priority**: Xiaozhi Cloud requires standard 16 kHz mono 60 ms (`960`-sample) Opus frames. Building and unit-testing the PCM channel selector, frame accumulator, and Opus encoder/decoder alongside the 4-mic capture loop guarantees end-to-end audio integrity before adding network complexity.

**Independent Test**: Can be fully tested via automated JVM unit tests verifying PCM stereo-to-mono channel extraction, exact 60 ms (`1,920`-byte) frame accumulation across arbitrary read chunk sizes, and Opus encode/decode round-trip integrity, plus on-device benchmark logging on the Phicomm R1 confirming real-time encoding overhead fits well within the 60 ms frame budget.

**Acceptance Scenarios**:

1. **Given** an incoming stream of 4-microphone capture chunks whose byte length does not align with 60 ms boundaries (such as 37.5 ms chunks), **When** chunks are fed into the PCM frame accumulator, **Then** it outputs exact 60 ms mono 16 kHz 16-bit PCM frames (`960` samples / `1,920` bytes) with zero dropped samples or framing drift.
2. **Given** valid 60 ms mono 16 kHz 16-bit PCM frames, **When** encoded to Opus and decoded back to PCM, **Then** each encoded packet is non-empty, compact, and decodes back to `960` mono samples at 16 kHz with preserved speech energy and real-time processing speed on the Phicomm R1.

---

### Edge Cases

- What if the stock Phicomm voice service is still running when our application starts? The 4-mic capture initializer receives a failure status and MUST report a clear diagnostic error state rather than spinning in an uncontrolled crash loop; recovery retries must use bounded backoff.
- What if app-private model files are partially written (e.g., power cut during first-launch extraction) or fail integrity checks? The model provisioner MUST detect missing/invalid files, extract cleanly via atomic temp-file replacement, and re-verify before initializing the wake-word engine.
- What if the 4-mic capture returns 2-channel interleaved PCM where one channel is silence or raw un-beamformed audio? The channel selector MUST be configurable and empirically verified on-device so only the clean hardware-AEC beamformed mono channel is fed to wake-word recognition and Opus encoding.
- What if the wake-word engine fires twice in rapid succession for a single utterance? A configurable cooldown/debounce window MUST prevent duplicate wake-word triggers for the same spoken trigger phrase.
- What if the speaker reboots unattended? A boot-completed receiver MUST automatically start the persistent foreground voice service so hands-free wake-word listening resumes with zero manual intervention.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a single clean-slate standalone Android application targeting the Phicomm R1 (`minSdk 21`, `armeabi-v7a`) with a persistent foreground service and automatic startup on device boot.
- **FR-002**: The system MUST capture audio exclusively through the Phicomm R1's 4-microphone hardware array with hardware 2-channel AEC active, disabling automatic flash-filling debug WAV dumps by default, and managing clean init, open, start, read, wake-status lock/unlock, DOA retrieval, stop, close, and release transitions.
- **FR-003**: The system MUST provision the stock `"Xiao Xun Xiao Xun"` acoustic and wakeup model assets at runtime by reading them from the speaker's existing system package into app-private storage and validating file integrity before engine initialization, without storing proprietary model binaries in the Git repository.
- **FR-004**: The system MUST run continuous offline `"Xiao Xun Xiao Xun"` wake-word detection on the 4-microphone PCM stream, parsing returned phrase text and confidence scores, applying configurable idle and playback-aware thresholds (defaulting to the empirically verified stock thresholds `-9.1` idle and `-11.3` during playback), enforcing trigger debounce, and emitting structured wake-word events containing phrase, score, DOA angle, and timestamp.
- **FR-005**: The system MUST convert raw 4-microphone capture output into clean 16 kHz mono 16-bit PCM frames of exact 60 ms duration (`960` samples / `1,920` bytes) regardless of upstream read chunk size, and provide bidirectional 16 kHz mono 60 ms Opus encoding and decoding.
- **FR-006**: The system MUST expose an observable runtime state and structured diagnostic logs (audio engine state, 4-mic channel energy/RMS metrics, model extraction status, wake-word detections with score & DOA, and Opus encode timing) to support live verification over ADB and downstream dashboard integration.
- **FR-007**: All pure-logic audio processing components (wake-word result/threshold parser, debounce filter, PCM channel extractor, 60 ms frame accumulator, and Opus round-trip framing) MUST be covered by automated JVM unit tests.

### Key Entities

- **VoiceEngineState**: Represents the lifecycle state of the foreground voice pipeline (`STOPPED`, `EXTRACTING_MODELS`, `INITIALIZING_HAL`, `LISTENING_WAKEWORD`, `WOKEN_STREAMING`, `ERROR`).
- **WakeWordEvent**: Immutable event emitted upon an accepted wake-word trigger, carrying `phrase`, `confidenceScore`, `appliedThreshold`, `doaDegrees`, `utteranceStartMs`, `utteranceEndMs`, and `timestampMs`.
- **AudioPipelineConfig**: Configuration parameters for 4-mic capture and wake-word detection (`captureChannelMode`, `selectedMonoChannel`, `idleThreshold`, `playbackThreshold`, `debounceMs`, `opusFrameDurationMs`, `opusBitrateBps`, `debugWavEnabled`).
- **ModelAssetManifest**: Descriptor of required on-device model entries (`sourceApkPath`, entry paths, target filenames, version stamp, integrity status).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a clean install onto the live Phicomm R1, first-launch model extraction and integrity verification complete automatically in under `10 seconds`, and subsequent service restarts reuse cached models in under `2 seconds`.
- **SC-002**: Speaking `"Xiao Xun Xiao Xun"` from `1.5–3 meters` away in a quiet room triggers a verified wake-word event with valid DOA angle in at least `90%` of trials (`>= 9/10`), with wake-word detection latency under `500 ms` from end of utterance.
- **SC-003**: While speaker audio is playing at normal listening volume, speaking `"Xiao Xun Xiao Xun"` still triggers wake-word detection reliably (demonstrating hardware AEC effectiveness), while normal non-wake-word room speech produces zero false triggers over a `5-minute` continuous idle observation window.
- **SC-004**: Continuous idle wake-word listening on the live Phicomm R1 maintains total application resident memory (`PSS`/`RSS`) below `80 MB` and sustains stable operation without crashes or storage leaks.
- **SC-005**: 100% of automated JVM unit tests pass, and on-device 60 ms Opus encoding completes in under `20 ms` per frame (`< 33%` of real-time budget) on the RK3229 processor.

## Assumptions

- The verified reverse-engineering contracts in `docs/re/4mic-hal-contract.md`, `docs/re/wakeup-engine-contract.md`, and `docs/re/stock-packages-inventory.md` are authoritative for all hardware/native interactions.
- Before on-device verification of Feature `002`, conflicting stock voice app `com.phicomm.speaker.device` will be non-destructively hidden via `pm hide com.phicomm.speaker.device` (while keeping `/system/app/Unisound/Unisound.apk` untouched on `/system` so runtime model extraction succeeds).
- Full Bluetooth A2DP Sink/AVRCP coexistence arbitration belongs to Feature `003`, Xiaozhi Cloud WebSocket transport belongs to Feature `004`, and the LAN Web Dashboard belongs to Feature `005`; Feature `002` exposes clean internal interfaces and diagnostic hooks for those downstream features.
- No local Android NDK installation is assumed; Opus encoding/decoding can use a lightweight pure-JVM/Android-compatible RFC-6716 Opus library behind an interchangeable codec interface so both JVM unit tests and `armeabi-v7a` Android builds compile cleanly with zero NDK dependency.
