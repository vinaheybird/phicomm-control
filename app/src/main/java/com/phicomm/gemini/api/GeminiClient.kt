package com.phicomm.gemini.api

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {
    private const val TAG = "GeminiClient"
    
    fun sendAudioQuery(wavFile: File, apiKey: String, modelName: String): String? {
        if (!wavFile.exists()) {
            Log.e(TAG, "File audio không tồn tại: ${wavFile.absolutePath}")
            return null
        }

        try {
            Log.d(TAG, "Đang đọc file wav kích thước ${wavFile.length()} bytes...")
            val audioBytes = wavFile.readBytes()
            Log.d(TAG, "Đang encode base64...")
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "audio/wav")
                                    put("data", base64Audio)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", "You are a helpful AI voice assistant in a smart speaker. Listen to the user's audio query and answer it directly and concisely in Vietnamese. Do NOT transcribe the audio, just provide the final answer.")
                            })
                        })
                    })
                })
            }.toString()

            Log.d(TAG, "Đang gửi request với model: $modelName ...")
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

            val responseCode = conn.responseCode
            Log.d(TAG, "Gemini API HTTP Status ($modelName): $responseCode")

            val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseBody = inputStream.bufferedReader().use { it.readText() }

            if (responseCode in 200..299) {
                Log.d(TAG, "Gemini phản hồi thành công")
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text")
                        Log.d(TAG, "Nội dung phản hồi: $text")
                        return text
                    }
                }
                Log.e(TAG, "Không thể parse JSON từ Gemini: $responseBody")
            } else {
                Log.e(TAG, "Lỗi từ Gemini API: $responseBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception khi gọi Gemini API: ${e.message}", e)
        }
        return null
    }
}
