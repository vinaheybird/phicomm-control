# Phicomm R1 Offline "Xiao Xun Xiao Xun" Wake-Word Engine Contract

**Audit Date**: 2026-11-22  
**Target Device**: Phicomm R1 (`rk322x-box`, ARMv7 `armeabi-v7a`, Android `5.1.1` / API `22`)  
**Primary Empirical Sources**:
- `/system/app/Unisound/Unisound.apk` (`cn.yunzhisheng.asrfix.JniAsrFix`, `com.unisound.sdk.ab`, `com.unisound.sdk.bg`, `com.unisound.sdk.o`, `com.unisound.sdk.m`, `com.unisound.sdk.j`, `com.unisound.sdk.l`)
- `/system/app/Unisound/Unisound.apk` assets: `assets/asrfix/data`, `assets/wakeup/data`, `assets/net/data`, `assets/ml`, `assets/version/data`, `assets/config/config.mg`
- `/system/lib/libasrfix.so` (1,163,620 bytes) and `/system/lib/libuscasr.so` (363,292 bytes)

---

## 1. Engine Overview & Security/Licensing Verification

The stock Phicomm R1 offline wake-word detector for `"Xiao Xun Xiao Xun"` (小讯小讯) runs entirely locally inside `/system/lib/libasrfix.so` via JNI class `cn.yunzhisheng.asrfix.JniAsrFix`.

### 1.1 Package-Name & Signature Constraint Audit

Empirical ELF string and disassembly audit of `/system/lib/libasrfix.so` and `cn.yunzhisheng.asrfix.JniAsrFix` confirms:

- **Zero APK package-name or signing-certificate checks** inside `libasrfix.so`. (The internal error string `"Engine invalid (package)!"` refers to acoustic-model/grammar package integrity validation, not Android `packageName`.)
- **Exact JNI class name required**: Because `libasrfix.so` binds native functions to `cn.yunzhisheng.asrfix.JniAsrFix`, the standalone clean APK **MUST** declare class **`cn.yunzhisheng.asrfix.JniAsrFix`** (and keep it un-obfuscated in ProGuard/R8).
- **Library loading**: `JniAsrFix.<clinit>` invokes `System.loadLibrary("asrfix")`. `/system/lib/libasrfix.so` is preinstalled on the R1 system partition, so the clean APK MUST NOT bundle or commit `libasrfix.so`.
- **No cloud connectivity required**: Wake-word initialization and recognition operate 100% offline.

---

## 2. Git-Clean Model Asset Sourcing Strategy

The stock acoustic model (`assets/asrfix/data`) is `28.8 MB` and proprietary to Unisound/Phicomm. Per repository governance (`AGENTS.md` and `constitution.md` Principle II), proprietary models MUST NEVER be committed to Git or bundled into the clean APK repository.

### 2.1 On-Device Asset Verification

Verified via `adb shell ls -l /system/app/Unisound/Unisound.apk` and ZIP manifest inspection:

- `/system/app/Unisound/Unisound.apk` has mode **`-rw-r--r--` (`0644`)** — world-readable by any user-space Android app even after `pm hide com.phicomm.speaker.device` is executed (because `pm hide` only disables package execution for user 0 without removing or changing permissions on the APK file).
- Therefore, **NEVER delete `/system/app/Unisound/Unisound.apk`** from `/system/app/`.

### 2.2 Required Asset Entries Inside `/system/app/Unisound/Unisound.apk`

Verified from `assets/ml` (`models=asrfix,wakeup,extjsgf,net,defaultjsgf`, `am=am0`, `custom=net`) and `com.unisound.sdk.ab`:

| Entry inside `Unisound.apk` | Size (bytes) | Extracted Filename in App Private Dir (`<modelDir>`) | Purpose |
|---|---|---|---|
| `assets/asrfix/data` | `28,800,785` | `<modelDir>/asrfix.dat` | Acoustic model (AM) bundle loaded by `JniAsrFix.init` |
| `assets/wakeup/data` | `647` | `<modelDir>/wakeup.dat` | Compiled `"Xiao Xun Xiao Xun"` wakeup grammar FST |
| `assets/net/data` | `1,321` | `<modelDir>/net.dat` | Secondary custom grammar net loaded alongside `wakeup.dat` |
| `assets/version/data` | `57` | `<modelDir>/version` | Model version stamp (`v3.10.77-phicomm,c6a7b7c5,838:187#581#383`, base64-encoded) |

