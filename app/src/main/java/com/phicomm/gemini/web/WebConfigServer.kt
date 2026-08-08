package com.phicomm.gemini.web

import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.Gson
import com.phicomm.gemini.audio.PromptMuteController
import com.phicomm.gemini.bluetooth.BluetoothController
import com.phicomm.gemini.hardware.LedController
import fi.iki.elonen.NanoHTTPD
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val gson = Gson()

    // Port firmware gốc Phicomm R1 dùng để nhận config WiFi
    private val PHICOMM_WIFI_API = "http://localhost:8989/api/configwifi"

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
            if (!isConnectedToHomeWifi()) {
                val resp = newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
                resp.addHeader("Location", "/setup-wifi")
                return resp
            }
        }

        // Dashboard chính
        return serveDashboardHtml()
    }

    private fun handleApiRequest(session: IHTTPSession, uri: String, method: Method): Response {
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
                    val secureType = session.parameters["password_type"]?.get(0)
                        ?: session.parameters["passwordType"]?.get(0)
                        ?: if (password.isEmpty()) "OPEN" else "WPA"
                    if (ssid.isBlank()) {
                        responseMap["success"] = false
                        responseMap["message"] = "Tên WiFi (SSID) không được để trống"
                    } else {
                        val (ok, msg) = callPhicommWifiApi(ssid, password, secureType)
                        responseMap["success"] = ok
                        responseMap["message"] = msg
                    }
                }

                "/api/wifi/status" -> {
                    val ip = getCurrentIp()
                    val ssid = getCurrentSsid()
                    responseMap["success"] = true
                    responseMap["ip"] = ip
                    responseMap["ssid"] = ssid
                    responseMap["isHomeWifi"] = isConnectedToHomeWifi()
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
        val html = """
            <!DOCTYPE html>
            <html>
            <head><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Phicomm R1 WiFi</title></head>
            <body style="font-family:sans-serif; max-width:400px; margin:auto; padding:20px;">
                <h2>Kết Nối WiFi</h2>
                <div id="status" style="color:blue; font-weight:bold; margin-bottom:15px;"></div>
                <p>SSID: <input id="ssid" type="text" style="width:100%; padding:8px;"></p>
                <p>Mật khẩu: <input id="pass" type="password" style="width:100%; padding:8px;"></p>
                <button onclick="connect()" style="padding:12px; width:100%; margin-top:10px;">Kết Nối</button>
                <p style="margin-top:20px;"><a href="/">Quay lại Dashboard</a></p>
                
                <script>
                    fetch('/api/wifi/status').then(r=>r.json()).then(d=>{
                        if(d.isHomeWifi) document.getElementById('status').innerText = 'Đang dùng IP: ' + d.ip;
                    });
                    
                    async function connect() {
                        const ssid = document.getElementById('ssid').value;
                        const pass = document.getElementById('pass').value;
                        if(!ssid) return alert("Nhập SSID!");
                        
                        document.getElementById('status').innerText = 'Đang gửi lệnh...';
                        const res = await fetch('/api/wifi/connect?ssid=' + encodeURIComponent(ssid) + '&password=' + encodeURIComponent(pass), {method:'POST'});
                        const data = await res.json();
                        document.getElementById('status').innerText = data.success ? '✅ ' + data.message : '❌ ' + data.message;
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun serveDashboardHtml(): Response {
        val S = '$'
        val html = """
            <!DOCTYPE html>
            <html>
            <head><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Phicomm R1</title></head>
            <body style="font-family:sans-serif; max-width:500px; margin:auto; padding:20px;">
                <h2>🔊 Phicomm R1 Controller</h2>
                
                <fieldset>
                    <legend>Bluetooth</legend>
                    <p>Trạng thái: <b id="btStatus">...</b></p>
                    <button onclick="post('/api/bluetooth/toggle')">Bật/Tắt Bluetooth</button>
                    <button onclick="post('/api/bluetooth/discover')">Bật Dò Tìm (5p)</button>
                    <button onclick="post('/api/bluetooth/disconnect')">Ngắt Kết Nối</button>
                    <p>Tự nối lại gần nhất: <input type="checkbox" id="btAuto" onchange="post('/api/bluetooth/auto-reconnect?enabled='+this.checked)"></p>
                    <hr>
                    <b>Thiết bị đã ghép đôi:</b>
                    <div id="pairedList" style="margin-top:10px;"></div>
                </fieldset>

                <fieldset style="margin-top:20px;">
                    <legend>Cấu Hình</legend>
                    <p>Đèn LED: <input type="checkbox" id="ledToggle" onchange="post('/api/led/toggle')"></p>
                    <p>Màu LED: 
                        <select id="ledMode" onchange="post('/api/led/mode?mode='+this.value)">
                            <option value="BLUE_SOLID">Xanh lam</option>
                            <option value="CYAN_PULSE">Xanh lam nháy</option>
                            <option value="ORANGE_SOLID">Cam</option>
                            <option value="GREEN_SOLID">Xanh lá</option>
                            <option value="OFF">Tắt</option>
                        </select>
                    </p>
                    <p>Mute giọng TQ: <input type="checkbox" id="muteToggle" onchange="post('/api/prompt-mute/toggle')"></p>
                    <p>Âm lượng (<span id="volLabel"></span>): <br><input type="range" id="volRange" min="0" max="100" style="width:100%;" onchange="post('/api/volume?level='+this.value)"></p>
                </fieldset>

                <p style="margin-top:20px;"><a href="/setup-wifi">📶 Cài Đặt WiFi Mới</a></p>

                <script>
                    async function post(url) {
                        await fetch(url, {method:'POST'});
                        refresh();
                    }
                    async function refresh() {
                        try {
                            const r = await fetch('/api/status');
                            const d = await r.json();
                            
                            document.getElementById('btStatus').innerText = d.btEnabled ? 
                                (d.connectedDevice ? '🟢 Đã nối: ' + d.connectedDevice.name : '🟡 Đang bật (Chưa kết nối)') : '🔴 Đã tắt';
                            document.getElementById('btAuto').checked = d.autoReconnect;
                            document.getElementById('ledToggle').checked = d.ledEnabled;
                            document.getElementById('ledMode').value = d.ledMode;
                            document.getElementById('muteToggle').checked = d.promptMute;
                            document.getElementById('volRange').value = d.volume;
                            document.getElementById('volLabel').innerText = d.volume + '%';
                            
                            document.getElementById('pairedList').innerHTML = (d.pairedDevices||[]).map(dev => 
                                `<div style="margin-bottom:8px; border-bottom:1px solid #eee; padding-bottom:5px;">
                                    <strong>${S}{dev.name}</strong><br>
                                    <small>${S}{dev.address}</small> 
                                    ${S}{dev.isConnected ? '<b style="color:green;">(Đang phát)</b>' : `<button onclick="post('/api/bluetooth/connect?address=${S}{dev.address}')">Kết Nối</button>`}
                                </div>`
                            ).join('') || 'Chưa có thiết bị nào.';
                        } catch(e) {}
                    }
                    refresh();
                    setInterval(refresh, 5000);
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

    /**
     * Gọi API firmware gốc Phicomm R1 tại port 8989 để cấu hình WiFi.
     * Đây là cách chính xác Phicomm thiết kế sẵn — không cần WifiManager.addNetwork().
     *
     * curl -X POST --data '{"ssid":"...","secure":"WPA","password":"..."}' http://192.168.43.1:8989/api/configwifi
     */
    private fun callPhicommWifiApi(ssid: String, password: String, secureType: String): Pair<Boolean, String> {
        return try {
            val json = """{"ssid":"$ssid","secure":"$secureType","password":"$password"}"""
            Log.d(TAG, "Gọi Phicomm WiFi API: $PHICOMM_WIFI_API với body=$json")

            val url = URL(PHICOMM_WIFI_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(json) }

            val responseCode = conn.responseCode
            val responseBody = try { conn.inputStream.bufferedReader().readText() } catch (_: Exception) { "" }
            Log.d(TAG, "Phicomm API response: $responseCode — $responseBody")

            if (responseCode in 200..299) {
                Pair(true, "Lệnh kết nối đã gửi tới loa! Loa sẽ tự kết nối vào '$ssid' và tắt SoftAP sau vài giây.")
            } else {
                Pair(false, "Loa từ chối lệnh (HTTP $responseCode). Kiểm tra lại SSID và mật khẩu.")
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Không kết nối được port 8989: ${e.message}")
            Pair(false, "Không kết nối được tới firmware loa (port 8989). Đảm bảo app com.phicomm.speaker.netctl đang chạy.")
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi gọi Phicomm WiFi API: ${e.message}", e)
            Pair(false, "Lỗi: ${e.message}")
        }
    }

    private fun getCurrentSsid(): String = try {
        wifiManager.connectionInfo.ssid?.removeSurrounding("\"") ?: ""
    } catch (_: Exception) { "" }

    private fun getCurrentIp(): String = try {
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt != 0) String.format("%d.%d.%d.%d",
            ipInt and 0xff, ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
        else ""
    } catch (_: Exception) { "" }

    private fun isConnectedToHomeWifi(): Boolean {
        val ip = getCurrentIp()
        return ip.isNotEmpty() && !ip.startsWith("192.168.43.")
    }
}

