package com.phicomm.gemini.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.net.URLEncoder

class TtsManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    init {
        Log.d(TAG, "Đã khởi tạo TtsManager sử dụng MediaPlayer (Google Translate TTS)")
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        Log.d(TAG, "Đang phát âm thanh cho text: $text")
        
        // Cắt ngắn text nếu quá dài (Google Translate TTS thường giới hạn 200 ký tự)
        // Trong prompt của Gemini ta đã yêu cầu "concise answer" nên thường sẽ ngắn.
        val safeText = if (text.length > 200) text.substring(0, 197) + "..." else text
        
        try {
            val encodedText = URLEncoder.encode(safeText, "UTF-8")
            // Sử dụng Google Translate TTS API miễn phí
            val url = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedText&tl=vi&client=tw-ob"

            // Dọn dẹp MediaPlayer cũ nếu đang chạy
            stop()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, Uri.parse(url))
                setOnPreparedListener { 
                    Log.d(TAG, "MediaPlayer đã sẵn sàng, bắt đầu phát...")
                    it.start() 
                }
                setOnCompletionListener { 
                    Log.d(TAG, "Phát TTS hoàn tất.")
                    it.release() 
                    mediaPlayer = null
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer Error: what=$what, extra=$extra")
                    true
                }
                prepareAsync() // Prepare bất đồng bộ để không block luồng hiện tại
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi Google Translate TTS hoặc MediaPlayer: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi stop MediaPlayer: ${e.message}")
        }
    }

    fun shutdown() {
        Log.d(TAG, "Tắt TtsManager")
        stop()
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