### 2.3 Runtime Extraction & Integrity Verification Procedure (Feature `002`)

On first launch (or whenever `version` mismatches or a `.dat` file is missing/corrupt):
1. Open `/system/app/Unisound/Unisound.apk` using `java.util.zip.ZipFile("/system/app/Unisound/Unisound.apk")`.
2. Extract `assets/asrfix/data` -> `<filesDir>/asrfix_models/asrfix.dat`, `assets/wakeup/data` -> `<filesDir>/asrfix_models/wakeup.dat`, `assets/net/data` -> `<filesDir>/asrfix_models/net.dat`, and `assets/version/data` -> `<filesDir>/asrfix_models/version`.
3. Validate each extracted file (`asrfix.dat`, `wakeup.dat`, `net.dat`) by verifying exact uncompressed byte length AND IEEE CRC-32 against the `Unisound.apk` ZIP entry header. *(Verified live on R1 and via `com.unisound.sdk.ab` disassembly: `ab.h` is always `false` in stock `Unisound.apk` v3.10.77, and calling legacy native `JniAsrFix.crcCheck()` on v3.10.77 assets returns non-zero.)*
4. Ensure `<modelDir>` path passed as `amPath` ends with a trailing slash `/` (matching `com.unisound.sdk.ab.a`).

---

## 3. Exact JNI Contract: `cn.yunzhisheng.asrfix.JniAsrFix`

Verified from `dexdump.exe` disassembly of `cn.yunzhisheng.asrfix.JniAsrFix` in `Unisound.apk`:

### 3.1 Native Method Signatures

