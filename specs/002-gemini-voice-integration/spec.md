# Feature: Gemini Voice Integration

## 1. Description
Integrate Google's Gemini AI with the Phicomm R1 smart speaker by intercepting its network traffic. The system will hijack DNS requests from the speaker to redirect voice query traffic to a local Fake Server. The server will capture the raw audio stream (Opus), decode it to WAV, send the query to the Gemini API, and play the AI's response back using Text-to-Speech (TTS). Additionally, the system provides a web interface to configure the Gemini API key and model, and includes utility scripts for DNS hijacking and network sniffing.

## 2. Business Goal
To replace the obsolete or unresponsive native voice assistant on the Phicomm R1 speaker with a state-of-the-art AI (Gemini), breathing new life into the hardware without requiring physical modifications to the device.

## 3. User Scenarios

### Scenario 1: Intercepting Voice Query
- **Given** the user has configured the DNS hijacking and Gemini API key,
- **When** the user says "Xiao Fei Xiao Fei" and asks a question,
- **Then** the local server intercepts the audio, processes it through Gemini, and the speaker plays the AI's spoken response.

### Scenario 2: Configuring Gemini API
- **Given** the user accesses the local Web Dashboard,
- **When** the user submits their Gemini API key and selects a model (e.g., gemini-1.5-flash),
- **Then** the settings are saved securely, and subsequent voice queries utilize the newly configured AI parameters.

### Scenario 3: Device Network Configuration
- **Given** the user needs to point the speaker to the fake server,
- **When** the user runs the provided DNS hijack script on their computer,
- **Then** the speaker's internal DNS is rerouted to the local fake server.

## 4. Functional Requirements

### 4.1. Network Interception
- The system must provide utility scripts (`hijack_dns.ps1`, `sniff_network.ps1`) to hijack the speaker's DNS resolution for specific domains (`aios-home.hivoice.cn`, `home.phicomm.com`).
- The DNS VPN Service must use DNS over HTTPS (DoH) via 8.8.4.4 to accurately resolve legitimate domains without looping back into the VPN intercept.

### 4.2. Fake Server & Audio Processing
- The local web server must listen for `POST` requests to `/trafficRouter` (the endpoint used by the speaker for voice data).
- The server must buffer raw Opus audio data from the request.
- The server must decode the Opus audio stream into a standard WAV file format upon detecting silence (end of stream).
- The server must return a valid JSON payload that mimics the native backend to keep the speaker's internal state machine happy.

### 4.3. AI Integration & TTS
- The server must transmit the decoded WAV audio to the Gemini API.
- The server must receive the text response from Gemini and synthesize it into speech using a TTS Manager.
- The system must handle cases where the API key is missing gracefully.

### 4.4. Configuration Interface
- The web server must expose a `/api/gemini/config` endpoint.
- It must support `POST` to save the `apiKey` and `modelName`.
- It must support `GET` to retrieve the current configuration status.

## 5. Success Criteria
- **Audio Interception**: 100% of voice queries sent to `/trafficRouter` are successfully captured by the local server.
- **Audio Decoding**: The Opus stream is decoded to a playable WAV file without corruption.
- **AI Response Time**: The end-to-end latency (from speaking to hearing the TTS response) should be reasonably fast, demonstrating successful integration.
- **Configuration**: Users can successfully update the Gemini API key without restarting the server.

## 6. Assumptions
- The Phicomm R1 speaker is accessible on the same local network.
- The user has valid ADB access to the speaker to run the hijacking scripts.
- The Gemini API remains accessible and the selected model supports audio input.
