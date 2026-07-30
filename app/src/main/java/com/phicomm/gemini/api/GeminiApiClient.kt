package com.phicomm.gemini.api

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiApiClient {
    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val conversationHistory = ArrayList<JsonObject>()

    fun clearHistory() {
        conversationHistory.clear()
    }

    fun sendAudioToGemini(apiKey: String, audioWavFile: File, onResponse: (String?, String?) -> Unit) {
        if (apiKey.isEmpty()) {
            onResponse(null, "Chưa cấu hình Gemini API Key. Vui lòng vào trang Web Config để nhập API Key.")
            return
        }

        try {
            val audioBytes = audioWavFile.readBytes()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val rootJson = JsonObject()

            // System Instruction giúp Gemini trả lời ngắn gọn phục vụ phát âm thanh ra loa
            val systemInstruction = JsonObject()
            val systemParts = JsonArray()
            val systemText = JsonObject()
            systemText.addProperty("text", "Bạn là trợ lý ảo thông minh trên loa Phicomm R1. Hãy trả lời bằng tiếng Việt ngắn gọn, súc tích, thân thiện và tự nhiên, cô đọng trong 1-3 câu để đọc qua loa âm thanh.")
            systemParts.add(systemText)
            systemInstruction.add("parts", systemParts)
            rootJson.add("system_instruction", systemInstruction)

            val contentsArray = JsonArray()

            // Thêm lịch sử hội thoại trước đó (nếu có)
            for (historyItem in conversationHistory) {
                contentsArray.add(historyItem)
            }

            // Tạo tin nhắn mới chứa file âm thanh WAV
            val currentUserMessage = JsonObject()
            currentUserMessage.addProperty("role", "user")
            val userParts = JsonArray()

            val inlineDataPart = JsonObject()
            val inlineData = JsonObject()
            inlineData.addProperty("mime_type", "audio/wav")
            inlineData.addProperty("data", base64Audio)
            inlineDataPart.add("inline_data", inlineData)

            userParts.add(inlineDataPart)
            currentUserMessage.add("parts", userParts)

            contentsArray.add(currentUserMessage)
            rootJson.add("contents", contentsArray)

            val requestBody = rootJson.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e(TAG, "Lỗi kết nối Gemini API: ${e.message}", e)
                    onResponse(null, "Không thể kết nối đến Gemini API. Kiểm tra lại mạng internet.")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val responseStr = response.body?.string()
                    if (!response.isSuccessful || responseStr == null) {
                        Log.e(TAG, "Gemini API trả về lỗi code ${response.code}: $responseStr")
                        onResponse(null, "Lỗi Gemini API code ${response.code}.")
                        return
                    }

                    try {
                        val responseJson = gson.fromJson(responseStr, JsonObject::class.java)
                        val candidates = responseJson.getAsJsonArray("candidates")
                        if (candidates != null && candidates.size() > 0) {
                            val firstCandidate = candidates[0].asJsonObject
                            val content = firstCandidate.getAsJsonObject("content")
                            val parts = content.getAsJsonArray("parts")
                            val responseText = parts[0].asJsonObject.get("text").asString

                            // Lưu vào lịch sử hội thoại
                            val modelResponse = JsonObject()
                            modelResponse.addProperty("role", "model")
                            val modelParts = JsonArray()
                            val modelTextPart = JsonObject()
                            modelTextPart.addProperty("text", responseText)
                            modelParts.add(modelTextPart)
                            modelResponse.add("parts", modelParts)

                            // Thêm lượt trao đổi vừa rồi vào history (giới hạn 6 lượt gần nhất)
                            conversationHistory.add(currentUserMessage)
                            conversationHistory.add(modelResponse)
                            while (conversationHistory.size > 12) {
                                conversationHistory.removeAt(0)
                            }

                            onResponse(responseText, null)
                        } else {
                            onResponse(null, "Gemini không trả về phản hồi nào.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi giải mã phản hồi Gemini: ${e.message}", e)
                        onResponse(null, "Lỗi xử lý phản hồi từ Gemini.")
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi tạo request Gemini: ${e.message}", e)
            onResponse(null, "Lỗi khởi tạo yêu cầu Gemini API.")
        }
    }
}
