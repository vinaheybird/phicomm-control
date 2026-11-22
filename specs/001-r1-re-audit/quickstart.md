# Quickstart: Running & Verifying Feature `001-r1-re-audit`

## Prerequisites

- Live Phicomm R1 reachable over ADB Wi-Fi (e.g., `192.168.88.66:5555`).
- `adb.exe` on `PATH` and Android SDK Build-Tools `34.0.0` at `%LOCALAPPDATA%\Android\Sdk\build-tools\34.0.0`.
- Python 3 available on `PATH`.

## Verification Checklist

After completing all tasks in `tasks.md`:

1. **Verify all 4 English contracts exist under `docs/re/`**:
   ```powershell
   Get-ChildItem docs\re\*.md | Select-Object Name, Length
   ```
   Expected files:
   - `docs/re/4mic-hal-contract.md`
   - `docs/re/wakeup-engine-contract.md`
   - `docs/re/bluetooth-avrcp-contract.md`
   - `docs/re/stock-packages-inventory.md`

2. **Verify zero unresolved placeholders or guesswork remain**:
   ```powershell
   Select-String -Path docs\re\*.md -Pattern "TBD|TODO|NEEDS CLARIFICATION|\?\?\?"
   ```
   Expected: zero matches.

3. **Verify Git hygiene (zero proprietary dumps tracked or untracked)**:
   ```powershell
   git status --short
   ```
   Expected: only tracked Markdown/SpecKit files (`docs/re/*.md`, `specs/001-r1-re-audit/*`, `.specify/feature.json`); nothing inside `re_dumps/`.

4. **Verify speaker `/system` and package state remained read-only**:
   ```powershell
   adb connect 192.168.88.66:5555; adb -s 192.168.88.66:5555 shell "pm list packages -d"
   ```
   Expected: identical to pre-audit state (no unintended system modifications).
