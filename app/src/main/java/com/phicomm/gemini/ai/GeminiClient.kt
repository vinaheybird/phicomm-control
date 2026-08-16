package com.phicomm.gemini.ai

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    // TODO: The user should provide their API key via a configuration web page later.
    private const val API_KEY = "YOUR_GEMINI_API_KEY" 
    
    // Default client (no VPN protect). Replace with VpnAwareHttpClient.get(vpnService) when VPN is active.
    private var httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun setHttpClient(client: OkHttpClient) {
        httpClient = client
    }

    fun uploadAudioAndAsk(audioFile: File, prompt: String = "Bạn là một trợ lý ảo thông minh. Hãy trả lời ngắn gọn, thân thiện bằng tiếng Việt câu hỏi trong file âm thanh này."): String? {
        val apiKey = com.phicomm.gemini.config.ConfigManager.getGeminiApiKey()
        if (apiKey.isEmpty()) {
            Log.e(TAG, "Missing Gemini API Key!")
            return "Chưa cấu hình khóa API Gemini."
        }

        try {
            val fileUri = uploadFile(audioFile, apiKey)
            if (fileUri == null) {
                Log.e(TAG, "Failed to upload audio to Gemini")
                return "Đã xảy ra lỗi khi tải âm thanh lên máy chủ."
            }

            return generateContent(fileUri, prompt, apiKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Gemini interaction", e)
            return "Lỗi kết nối máy chủ AI."
        }
    }

    private fun uploadFile(file: File, apiKey: String): String? {
        val url = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey"
        
        val requestBody = file.asRequestBody("audio/wav".toMediaTypeOrNull())
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("X-Goog-Upload-Protocol", "raw")
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
        
        if (response.isSuccessful && responseBody != null) {
            val json = JSONObject(responseBody)
            val fileInfo = json.optJSONObject("file")
            return fileInfo?.optString("uri")
        } else {
            Log.e(TAG, "Upload failed: ${response.code} $responseBody")
        }
        return null
    }

    private fun generateContent(fileUri: String, prompt: String, apiKey: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        
        val jsonPayload = """
            {
                "contents": [
                    {
                        "parts": [
                            {"text": "$prompt"},
                            {
                                "fileData": {
                                    "mimeType": "audio/wav",
                                    "fileUri": "$fileUri"
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val requestBody = jsonPayload.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
        
        if (response.isSuccessful && responseBody != null) {
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text")
                }
            }
        } else {
            Log.e(TAG, "Generate content failed: ${response.code} $responseBody")
        }
        return null
    }
}
