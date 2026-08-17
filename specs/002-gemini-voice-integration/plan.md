# Implementation Plan: Gemini Voice Integration

## Architecture & Approach

This feature introduces a "Fake Server" mechanism that intercepts the Phicomm R1 smart speaker's backend API traffic, specifically voice queries. We achieve this by:
1. **DNS Hijacking**: Rerouting Phicomm domains (`aios-home.hivoice.cn` and `home.phicomm.com`) to a local IP address (where this app runs) by modifying `/system/etc/hosts` on the speaker using ADB.
2. **DNS VPN Bypass**: Modifying the existing `DnsVpnService` to accurately resolve legitimate domains through Google's DoH (`8.8.4.4`). This ensures that intercepted domains don't get stuck in an endless loop, as DoH operates over HTTPS instead of standard UDP port 53.
3. **Fake Server**: A fallback handler in `WebConfigServer` intercepts all `POST` requests to the Phicomm APIs, prioritizing `/trafficRouter` (the endpoint used by the speaker for streaming voice data).
4. **Audio Buffering & Decoding**: The intercepted body is raw Opus audio. The server buffers this stream chunk by chunk into memory. When the speaker's Voice Activity Detection (VAD) detects silence, it sends a request with `Content-Length: 0`. At this point, the buffer is flushed to disk and decoded to a WAV file asynchronously using `OpusDecoder`.
5. **AI & TTS Integration**: The decoded WAV file is sent to the Gemini API via `GeminiClient`. The text response is then played back locally using Android's `TtsManager`. Fake JSON responses are returned to the speaker to maintain its internal state machine without error loops.

## Component Modifications

### 1. Networking & Interception (`app/src/main/java/com/phicomm/gemini/vpn/`)
#### [MODIFY] [DnsVpnService.kt](file:///d:/AGY/phicomm-control/app/src/main/java/com/phicomm/gemini/vpn/DnsVpnService.kt)
- Replace standard UDP DNS forwarding with Google DNS over HTTPS (DoH) to bypass VPN routing loops.
- Use `HttpsURLConnection` to call `https://8.8.4.4/resolve?name=$domain`.
- Add `Accept: application/dns-json` header and a custom `HostnameVerifier` to trust the IP certificate.
- Parse the response string using Regex: `"data":"(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})"` to extract the IPv4 address.
- Construct the UDP DNS response packet and flush it back to the `outputStream` to the TUN interface.

### 2. Fake Server & Audio Interception (`app/src/main/java/com/phicomm/gemini/web/`)
#### [MODIFY] [WebConfigServer.kt](file:///d:/AGY/phicomm-control/app/src/main/java/com/phicomm/gemini/web/WebConfigServer.kt)
- Add a fallback handler for `Method.POST` to catch all incoming Phicomm R1 traffic.
- **Audio Buffering**:
  - For paths containing `/trafficRouter`, read the raw binary body from `session.inputStream`.
  - Buffer chunks into a `ByteArrayOutputStream` (`audioBuffer`).
- **Stream Termination**:
  - When a request to `/trafficRouter` has `Content-Length == 0`, it signifies the end of the voice query.
  - Flush the `audioBuffer` to a file: `query_${System.currentTimeMillis()}.wav`.
- **Fake JSON Responses**:
  - Return a static JSON success message for `/trafficRouter` (e.g., `nlu: { domain: "unknown", intent: "unknown" }`).
  - Return a static JSON success message for other endpoints containing fake user ID and bind status (`hasBound: true`).

### 3. AI & TTS Integration (`app/src/main/java/com/phicomm/gemini/web/`)
#### [MODIFY] [WebConfigServer.kt](file:///d:/AGY/phicomm-control/app/src/main/java/com/phicomm/gemini/web/WebConfigServer.kt)
- After flushing the audio buffer, spawn a background `Thread` to process the file.
- Call `OpusDecoder.decodeToWav(rawData, file)`.
- **SharedPreferences**: Retrieve `api_key` and `model_name` (default: `gemini-1.5-flash`) from `gemini_prefs`.
- Call `GeminiClient.sendAudioQuery(file, apiKey, modelName)`.
- Initialize `TtsManager` and call `ttsManager.speak(responseText)` with the Gemini API response.
- **API Endpoints**: Add `/api/gemini/config`.
  - `POST`: Extract `apiKey` and `modelName` parameters and persist them to `gemini_prefs`.
  - `GET`: Return `hasKey: Boolean` and `modelName` for the frontend dashboard.

### 4. Utility Scripts (`scripts/`)
#### [NEW] [hijack_dns.ps1](file:///d:/AGY/phicomm-control/scripts/hijack_dns.ps1)
- Prompt for the computer's IP address (`$PC_IP`).
- Automatically execute `adb root` and `adb remount` to gain write access to `/system`.
- Append `$PC_IP aios-home.hivoice.cn` and `$PC_IP home.phicomm.com` to `/system/etc/hosts` using `adb shell echo`.

#### [NEW] [sniff_network.ps1](file:///d:/AGY/phicomm-control/scripts/sniff_network.ps1)
- Clears logcat (`adb logcat -c`).
- Starts a tailed logcat filtering for `http|https|peiluyou|phicomm|aispeech|dui\.ai|dns|resolve|audio` to monitor the speaker's internal state.

### 5. Deployment (`deploy.ps1`)
#### [MODIFY] [deploy.ps1](file:///d:/AGY/phicomm-control/deploy.ps1)
- After standard installation and starting the service component, execute `adb shell am startservice -a com.phicomm.gemini.START_SERVICE`.
- Append `adb logcat -c` to clear logs for a clean debugging session after deploy.

## Verification
1. Run `scripts\hijack_dns.ps1` and verify the `/etc/hosts` on the device reflects the PC IP.
2. Ensure the App is running and the Fake Server is listening.
3. Speak "Xiao Fei Xiao Fei, what's the weather?" to the device.
4. Verify logcat shows FAKE SERVER POST REQUEST to `/trafficRouter` and buffers Opus data.
5. Verify logcat displays "End of audio stream. Decoding X bytes...".
6. Verify Gemini returns a text response, and `TtsManager` speaks the result out of the Android device/speaker.
7. Access the Web Dashboard `/api/gemini/config` via GET and POST to ensure settings are saved.
