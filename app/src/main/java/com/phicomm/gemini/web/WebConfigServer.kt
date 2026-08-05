package com.phicomm.gemini.web

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.google.gson.Gson
import com.phicomm.gemini.audio.PromptMuteController
import com.phicomm.gemini.bluetooth.BluetoothController
import com.phicomm.gemini.hardware.LedController
import com.phicomm.gemini.wifi.WifiSetupHelper
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class WebConfigServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "WebConfigServer"

        @Volatile
        private var instance: WebConfigServer? = null

        fun startInstance(context: Context, port: Int = 8080): WebConfigServer {
            val current = instance
            if (current != null && current.isAlive) {
                return current
            }
            synchronized(this) {
                val newInstance = instance
                if (newInstance != null && newInstance.isAlive) {
                    return newInstance
                }
                val server = WebConfigServer(context.applicationContext, port)
                server.startServer()
                instance = server
                return server
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val gson = Gson()
    private val wifiSetupHelper = WifiSetupHelper(context)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "Request: $method $uri")

        // REST API Endpoints
        if (uri.startsWith("/api/")) {
            return handleApiRequest(session, uri, method)
        }

        // Trang setup WiFi (truy cập trực tiếp)
        if (uri == "/setup-wifi") {
            return serveWifiSetupHtml()
        }

        // Trang chính: nếu loa chưa kết nối WiFi nhà → tự redirect sang /setup-wifi
        if (uri == "/" || uri == "") {
            if (!wifiSetupHelper.isConnectedToHomeWifi()) {
                val resp = newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
                resp.addHeader("Location", "/setup-wifi")
                return resp
            }
        }

        // Dashboard chính
        return serveDashboardHtml()
    }

    private fun handleApiRequest(session: IHTTPSession, uri: String, method: Method): Response {
        val params = session.parameters
        val responseMap = mutableMapOf<String, Any?>()

        try {
            if (Method.POST == method) {
                val files = HashMap<String, String>()
                session.parseBody(files)
            }

            when (uri) {
                "/api/wifi/connect" -> {
                    val ssid = session.parameters["ssid"]?.get(0) ?: ""
                    val password = session.parameters["password"]?.get(0) ?: ""
                    val passwordType = session.parameters["password_type"]?.get(0)
                        ?: session.parameters["passwordType"]?.get(0)
                    if (ssid.isBlank()) {
                        responseMap["success"] = false
                        responseMap["message"] = "Tên WiFi (SSID) không được để trống"
                    } else {
                        // Gọi connectToWifi trực tiếp — hàm này tự spawn background thread
                        // và tự delay 3s trước khi ngắt SoftAP, đủ thời gian cho HTTP response về điện thoại
                        val (ok, msg) = wifiSetupHelper.connectToWifi(ssid, password, passwordType)
                        responseMap["success"] = ok
                        responseMap["message"] = msg
                    }
                }

                "/api/wifi/status" -> {
                    val ip = wifiSetupHelper.getCurrentIp()
                    val ssid = wifiSetupHelper.getCurrentSsid()
                    responseMap["success"] = true
                    responseMap["ip"] = ip
                    responseMap["ssid"] = ssid
                    responseMap["isHomeWifi"] = wifiSetupHelper.isConnectedToHomeWifi()
                }

                // ── Bluetooth ───────────────────────────────────────────────
                "/api/status" -> {
                    val btEnabled = BluetoothController.isBluetoothEnabled()
                    val connectedDevice = BluetoothController.getConnectedDevice()
                    val pairedDevices = BluetoothController.getPairedDevices()
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val volPercent = if (maxVol > 0) (currentVol * 100 / maxVol) else 0

                    responseMap["success"] = true
                    responseMap["btEnabled"] = btEnabled
                    responseMap["connectedDevice"] = if (connectedDevice != null) {
                        mapOf("name" to (connectedDevice.name ?: "Chưa rõ"), "address" to connectedDevice.address)
                    } else null
                    responseMap["pairedDevices"] = pairedDevices
                    responseMap["autoReconnect"] = BluetoothController.isAutoReconnectEnabled()
                    responseMap["ledEnabled"] = LedController.isEnabled()
                    responseMap["ledMode"] = LedController.getCurrentMode().name
                    responseMap["promptMute"] = PromptMuteController.isPromptMuteEnabled()
                    responseMap["volume"] = volPercent
                }

                "/api/bluetooth/toggle" -> {
                    val newState = BluetoothController.toggleBluetooth()
                    responseMap["success"] = true
                    responseMap["btEnabled"] = newState
                }

                "/api/bluetooth/connect" -> {
                    val address = session.parameters["address"]?.get(0) ?: ""
                    if (address.isNotEmpty()) {
                        val result = BluetoothController.connectDevice(address)
                        responseMap["success"] = result
                        responseMap["message"] = if (result) "Đang kết nối đến $address" else "Lỗi kết nối"
                    } else {
                        responseMap["success"] = false
                        responseMap["message"] = "Thiếu địa chỉ MAC"
                    }
                }

                "/api/bluetooth/disconnect" -> {
                    val result = BluetoothController.disconnectCurrentDevice()
                    responseMap["success"] = result
                    responseMap["message"] = if (result) "Đã ngắt kết nối" else "Không có thiết bị kết nối"
                }

                "/api/bluetooth/discover" -> {
                    val result = BluetoothController.makeDiscoverable(300)
                    responseMap["success"] = result
                    responseMap["message"] = if (result) "Đã bật chế độ dò tìm trong 5 phút" else "Lỗi bật dò tìm"
                }

                "/api/bluetooth/auto-reconnect" -> {
                    val enabledStr = session.parameters["enabled"]?.get(0) ?: "true"
                    val enabled = enabledStr == "true" || enabledStr == "1" || enabledStr == "on"
                    BluetoothController.setAutoReconnectEnabled(enabled)
                    responseMap["success"] = true
                    responseMap["autoReconnect"] = enabled
                }

                "/api/led/toggle" -> {
                    val newState = LedController.toggleLed()
                    responseMap["success"] = true
                    responseMap["ledEnabled"] = newState
                }

                "/api/led/mode" -> {
                    val modeStr = session.parameters["mode"]?.get(0) ?: "BLUE_SOLID"
                    try {
                        val mode = LedController.LedMode.valueOf(modeStr)
                        LedController.setMode(mode)
                        responseMap["success"] = true
                        responseMap["ledMode"] = mode.name
                    } catch (e: Exception) {
                        responseMap["success"] = false
                        responseMap["message"] = "Chế độ LED không hợp lệ"
                    }
                }

                "/api/prompt-mute/toggle" -> {
                    val current = PromptMuteController.isPromptMuteEnabled()
                    PromptMuteController.setPromptMuteEnabled(!current)
                    responseMap["success"] = true
                    responseMap["promptMute"] = !current
                }

                "/api/volume" -> {
                    val volParam = session.parameters["level"]?.get(0) ?: "50"
                    val percent = volParam.toIntOrNull() ?: 50
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val targetVol = (percent * maxVol) / 100
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                    responseMap["success"] = true
                    responseMap["volume"] = percent
                }

                else -> {
                    responseMap["success"] = false
                    responseMap["message"] = "Endpoint không tồn tại"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi xử lý API $uri: ${e.message}", e)
            responseMap["success"] = false
            responseMap["error"] = e.message ?: "Lỗi hệ thống"
        }

        val json = gson.toJson(responseMap)
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json)
    }

    // ══════════════════════════════════════════════════════════════════
    // TRANG SETUP WIFI
    // ══════════════════════════════════════════════════════════════════
    private fun serveWifiSetupHtml(): Response {
        val S = '$'
        val html = """
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<title>Phicomm R1 — Kết Nối WiFi</title>
<style>
  :root {
    --bg: linear-gradient(135deg, #0b0f1e 0%, #0f1f3d 50%, #120b2e 100%);
    --card: rgba(15, 25, 50, 0.85);
    --border: rgba(99, 179, 237, 0.2);
    --blue: #38bdf8;
    --purple: #a78bfa;
    --green: #34d399;
    --red: #f87171;
    --text: #f0f6ff;
    --muted: #94a3b8;
    --input-bg: rgba(255,255,255,0.06);
    --input-border: rgba(99, 179, 237, 0.3);
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: var(--bg);
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 1.5rem;
    color: var(--text);
  }
  .container {
    width: 100%;
    max-width: 420px;
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
  }
  /* ── Header ── */
  .header { text-align: center; }
  .wifi-icon {
    font-size: 3.5rem;
    display: block;
    margin-bottom: 0.5rem;
    animation: pulse 2.5s ease-in-out infinite;
    filter: drop-shadow(0 0 16px rgba(56, 189, 248, 0.6));
  }
  @keyframes pulse {
    0%, 100% { transform: scale(1); opacity: 1; }
    50% { transform: scale(1.08); opacity: 0.85; }
  }
  .header h1 {
    font-size: 1.5rem;
    font-weight: 800;
    background: linear-gradient(90deg, var(--blue), var(--purple));
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .header p { color: var(--muted); font-size: 0.88rem; margin-top: 0.25rem; }

  /* ── Card ── */
  .card {
    background: var(--card);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border: 1px solid var(--border);
    border-radius: 20px;
    padding: 1.6rem;
    box-shadow: 0 20px 60px rgba(0,0,0,0.5), 0 0 0 1px rgba(56,189,248,0.08) inset;
  }
  .card-title {
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--blue);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    margin-bottom: 1.2rem;
    display: flex;
    align-items: center;
    gap: 0.4rem;
  }

  /* ── Form ── */
  .field { margin-bottom: 1rem; }
  .field label {
    display: block;
    font-size: 0.82rem;
    font-weight: 600;
    color: var(--muted);
    margin-bottom: 0.4rem;
    letter-spacing: 0.03em;
  }
  .input-wrap { position: relative; }
  .input-wrap input {
    width: 100%;
    background: var(--input-bg);
    border: 1px solid var(--input-border);
    border-radius: 12px;
    color: var(--text);
    font-size: 1rem;
    padding: 0.75rem 1rem;
    outline: none;
    transition: border-color 0.2s, box-shadow 0.2s;
    -webkit-appearance: none;
  }
  .input-wrap input:focus {
    border-color: var(--blue);
    box-shadow: 0 0 0 3px rgba(56,189,248,0.15);
  }
  .input-wrap input::placeholder { color: rgba(148,163,184,0.5); }
  .toggle-pass {
    position: absolute;
    right: 0.75rem;
    top: 50%;
    transform: translateY(-50%);
    background: none;
    border: none;
    cursor: pointer;
    color: var(--muted);
    font-size: 1.1rem;
    line-height: 1;
    padding: 0.2rem;
  }

  /* ── Button ── */
  .btn-connect {
    width: 100%;
    padding: 0.9rem;
    border: none;
    border-radius: 14px;
    background: linear-gradient(135deg, #0ea5e9, #6366f1);
    color: white;
    font-size: 1rem;
    font-weight: 700;
    cursor: pointer;
    letter-spacing: 0.02em;
    transition: all 0.25s;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    position: relative;
    overflow: hidden;
    margin-top: 0.5rem;
  }
  .btn-connect:hover { opacity: 0.92; transform: translateY(-1px); box-shadow: 0 8px 24px rgba(14,165,233,0.35); }
  .btn-connect:active { transform: translateY(0); }
  .btn-connect:disabled { opacity: 0.55; cursor: not-allowed; transform: none; }

  /* Spinner */
  .spinner {
    width: 18px; height: 18px;
    border: 2px solid rgba(255,255,255,0.3);
    border-top-color: white;
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
    display: none;
  }
  @keyframes spin { to { transform: rotate(360deg); } }

  /* ── Status box ── */
  .status-box {
    border-radius: 12px;
    padding: 1rem 1.1rem;
    font-size: 0.88rem;
    line-height: 1.5;
    display: none;
    margin-top: 0.8rem;
  }
  .status-box.success {
    background: rgba(52, 211, 153, 0.12);
    border: 1px solid rgba(52, 211, 153, 0.35);
    color: #6ee7b7;
  }
  .status-box.error {
    background: rgba(248, 113, 113, 0.12);
    border: 1px solid rgba(248, 113, 113, 0.35);
    color: #fca5a5;
  }
  .status-box.info {
    background: rgba(56, 189, 248, 0.1);
    border: 1px solid rgba(56, 189, 248, 0.3);
    color: #7dd3fc;
  }

  /* ── Steps ── */
  .steps { display: flex; flex-direction: column; gap: 0.6rem; margin-top: 0.2rem; }
  .step {
    display: flex;
    align-items: flex-start;
    gap: 0.7rem;
    font-size: 0.85rem;
    color: var(--muted);
    line-height: 1.4;
  }
  .step-num {
    min-width: 22px; height: 22px;
    background: rgba(56,189,248,0.15);
    border: 1px solid rgba(56,189,248,0.3);
    border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--blue);
  }

  /* ── Dashboard link ── */
  .dash-link {
    text-align: center;
    font-size: 0.82rem;
    color: var(--muted);
  }
  .dash-link a {
    color: var(--blue);
    text-decoration: none;
  }
  .dash-link a:hover { text-decoration: underline; }

  /* ── Connected state ── */
  #connectedBanner {
    background: rgba(52,211,153,0.1);
    border: 1px solid rgba(52,211,153,0.35);
    border-radius: 16px;
    padding: 1.4rem;
    text-align: center;
    display: none;
  }
  #connectedBanner .icon { font-size: 2.5rem; }
  #connectedBanner h2 { font-size: 1.1rem; color: var(--green); margin: 0.5rem 0 0.3rem; }
  #connectedBanner p { font-size: 0.85rem; color: var(--muted); }
  #connectedBanner .ip-badge {
    display: inline-block;
    margin-top: 0.8rem;
    background: rgba(52,211,153,0.15);
    border: 1px solid rgba(52,211,153,0.4);
    border-radius: 8px;
    padding: 0.4rem 0.9rem;
    font-family: monospace;
    font-size: 1rem;
    color: var(--green);
    font-weight: 700;
  }
</style>
</head>
<body>
<div class="container">

  <!-- Header -->
  <div class="header">
    <span class="wifi-icon">📶</span>
    <h1>Kết Nối WiFi Nhà</h1>
    <p>Phicomm R1 — Bước đầu tiên</p>
  </div>

  <!-- Connected banner (shown when already on home WiFi) -->
  <div id="connectedBanner">
    <div class="icon">✅</div>
    <h2>Đã Kết Nối WiFi Nhà!</h2>
    <p>Loa đã có IP trên mạng nhà</p>
    <div class="ip-badge" id="connectedIp">...</div>
    <br><br>
    <a href="/" style="color:var(--blue); font-size:0.9rem;">🎛️ Mở Bảng Điều Khiển →</a>
  </div>

  <!-- Setup form -->
  <div class="card" id="setupCard">
    <div class="card-title">📶 Nhập Thông Tin WiFi</div>

    <div class="field">
      <label for="ssid">Tên WiFi (SSID)</label>
      <div class="input-wrap">
        <input type="text" id="ssid" placeholder="Ví dụ: Hoa Duong" autocomplete="off" autocapitalize="off">
      </div>
    </div>

    <div class="field">
      <label for="password">Mật Khẩu WiFi</label>
      <div class="input-wrap">
        <input type="password" id="password" placeholder="Mật khẩu (để trống nếu mạng mở)" autocomplete="off">
        <button class="toggle-pass" type="button" onclick="togglePass()" id="eyeBtn">👁</button>
      </div>
    </div>

    <button class="btn-connect" id="connectBtn" onclick="doConnect()">
      <div class="spinner" id="spinner"></div>
      <span id="btnText">📶 Kết Nối WiFi</span>
    </button>

    <div class="status-box" id="statusBox"></div>
  </div>

  <!-- Instructions -->
  <div class="card" id="instructCard">
    <div class="card-title">📋 Hướng Dẫn</div>
    <div class="steps">
      <div class="step">
        <div class="step-num">1</div>
        <span>Nhập đúng <strong>tên WiFi</strong> nhà bạn (phân biệt chữ hoa/thường)</span>
      </div>
      <div class="step">
        <div class="step-num">2</div>
        <span>Nhập <strong>mật khẩu</strong> WiFi và nhấn Kết Nối</span>
      </div>
      <div class="step">
        <div class="step-num">3</div>
        <span>Đợi <strong>15–30 giây</strong> để loa kết nối vào WiFi nhà</span>
      </div>
      <div class="step">
        <div class="step-num">4</div>
        <span>Kết nối điện thoại vào WiFi nhà, truy cập <strong>http://phicomm.local:8080</strong></span>
      </div>
    </div>
  </div>

  <!-- Dashboard link -->
  <div class="dash-link">
    Đã kết nối WiFi nhà? <a href="/">Mở bảng điều khiển →</a>
  </div>

</div>

<script>
  // Kiểm tra trạng thái WiFi khi tải trang
  fetch('/api/wifi/status')
    .then(r => r.json())
    .then(d => {
      if (d.isHomeWifi && d.ip) {
        document.getElementById('setupCard').style.display = 'none';
        document.getElementById('instructCard').style.display = 'none';
        document.getElementById('connectedBanner').style.display = 'block';
        document.getElementById('connectedIp').textContent = d.ip;
      }
    }).catch(() => {});

  function togglePass() {
    const inp = document.getElementById('password');
    inp.type = inp.type === 'password' ? 'text' : 'password';
  }

  function showStatus(type, html) {
    const box = document.getElementById('statusBox');
    box.className = 'status-box ' + type;
    box.innerHTML = html;
    box.style.display = 'block';
  }

  async function doConnect() {
    const ssid = document.getElementById('ssid').value.trim();
    const pass = document.getElementById('password').value;

    if (!ssid) {
      showStatus('error', '⚠️ Vui lòng nhập Tên WiFi (SSID)');
      return;
    }

    // UI loading state
    const btn = document.getElementById('connectBtn');
    btn.disabled = true;
    document.getElementById('spinner').style.display = 'block';
    document.getElementById('btnText').textContent = 'Đang kết nối...';
    showStatus('info', '⏳ Đang gửi lệnh kết nối WiFi tới loa...');

    try {
      const params = new URLSearchParams({ ssid, password: pass });
      const res = await fetch('/api/wifi/connect?' + params, { method: 'POST' });
      const data = await res.json();

      if (data.success) {
        showStatus('success',
          '✅ <strong>Đã gửi lệnh kết nối!</strong><br><br>' +
          '📌 Loa đang kết nối vào <strong>"' + ssid + '"</strong>.<br><br>' +
          'Tiếp theo:<br>' +
          '1️⃣ Đợi 15–30 giây<br>' +
          '2️⃣ Tắt WiFi AP của loa — điện thoại sẽ tự mất kết nối<br>' +
          '3️⃣ Kết nối điện thoại vào WiFi nhà<br>' +
          '4️⃣ Truy cập <strong>http://phicomm.local:8080</strong><br>' +
          '&nbsp;&nbsp;&nbsp;&nbsp;(hoặc tìm IP loa trong router)'
        );
        document.getElementById('btnText').textContent = '✅ Đã Gửi Lệnh';
      } else {
        showStatus('error', '❌ <strong>Thất bại:</strong> ' + (data.message || 'Lỗi không xác định'));
        btn.disabled = false;
        document.getElementById('spinner').style.display = 'none';
        document.getElementById('btnText').textContent = '📶 Thử Lại';
      }
    } catch (e) {
      showStatus('error', '❌ Không thể kết nối tới server loa. Kiểm tra lại kết nối WiFi.');
      btn.disabled = false;
      document.getElementById('spinner').style.display = 'none';
      document.getElementById('btnText').textContent = '📶 Thử Lại';
    }
  }

  // Cho phép bấm Enter để submit
  document.addEventListener('keydown', function(e) {
    if (e.key === 'Enter') doConnect();
  });
</script>
</body>
</html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    // ══════════════════════════════════════════════════════════════════
    // DASHBOARD CHÍNH
    // ══════════════════════════════════════════════════════════════════
    private fun serveDashboardHtml(): Response {
        val S = '$'
        val html = """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Phicomm R1 - Web Controller</title>
                <style>
                    :root {
                        --bg-gradient: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
                        --card-bg: rgba(30, 41, 59, 0.7);
                        --card-border: rgba(255, 255, 255, 0.1);
                        --accent-blue: #38bdf8;
                        --accent-purple: #a855f7;
                        --accent-green: #34d399;
                        --accent-red: #f43f5e;
                        --text-primary: #f8fafc;
                        --text-secondary: #94a3b8;
                    }

                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: system-ui, -apple-system, sans-serif; }

                    body {
                        background: var(--bg-gradient);
                        color: var(--text-primary);
                        min-height: 100vh;
                        padding: 1.5rem;
                        display: flex;
                        justify-content: center;
                    }

                    .dashboard {
                        width: 100%;
                        max-width: 600px;
                        display: flex;
                        flex-direction: column;
                        gap: 1.2rem;
                    }

                    .header {
                        text-align: center;
                        padding: 1rem 0;
                    }

                    .header h1 {
                        font-size: 1.8rem;
                        background: linear-gradient(to right, var(--accent-blue), var(--accent-purple));
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 0.5rem;
                    }

                    .header p { color: var(--text-secondary); font-size: 0.9rem; margin-top: 0.3rem; }

                    .card {
                        background: var(--card-bg);
                        backdrop-filter: blur(12px);
                        border: 1px solid var(--card-border);
                        border-radius: 16px;
                        padding: 1.2rem;
                        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
                    }

                    .card-title {
                        font-size: 1.1rem;
                        font-weight: 700;
                        margin-bottom: 1rem;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        color: var(--accent-blue);
                    }

                    .status-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 0.4rem;
                        padding: 0.3rem 0.8rem;
                        border-radius: 20px;
                        font-size: 0.85rem;
                        font-weight: 600;
                    }
                    .status-on { background: rgba(52, 211, 153, 0.15); color: var(--accent-green); border: 1px solid var(--accent-green); }
                    .status-off { background: rgba(244, 63, 94, 0.15); color: var(--accent-red); border: 1px solid var(--accent-red); }

                    .control-row {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 0.6rem 0;
                        border-bottom: 1px solid rgba(255, 255, 255, 0.05);
                    }
                    .control-row:last-child { border-bottom: none; }

                    .toggle-switch {
                        position: relative;
                        display: inline-block;
                        width: 50px;
                        height: 26px;
                    }
                    .toggle-switch input { opacity: 0; width: 0; height: 0; }
                    .slider {
                        position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
                        background-color: #334155; transition: .3s; border-radius: 26px;
                    }
                    .slider:before {
                        position: absolute; content: ""; height: 20px; width: 20px; left: 3px; bottom: 3px;
                        background-color: white; transition: .3s; border-radius: 50%;
                    }
                    input:checked + .slider { background-color: var(--accent-blue); }
                    input:checked + .slider:before { transform: translateX(24px); }

                    .btn {
                        background: linear-gradient(135deg, var(--accent-blue), #0284c7);
                        color: #0f172a;
                        font-weight: 700;
                        border: none;
                        padding: 0.7rem 1.2rem;
                        border-radius: 10px;
                        cursor: pointer;
                        transition: all 0.2s;
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        gap: 0.4rem;
                        text-decoration: none;
                        font-size: 0.9rem;
                    }
                    .btn:hover { opacity: 0.9; transform: translateY(-1px); }
                    .btn-secondary { background: #334155; color: var(--text-primary); }
                    .btn-danger { background: var(--accent-red); color: white; }
                    .btn-sm { padding: 0.4rem 0.8rem; font-size: 0.8rem; }

                    .device-list {
                        display: flex;
                        flex-direction: column;
                        gap: 0.6rem;
                        margin-top: 0.8rem;
                    }
                    .device-item {
                        background: rgba(15, 23, 42, 0.6);
                        border: 1px solid var(--card-border);
                        border-radius: 10px;
                        padding: 0.8rem 1rem;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .device-name { font-weight: 600; font-size: 0.95rem; }
                    .device-mac { font-size: 0.75rem; color: var(--text-secondary); }

                    .range-slider {
                        width: 100%;
                        height: 8px;
                        border-radius: 4px;
                        background: #334155;
                        outline: none;
                        accent-color: var(--accent-blue);
                    }

                    .select-box {
                        background: #0f172a;
                        color: var(--text-primary);
                        border: 1px solid #334155;
                        padding: 0.5rem 0.8rem;
                        border-radius: 8px;
                        font-size: 0.9rem;
                    }
                </style>
            </head>
            <body>
                <div class="dashboard">
                    <div class="header">
                        <h1>🔊 Phicomm R1 Controller</h1>
                        <p>Trang Điều Khiển Loa Bluetooth &amp; Phần Cứng</p>
                    </div>

                    <!-- Bluetooth Card -->
                    <div class="card">
                        <div class="card-title">
                            <span>📡 Kết Nối Bluetooth</span>
                            <span id="btStatusBadge" class="status-badge status-off">Đang kiểm tra...</span>
                        </div>

                        <div class="control-row">
                            <div>
                                <div style="font-weight:600;">Bật / Tắt Bluetooth</div>
                                <div style="font-size:0.8rem; color:var(--text-secondary);">Phát sóng Bluetooth cho loa</div>
                            </div>
                            <label class="toggle-switch">
                                <input type="checkbox" id="btToggle" onchange="toggleBluetooth()">
                                <span class="slider"></span>
                            </label>
                        </div>

                        <div class="control-row">
                            <div>
                                <div style="font-weight:600;">Tự Động Kết Nối Lại</div>
                                <div style="font-size:0.8rem; color:var(--text-secondary);">Tự nối lại thiết bị gần nhất</div>
                            </div>
                            <label class="toggle-switch">
                                <input type="checkbox" id="autoReconnectToggle" onchange="toggleAutoReconnect()">
                                <span class="slider"></span>
                            </label>
                        </div>

                        <div style="margin-top: 1rem; display: flex; gap: 0.6rem;">
                            <button class="btn" style="flex:1;" onclick="makeDiscoverable()">🔍 Bật Dò Tìm (5 phút)</button>
                            <button class="btn btn-secondary" onclick="fetchStatus()">🔄 Làm mới</button>
                        </div>
                    </div>

                    <!-- Paired Devices -->
                    <div class="card">
                        <div class="card-title">
                            <span>📱 Danh Sách Thiết Bị Đã Ghép Đôi</span>
                        </div>
                        <div id="pairedList" class="device-list">
                            <div style="text-align:center; color:var(--text-secondary); padding:1rem;">Đang tải danh sách...</div>
                        </div>
                    </div>

                    <!-- LED Control -->
                    <div class="card">
                        <div class="card-title">
                            <span>💡 Điều Khiển Đèn LED</span>
                        </div>

                        <div class="control-row">
                            <div>
                                <div style="font-weight:600;">Bật / Tắt Đèn LED</div>
                                <div style="font-size:0.8rem; color:var(--text-secondary);">Tiết kiệm điện khi tắt LED</div>
                            </div>
                            <label class="toggle-switch">
                                <input type="checkbox" id="ledToggle" onchange="toggleLed()">
                                <span class="slider"></span>
                            </label>
                        </div>

                        <div class="control-row">
                            <div style="font-weight:600;">Hiệu Ứng / Màu Đèn</div>
                            <select id="ledModeSelect" class="select-box" onchange="changeLedMode()">
                                <option value="BLUE_SOLID">Xanh Lam Dịu</option>
                                <option value="CYAN_PULSE">Xanh Lam Nháy Nhẹ</option>
                                <option value="ORANGE_SOLID">Vàng Cam</option>
                                <option value="GREEN_SOLID">Xanh Lá</option>
                                <option value="OFF">Tắt Đèn</option>
                            </select>
                        </div>
                    </div>

                    <!-- Audio -->
                    <div class="card">
                        <div class="card-title">
                            <span>🔊 Âm Thanh &amp; Thông Báo</span>
                        </div>

                        <div class="control-row">
                            <div>
                                <div style="font-weight:600;">Tắt Âm Thông Báo Bluetooth To</div>
                                <div style="font-size:0.8rem; color:var(--text-secondary);">Tự động mute giọng nói gốc của loa R1</div>
                            </div>
                            <label class="toggle-switch">
                                <input type="checkbox" id="promptMuteToggle" onchange="togglePromptMute()">
                                <span class="slider"></span>
                            </label>
                        </div>

                        <div style="margin-top: 1rem;">
                            <div style="display:flex; justify-content:space-between; font-weight:600; margin-bottom:0.5rem;">
                                <span>Âm Lượng Loa:</span>
                                <span id="volLabel">50%</span>
                            </div>
                            <input type="range" id="volRange" class="range-slider" min="0" max="100" value="50" onchange="setVolume(this.value)" oninput="document.getElementById('volLabel').innerText = this.value + '%'">
                        </div>
                    </div>

                    <!-- WiFi Re-setup -->
                    <div class="card">
                        <div class="card-title"><span>📶 Đổi Mạng WiFi</span></div>
                        <p style="font-size:0.85rem; color:var(--text-secondary); margin-bottom:0.8rem;">Muốn đổi sang mạng WiFi khác?</p>
                        <a href="/setup-wifi" class="btn" style="width:100%; display:block; text-align:center;">📶 Thiết Lập WiFi Mới</a>
                    </div>

                </div>

                <script>
                    async function fetchStatus() {
                        try {
                            const res = await fetch('/api/status');
                            const data = await res.json();
                            if (!data.success) return;

                            document.getElementById('btToggle').checked = data.btEnabled;
                            const badge = document.getElementById('btStatusBadge');
                            if (data.btEnabled) {
                                if (data.connectedDevice) {
                                    badge.className = 'status-badge status-on';
                                    badge.innerText = '🟢 Đã kết nối: ' + data.connectedDevice.name;
                                } else {
                                    badge.className = 'status-badge status-on';
                                    badge.innerText = '🟡 Đang bật (Chưa kết nối)';
                                }
                            } else {
                                badge.className = 'status-badge status-off';
                                badge.innerText = '🔴 Đã tắt Bluetooth';
                            }

                            document.getElementById('autoReconnectToggle').checked = data.autoReconnect;
                            document.getElementById('ledToggle').checked = data.ledEnabled;
                            document.getElementById('ledModeSelect').value = data.ledMode;
                            document.getElementById('promptMuteToggle').checked = data.promptMute;
                            document.getElementById('volRange').value = data.volume;
                            document.getElementById('volLabel').innerText = data.volume + '%';

                            const pairedList = document.getElementById('pairedList');
                            if (!data.pairedDevices || data.pairedDevices.length === 0) {
                                pairedList.innerHTML = '<div style="text-align:center; color:var(--text-secondary); padding:1rem;">Chưa có thiết bị nào được ghép đôi</div>';
                            } else {
                                pairedList.innerHTML = data.pairedDevices.map(dev => `
                                    <div class="device-item">
                                        <div>
                                            <div class="device-name">${S}{dev.name} ${S}{dev.isConnected ? '✅ (Đang phát)' : ''}</div>
                                            <div class="device-mac">${S}{dev.address}</div>
                                        </div>
                                        <div>
                                            ${S}{dev.isConnected ?
                                                `<button class="btn btn-danger btn-sm" onclick="disconnectDevice()">Ngắt kết nối</button>` :
                                                `<button class="btn btn-sm" onclick="connectDevice('${S}{dev.address}')">Kết nối</button>`
                                            }
                                        </div>
                                    </div>
                                `).join('');
                            }
                        } catch (e) {
                            console.error("Lỗi lấy trạng thái:", e);
                        }
                    }

                    async function toggleBluetooth() {
                        await fetch('/api/bluetooth/toggle', { method: 'POST' });
                        setTimeout(fetchStatus, 800);
                    }
                    async function toggleAutoReconnect() {
                        const val = document.getElementById('autoReconnectToggle').checked;
                        await fetch('/api/bluetooth/auto-reconnect?enabled=' + val, { method: 'POST' });
                        fetchStatus();
                    }
                    async function connectDevice(address) {
                        await fetch('/api/bluetooth/connect?address=' + encodeURIComponent(address), { method: 'POST' });
                        setTimeout(fetchStatus, 1500);
                    }
                    async function disconnectDevice() {
                        await fetch('/api/bluetooth/disconnect', { method: 'POST' });
                        setTimeout(fetchStatus, 1000);
                    }
                    async function makeDiscoverable() {
                        const res = await fetch('/api/bluetooth/discover', { method: 'POST' });
                        const data = await res.json();
                        alert(data.message);
                        fetchStatus();
                    }
                    async function toggleLed() {
                        await fetch('/api/led/toggle', { method: 'POST' });
                        fetchStatus();
                    }
                    async function changeLedMode() {
                        const mode = document.getElementById('ledModeSelect').value;
                        await fetch('/api/led/mode?mode=' + mode, { method: 'POST' });
                        fetchStatus();
                    }
                    async function togglePromptMute() {
                        await fetch('/api/prompt-mute/toggle', { method: 'POST' });
                        fetchStatus();
                    }
                    async function setVolume(val) {
                        await fetch('/api/volume?level=' + val, { method: 'POST' });
                    }

                    fetchStatus();
                    setInterval(fetchStatus, 5000);
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    fun startServer() {
        try {
            try {
                stop()
            } catch (e: Throwable) {}

            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "WebConfigServer đã chạy thành công tại cổng $listeningPort")
        } catch (e: Throwable) {
            Log.e(TAG, "Không thể chạy WebConfigServer: ${e.message}", e)
        }
    }
}