| Java Method | JNI Descriptor | Modifiers | Role |
|---|---|---|---|
| `init(String amDirPath, String grammarPaths)` | `(Ljava/lang/String;Ljava/lang/String;)I` | `private native` | Initializes engine with AM directory and comma-separated grammar `.dat` files (`0` = success) |
| `start(String grammarTag, int modelId)` | `(Ljava/lang/String;I)I` | `private native` | Starts recognition session for `grammarTag` (e.g. `"wakeup"`) and `modelId` (`0` = success) |
| `recognize(byte[] pcm, int len)` | `([BI)I` | `private native` | Feeds 16 kHz 16-bit PCM chunk; returns **`2`** when a recognition/wakeup result is ready |
| `getResult()` | `()Ljava/lang/String;` | `private native` | Returns raw newline-delimited result string + confidence float score |
| `stop()` | `()I` | `private native` | Stops active recognition session cleanly |
| `cancel()` | `()I` | `private native` | Cancels active recognition session immediately |
| `release()` | `()V` | `private native` | Destroys native engine instance and frees RAM |
| `reset(String grammarPath, String ext)` | `(Ljava/lang/String;Ljava/lang/String;)I` | `private native` | Hot-reloads a compiled grammar file |
| `setOptionInt(int id, int value)` | `(II)I` | `private native` | Sets integer engine option (also returns `modelId` when `id == 16`) |
| `getOptionInt(int id)` | `(I)I` | `private native` | Reads integer engine option (e.g., `100` = `modelNum`, `101` = utterance start, `102` = utterance end) |
| `setOptionString(int id, String value)` | `(ILjava/lang/String;)I` | `private native` | Sets string engine option (e.g., `9` = appKey) |
| `getOptionString(int id, String defaultVal)` | `(ILjava/lang/String;)Ljava/lang/String;` | `private native` | Reads string engine option |
| `isEngineIdle()` | `()I` | `private native` | Queries whether native decoder is idle |
| `check_wav_end()` | `()I` | `private native` | Checks internal VAD/wav-end flag |
| `setActiveNet(int netIndex)` | `(I)I` | `private native` | Selects active grammar net |
| `isactive(byte[] pcm, int len)` | `([BI)I` | `private native` | Checks speech activity on PCM frame |
| `search(String a, String b)` | `(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;` | `private native` | Offline dictionary/grammar lookup |
| `trackInfo(int flag)` | `(I)V` | `private native` | Internal diagnostic trace toggle |
| `crcCheck(String filePath)` | `(Ljava/lang/String;)I` | `private static native` | Verifies Unisound `.dat` file CRC (`0` = valid) |
| `getVersion()` | `()Ljava/lang/String;` | `public static native` | Returns `libasrfix.so` engine version string |
| `compileDecodeNet(String a, String b)` | `(Ljava/lang/String;Ljava/lang/String;)I` | `public static native` | Compiles JSGF decode net (not needed for stock wakeup) |
| `loadGrammarStr(String grammar)` | `(Ljava/lang/String;)I` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` (grammar compiler API) |
| `unloadGrammar(String tag)` | `(Ljava/lang/String;)I` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |
| `initUserDataCompiler(String path)` | `(Ljava/lang/String;)J` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |
| `destroyUserDataCompiler(long h)` | `(J)V` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |
| `loadCompiledJsgf(long h, String path)` | `(JLjava/lang/String;)I` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |
| `compileUserData(long h, String a, String b, String c)` | `(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)I` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |
| `compileDynamicUserData(long h, String a, String b)` | `(JLjava/lang/String;Ljava/lang/String;)I` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |
| `partialCompileUserData(long h, String a, String b, String c, String d, String e)` | `(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |
| `getTagsInfo(long h)` | `(J)Ljava/lang/String;` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |
| `grammarCompilerSetOptionInt(long h, int id, int val)` | `(JII)I` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |
| `grammarCompilerGetOptionString(long h, int id)` | `(JI)Ljava/lang/String;` | `public native` | Required by `JNI_OnLoad` `RegisterNatives` |

> **Critical `JNI_OnLoad` Note (Verified Live on R1)**: `libasrfix.so` registers all 32 native methods eagerly via `RegisterNatives` inside `JNI_OnLoad`. Omitting even unused public grammar-compiler methods causes `JNI_OnLoad` to return `JNI_ERR`. All 32 declarations MUST be present on `cn.yunzhisheng.asrfix.JniAsrFix`.

### 3.2 Native Return Codes & Error Mapping

Verified from `JniAsrFix` static constants (`a`–`o`) and `JniAsrFix.a(I)I`:

- **`0` (`JniAsrFix.a`)**: Success / no result yet (`recognize` continues feeding).
- **`1` (`JniAsrFix.b`)**: Intermediate / active speech state.
- **`2` (`JniAsrFix.c`)**: **Recognition / Wake-Word Result Ready** (confirmed in `com.unisound.sdk.j.d([B)I`: branches when `recognize() == 2` to invoke `getResult()`).
- **`3` (`JniAsrFix.d`)**: End-of-speech / stop condition.
- **`-1` to `-12` (`JniAsrFix.e`–`o`)**: Native error codes (mapped in stock SDK by adding `-63600` [`0xffff0790`], e.g., `-6` = max timeout).

### 3.3 Verified Engine Option IDs

Verified from `JniAsrFix`, `com.unisound.sdk.m.a(String, String, String)`, and `com.unisound.sdk.j`:

| Option ID | API | Verified Value / Meaning | Source |
|---|---|---|---|
| `0` | `setOptionInt` | `1` (post-init default flag) | `com.unisound.sdk.m.a(String,String,String)` |
| `1` | `setOptionInt` | `3000` (beam/timeout parameter) | `com.unisound.sdk.m.a(String,String,String)` |
| `6` | `setOptionInt` | `8` (post-init decoder config) | `com.unisound.sdk.m.a(String,String,String)` |
| `9` | `setOptionString` | AppKey string (non-empty string) | `com.unisound.sdk.m.a(String,String,String)` |
| `16` (`K`) | `setOptionInt` | Acoustic model index (`0 .. modelNum - 1`); **return value is `modelId`** | `JniAsrFix.a(String,String,String,w)` |
| `22` (`z`) | `setOptionInt` | Wakeup mode selector (`1` = wakeup, `2` = ASR/oneshot) | `JniAsrFix.b(I)` / `com.unisound.sdk.j.b()` |
| `100` (`J`) | `getOptionInt` | Total acoustic models count (`modelNum`) | `JniAsrFix.a(String,String,String,w)` |
| `101` | `getOptionInt` | Detected wakeup `utteranceStartTime` (ms) | `JniAsrFix.l()` / `com.unisound.sdk.j.a()` |
| `102` | `getOptionInt` | Detected wakeup `utteranceEndTime` (ms) | `JniAsrFix.q()` / `com.unisound.sdk.j.a()` |
| `500` (`L`) | `setOptionInt` | Decoder worker thread count | `JniAsrFix` static field `L` |
| `502` (`M`) | `setOptionInt` | `1` (engine feature flag) | `JniAsrFix` static field `M` |

---

## 4. Verified Initialization, Recognition Loop & Result Parsing

### 4.1 Initialization Sequence

Verified from `com.unisound.sdk.bg.g(Z)I` -> `com.unisound.sdk.o.b(String, String)` -> `com.unisound.sdk.m.a(String, String, String)` -> `JniAsrFix.a(String, String, String, w)`:

1. Build paths from extracted `<modelDir>` (ending with `/`):
   - `amDirPath = "<modelDir>/"` (directory containing `asrfix.dat`)
   - `grammarPaths = "<modelDir>/wakeup.dat,<modelDir>/net.dat"`
2. Call `val rc = init(amDirPath, grammarPaths)`. Must return `0`.
3. Query available acoustic models and obtain `defaultModelId`:
   ```kotlin
   val modelNum = getOptionInt(100)
   var defaultModelId = 0
   for (i in 0 until modelNum) {
       val id = setOptionInt(16, i)
       if (i == 0) defaultModelId = id
   }
   if (modelNum < 2) {
       defaultModelId = setOptionInt(16, 0)
   }
   ```
4. Apply post-init options (verified in `com.unisound.sdk.m.a`):
   - `setOptionInt(0, 1)`
   - `setOptionInt(1, 3000)`
   - `setOptionInt(6, 8)`
   - `setOptionString(9, "phicomm_r1_local")` (any non-empty string if stock appKey is omitted; Feature `002` will verify whether `setOptionString(9, ...)` is optional or requires a string)
5. Start wakeup listening (verified in `com.unisound.sdk.j.b()`):
   - `start("wakeup", defaultModelId)` — returns `0` on success.

### 4.2 Continuous Wake-Word Feeding Loop

Verified from `com.unisound.sdk.j.d([B)I` and `com.unisound.sdk.j.b()`:

1. Continuously feed 16 kHz 16-bit PCM chunks from the 4-mic HAL into `val status = recognize(pcmChunk, pcmChunk.size)`.
2. When `status == 2`:
   - Call `val rawResult = getResult()` (may return `null` or empty; stock fallback is `"\n-20.0"`).
   - Optionally query `utteranceStartMs = getOptionInt(101)` and `utteranceEndMs = getOptionInt(102)`.
   - Parse `rawResult` (Section 4.3) and compare `confidenceScore >= threshold`.
   - If accepted: emit Wake-Word Detected event, call `stop()` (or `cancel()`), transition audio pipeline to Xiaozhi Cloud streaming, and once the AI turn ends, restart wakeup listening with `start("wakeup", defaultModelId)`.
   - If rejected (score below threshold): restart wakeup session cleanly (`stop()` -> `start("wakeup", defaultModelId)`) to keep continuous listening active.

### 4.3 Result String Format & Production Thresholds

Verified from `com.unisound.sdk.l.a(F, String)` and `assets/config/config.mg`:

- `getResult()` returns a newline-delimited string where the recognized wake-word phrase precedes the final line and the **last line (`lines[lines.size - 1]`) contains the float confidence score** (higher/less-negative is higher confidence; fallback silence score is `-20.0f`).
- `com.unisound.sdk.l.a(threshold, rawResult)` splits on `"\n"`, parses the last element as `Float`, and rejects (`""`) if `threshold > score` (i.e., accepts when **`score >= threshold`**).
- **Stock Phicomm R1 Tuned Thresholds** (from `assets/config/config.mg`):
  - **Idle (no playback)**: `wakeup_benchmark = -9.1f`
  - **During music / Bluetooth playback**: `wakeup_benchmark_for_music_playing = -11.3f` (relaxed tiers: `-12.3f`, `-13.3f`)
  - **Wakeup back-silence VAD**: `wakeup_vad_timeout_back_sil = 200` ms
  - **One-shot disabled on R1**: `oneshot_enable = false` (confirming `"Xiao Xun Xiao Xun"` -> pause/chime -> user speaks command is the stock R1 UX).

---

## 5. Memory & CPU Characteristics

- `libasrfix.so` loads the 28.8 MB acoustic model (`asrfix.dat`) + 647 B `wakeup.dat` into native memory once during `init()`.
- Because wake-word decoding uses a tiny 1-phrase FST (`wakeup.dat` = 647 bytes), idle wake-word CPU utilization stays minimal on the RK3229 Cortex-A7, well within the project's `< 80 MB` RSS budget when legacy Phicomm apps are disabled.
