# Workspace Rules: Phicomm R1 Control

This repository is a clean-slate Android application for the **Phicomm R1** smart speaker. Every agent operating in this workspace MUST follow these rules and the project constitution at `.specify/memory/constitution.md`.

## 1. Mandatory SpecKit Workflow (Spec-Driven Development)

All non-trivial work MUST follow the SpecKit SDD workflow defined in `.agents/skills/speckit-*` and `.specify/`:

- **New features & architectural phases:** Execute in order:
  1. `speckit-specify` — create `specs/NNN-<short-name>/spec.md` and update `.specify/feature.json`.
  2. `speckit-clarify` — resolve ambiguities in the spec before planning (when any exist).
  3. `speckit-plan` — produce `plan.md`, `research.md`, `data-model.md`, `contracts/`, and `quickstart.md` with a passing Constitution Check.
  4. `speckit-tasks` — generate dependency-ordered `tasks.md`.
  5. `speckit-analyze` — run cross-artifact consistency checks across `spec.md`, `plan.md`, and `tasks.md`.
  6. `speckit-implement` — implement strictly against approved tasks and mark completion.
- **Bug reports & defects:** Use `speckit-bug-assess` -> `speckit-bug-fix` -> `speckit-bug-test`.
- **Exploratory ideas:** Use `speckit-assess-intake` -> `speckit-assess-define` -> `speckit-assess-research` -> `speckit-assess-shape` -> `speckit-assess-decide`.
- **Governance changes:** Update `.specify/memory/constitution.md` exclusively through the `speckit-constitution` workflow.
- **No un-specced production code:** Do NOT scaffold or write production Android/JNI/Web source code outside an active SpecKit feature with an approved `spec.md`, `plan.md`, and `tasks.md`.

## 2. Language & Documentation Standard

- All repository documentation, specs, plans, tasks, RE reports (`docs/re/**`), code comments, identifiers, and commit messages MUST be written in **clear technical English**.
- Conversational replies to the user may match the user's language, but all files committed to the repository MUST remain in English.

## 3. Evidence-Based Reverse Engineering (RE-First)

- Never guess stock Phicomm R1 JNI signatures, ALSA device parameters, Unisound model loading paths, or Android 5.1 hidden Bluetooth APIs.
- Every hardware or stock-firmware assumption MUST be backed by empirical evidence from the live Phicomm R1 device (ADB inspection, decompiled stock APKs, symbol inspection, or `logcat` verification) and documented in `docs/re/` or the active feature's `research.md`.
- Keep all proprietary dumps, pulled APKs, `.dex`/`.odex` files, `.so` extractions, decompiled Java trees, and raw PCM recordings inside gitignored directories (`re_dumps/`, `decompiled/`). NEVER commit proprietary Phicomm or Unisound binaries or decompiled source code to Git.

## 4. Target Hardware & Architecture Constraints

- **Target Device:** Phicomm R1 (`rk322x-box`, Rockchip RK3229 quad-core ARM Cortex-A7, `armeabi-v7a`, Android `5.1.1` / API `22`, `512 MB` RAM).
- **Single-APK Architecture:** Build one clean standalone Android APK (`minSdk 21`, ABI `armeabi-v7a`) that directly invokes the stock on-device native libraries (`/system/lib/libUni4micHalJNI.so`, `/system/lib/libasrfix.so`, etc.) rather than running legacy Phicomm apps in parallel.
- **Stock Wake-Word:** Reuse the stock offline `"Xiao Xun Xiao Xun"` wake-word model (`libasrfix.so` + Unisound wakeup assets) for maximum accuracy and minimal CPU/RAM overhead.
- **Hardware AEC & 4-Mic Array:** Capture voice exclusively through the 4-mic hardware array (`RK_MA4` / `pcmC1D0c` via `Uni4micHalJNI`) with hardware 2-channel AEC loopback enabled—never fall back to raw single-channel `AudioRecord` for primary voice pickup.
- **Xiaozhi Cloud & Music:** Connect directly to Xiaozhi Cloud (`api.tenclass.net` OTA activation + full-duplex Opus WebSocket + MCP JSON-RPC 2.0) for Q&A and internet/server music playback.
- **Bluetooth Coexistence:** Maintain PC Bluetooth A2DP Sink playback alongside AI features using deterministic **hard-pause** (AVRCP Pause + AudioFocus) on wake-word and **auto-resume** once AI finishes speaking.
- **Basic Web Dashboard:** Keep the embedded LAN Web Dashboard strictly minimal and clean-slate (live audio/AI state, Bluetooth pairing & connect/disconnect, Xiaozhi Cloud status & 6-digit activation code, master volume). Do NOT reintroduce legacy features (Gemini integration, DNS hijack VPN, CPU/RAM widgets).
- **Non-Destructive Device Deployment:** Disable conflicting stock Phicomm apps in `/system/app/` using reversible commands (`pm hide` / `pm disable-user`). Do NOT delete or overwrite stock APKs in `/system` unless explicitly approved in a spec with a rollback procedure.

## 5. Verification & Quality Gates

- **Unit Tests:** Pure-Kotlin logic (Audio Arbitration State Machine, Xiaozhi JSON/MCP protocol, Opus framing) MUST have automated JVM unit tests (`gradlew testDebugUnitTest`).
- **On-Device Verification:** Hardware-dependent features (4-mic capture, AEC under Bluetooth load, wake-word detection, AVRCP pause/resume, Xiaozhi end-to-end streaming) MUST be verified on the live Phicomm R1 via ADB before marking tasks complete.
- **Resource Budget:** Monitor RSS memory (`dumpsys meminfo`) and idle CPU (`top`) on the R1; keep idle listening lightweight (`< 80 MB` RSS target) to ensure 24/7 stability within 512 MB total RAM.
- **Security & Secrets:** Never hardcode personal Wi-Fi passwords, Xiaozhi tokens, or device secrets in tracked source files.
