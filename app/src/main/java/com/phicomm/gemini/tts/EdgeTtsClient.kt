package com.phicomm.gemini.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class EdgeTtsClient(private val context: Context) {
    companion object {
        private const val TAG = "EdgeTtsClient"
        const val VOICE_HOAI_MY = "vi-VN-HoaiMyNeural"
        const val VOICE_NAM_MINH = "vi-VN-NamMinhNeural"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null

    fun speak(text: String, voice: String = VOICE_HOAI_MY, onCompletion: () -> Unit) {
        stop()

        Thread {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                // Dùng dịch vụ TTS tiếng Việt miễn phí chuẩn Edge-TTS / Google Translate TTS làm giải pháp trực tiếp nhẹ cho Android
                val ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedText&tl=vi&client=tw-ob"

                val request = Request.Builder()
                    .url(ttsUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) {
                    Log.e(TAG, "Lỗi lấy âm thanh TTS: code ${response.code}")
                    onCompletion()
                    return@Thread
                }

                val mp3File = File(context.cacheDir, "gemini_tts.mp3")
                FileOutputStream(mp3File).use { fos ->
                    response.body!!.byteStream().copyTo(fos)
                }

                playMp3(mp3File, onCompletion)

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi xử lý TTS: ${e.message}", e)
                onCompletion()
            }
        }.start()
    }

    private fun playMp3(file: File, onCompletion: () -> Unit) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    onCompletion()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    mediaPlayer = null
                    onCompletion()
                    true
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi phát file âm thanh MP3: ${e.message}", e)
            onCompletion()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
    }
}
