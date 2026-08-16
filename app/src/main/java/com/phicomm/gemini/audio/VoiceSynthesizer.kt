package com.phicomm.gemini.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.phicomm.gemini.ai.EdgeTtsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceSynthesizer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun speak(text: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val outputFile = File(context.getExternalFilesDir(null), "tts_${System.currentTimeMillis()}.mp3")
                val success = EdgeTtsClient.synthesize(text, outputFile)
                
                if (success && outputFile.exists()) {
                    withContext(Dispatchers.Main) {
                        playAudio(outputFile)
                    }
                } else {
                    Log.e("VoiceSynthesizer", "Edge TTS failed to synthesize audio")
                }
            } catch (e: Exception) {
                Log.e("VoiceSynthesizer", "Exception during synthesis", e)
            }
        }
    }

    private fun playAudio(file: File) {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    // Xóa file sau khi phát xong để dọn dẹp dung lượng
                    try { file.delete() } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceSynthesizer", "Error playing audio", e)
        }
    }

    fun shutdown() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
    }

    companion object {
        @Volatile
        private var instance: VoiceSynthesizer? = null

        fun getInstance(context: Context): VoiceSynthesizer {
            return instance ?: synchronized(this) {
                instance ?: VoiceSynthesizer(context.applicationContext).also { instance = it }
            }
        }
    }
}
