# Implementation Plan: Phicomm R1 Stock Firmware Reverse-Engineering Audit

**Branch**: `001-r1-re-audit` | **Date**: 2026-11-22 | **Spec**: [spec.md](file:///D:/AGY/phicomm-control/specs/001-r1-re-audit/spec.md)

**Input**: Feature specification from `/specs/001-r1-re-audit/spec.md`

## Summary

Perform a systematic, read-only reverse-engineering audit of the live Phicomm R1 speaker (`192.168.88.66:5555`, Android 5.1.1 / API 22, RK3229 `armeabi-v7a`) by pulling stock APKs, native libraries, and system audio/hardware configs into gitignored `re_dumps/`, inspecting manifests and DEX/ELF bytecode using Android SDK `aapt.exe`, `dexdump.exe`, and Python ELF/OAT scripts, and authoring four verified English technical contracts under `docs/re/` (`4mic-hal-contract.md`, `wakeup-engine-contract.md`, `bluetooth-avrcp-contract.md`, `stock-packages-inventory.md`) to unblock Feature `002` and Feature `003` with zero guesswork.

## Technical Context

**Language/Version**: PowerShell 5.1, Python 3.x (analysis scripts), Markdown (technical contracts in English)

**Primary Dependencies**: Android SDK Build-Tools `34.0.0` (`aapt.exe`, `dexdump.exe`), `adb.exe`, Python standard library (`zipfile`, `struct`, `re`), live Phicomm R1 over ADB Wi-Fi

**Storage**: Gitignored local dump directory `re_dumps/` for proprietary stock APKs, `.so`, `.odex`, and system configs; tracked Markdown contracts under `docs/re/`

**Testing**: Cross-verification of every documented JNI signature, constant, file path, and broadcast action against raw `dexdump.exe`, `aapt.exe`, ELF symbol tables, and live ADB `dumpsys` output; `git status` & `git check-ignore` hygiene gate

**Target Platform**: Phicomm R1 (`rk322x-box`, Rockchip RK3229 `armeabi-v7a`, Android 5.1.1 / API 22, 512 MB RAM)

**Project Type**: Reverse-engineering audit & technical contract specification (documentation-only tracked deliverables)

**Performance Goals**: Complete extraction, disassembly, and contract verification in one repeatable session without modifying device state

**Constraints**: Strictly read-only on the Phicomm R1 (`/system` and package states untouched); zero proprietary binaries, APKs, `.dex`/`.odex`, `.so`, or raw decompiled trees committed to Git; English-only tracked documentation

**Scale/Scope**: 4 core stock APKs (`Unisound.apk`, `EchoService.apk`, `Bluetooth.apk`, `Launcher.apk`), 7 secondary stock `com.phicomm.speaker.*` APKs (manifest inventory), 4 core native `.so` libraries (`libUni4micHalJNI.so`, `libuni4michal.so`, `libasrfix.so`, `libuscasr.so`), `/system/usr/uni_4mic_config/*`, `/system/etc/audio_policy.conf`, and live Bluetooth/Audio `dumpsys` telemetry

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitution Principle | Compliance Check | Status |
| :--- | :--- | :--- |
| **I. Spec-First Development** | Preceded by ratified `specs/001-r1-re-audit/spec.md` and passing `checklists/requirements.md`; no production Android code written in this feature. | PASS |
| **II. Evidence-Based Reverse Engineering** | This entire feature exists to produce empirical contracts citing exact stock APK classes, `dexdump` signatures, ELF symbols, system configs, and ADB outputs. | PASS |
| **III. Single-APK Hardware-Native Efficiency** | Audits stock `/system/lib/` 4-mic/AEC and `libasrfix.so` contracts plus reversible `pm hide` targets to enable a single lightweight APK in Features `002`–`003`. | PASS |
| **IV. Deterministic Audio & Bluetooth Coexistence** | Audits `Bluetooth.apk`, `EchoService.apk`, and `audio_policy.conf` to establish exact A2DP Sink + AVRCP Pause/Resume & AudioFocus contracts. | PASS |
| **V. Testability, Observability & Clean Hygiene** | All proprietary pulls stay strictly inside gitignored `re_dumps/`; all tracked reports under `docs/re/` are written in English and verified with `git status`. | PASS |

**Post-Design Re-Check**: All 5 constitutional gates pass with zero violations and no Complexity Tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/001-r1-re-audit/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Tooling, live baseline, and odex/ELF audit strategy
├── quickstart.md        # Repeatable audit execution & verification guide
├── checklists/
│   └── requirements.md  # Spec quality validation checklist
└── tasks.md             # Dependency-ordered task list (/speckit-tasks)
```

### Tracked Deliverables & Gitignored Local Workspace (repository root)

```text
docs/re/                               # TRACKED (English technical contracts)
├── 4mic-hal-contract.md               # US1: Uni4micHalJNI + ALSA + hardware AEC + DOA
├── wakeup-engine-contract.md          # US2: JniAsrFix + "Xiao Xun Xiao Xun" assets + constraints
├── bluetooth-avrcp-contract.md        # US3: A2DP Sink (Profile 10) + AVRCP Pause/Play + AudioPolicy
└── stock-packages-inventory.md        # US4: All 11 com.phicomm.speaker.* APKs + pm hide/unhide plan

re_dumps/                              # GITIGNORED (local proprietary pulls, NEVER committed)
├── apks/                              # Pulled /system/app/*.apk and *.odex
├── libs/                              # Pulled /system/lib/*.so
├── config/                            # Pulled /system/usr/uni_4mic_config/*, /system/etc/audio*
└── dumps/                             # Raw aapt/dexdump/dumpsys text outputs
```

**Structure Decision**: Keeping all proprietary APKs, `.so`, `.odex`, and raw disassemblies inside gitignored `re_dumps/` while committing only synthesized English engineering contracts under `docs/re/` guarantees zero legal/binary leakage into Git while giving Features `002`–`006` permanent, reviewable reference documentation.

## Complexity Tracking

> No Constitution Check violations. No complexity exceptions required.
