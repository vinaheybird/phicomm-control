package com.phicomm.gemini.ai

import android.util.Log
import okhttp3.*
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch

object EdgeTtsClient {
    private const val TAG = "EdgeTtsClient"
    private var httpClient: OkHttpClient = OkHttpClient()

    fun setHttpClient(client: OkHttpClient) {
        httpClient = client
    }

    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"
    private const val VOICE_NAME = "vi-VN-HoaiMyNeural"

    fun synthesize(text: String, outputFile: File): Boolean {
        var success = false
        val latch = CountDownLatch(1)
        val request = Request.Builder().url(WSS_URL).build()

        var fos: FileOutputStream? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Edge TTS WebSocket opened")
                try {
                    fos = FileOutputStream(outputFile)
                    
                    val timestamp = getTimestamp()
                    val requestId = UUID.randomUUID().toString().replace("-", "")

                    // 1. Send speech.config
                    val configMsg = "X-Timestamp:$timestamp\r\n" +
                            "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""
                    webSocket.send(configMsg)

                    // 2. Send SSML
                    val cleanText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='vi-VN'>" +
                            "<voice name='$VOICE_NAME'>" +
                            "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>$cleanText</prosody>" +
                            "</voice></speak>"

                    val ssmlMsg = "X-RequestId:$requestId\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:$timestamp\r\n" +
                            "Path:ssml\r\n\r\n" +
                            ssml
                    webSocket.send(ssmlMsg)

                } catch (e: Exception) {
                    Log.e(TAG, "Error sending to WebSocket", e)
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    Log.d(TAG, "Edge TTS finished stream")
                    success = true
                    webSocket.close(1000, "Done")
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val data = bytes.toByteArray()
                    // The binary message contains headers and audio data.
                    // The first 2 bytes are the header length in big-endian
                    if (data.size >= 2) {
                        val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                        if (data.size > headerLen + 2) {
                            val audioDataOffset = headerLen + 2
                            val audioDataLen = data.size - audioDataOffset
                            fos?.write(data, audioDataOffset, audioDataLen)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing TTS audio data", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Edge TTS WebSocket failure", t)
                success = false
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Edge TTS WebSocket closed")
            }
        }

        val webSocket = httpClient.newWebSocket(request, listener)
        try {
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Edge TTS interrupted", e)
        } finally {
            try { fos?.close() } catch (e: Exception) {}
        }
        
        return success
    }

    private fun getTimestamp(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
