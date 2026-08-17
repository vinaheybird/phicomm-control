# Tasks: Gemini Voice Integration

- [x] **1. DNS VPN Service Update**
  - [x] Update `DnsVpnService.kt` to use Google DoH (`8.8.4.4`) for external DNS resolution.
  - [x] Implement JSON parsing to extract the resolved IP and forward it correctly.

- [x] **2. Fake Server & Audio Interception**
  - [x] Add POST fallback handler in `WebConfigServer.kt`.
  - [x] Intercept `/trafficRouter` requests.
  - [x] Buffer the raw Opus audio stream into memory/disk.
  - [x] Add mock JSON responses for the Phicomm backend to keep the device active.

- [x] **3. Audio Decoding & AI Processing**
  - [x] Call `OpusDecoder.decodeToWav()` on the intercepted Opus stream.
  - [x] Fetch `apiKey` and `modelName` from `SharedPreferences`.
  - [x] Call `GeminiClient.sendAudioQuery()` with the WAV file.
  - [x] Pass the AI's text response to `TtsManager.speak()`.

- [x] **4. Web UI & Configuration API**
  - [x] Add `/api/gemini/config` endpoint in `WebConfigServer.kt`.
  - [x] Handle POST to save `apiKey` and `modelName`.
  - [x] Handle GET to return configuration status.

- [x] **5. Utility Scripts & Deployment**
  - [x] Create `scripts/hijack_dns.ps1` for ADB `/etc/hosts` manipulation.
  - [x] Create `scripts/sniff_network.ps1` for easy logcat debugging.
  - [x] Update `deploy.ps1` to start the background service post-install.
