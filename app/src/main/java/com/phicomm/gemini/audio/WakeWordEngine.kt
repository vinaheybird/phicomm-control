package com.phicomm.gemini.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.sqrt

class WakeWordEngine(private val onWakeWordDetected: () -> Unit) {
    companion object {
        private const val TAG = "WakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val ENERGY_THRESHOLD = 2500.0 // Năng lượng âm thanh để phát hiện tiếng nói
    }

    private var isListening = false
    private var listeningThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening) return
        isListening = true

        listeningThread = Thread {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = Math.max(minBufferSize, 2048)
            val buffer = ShortArray(bufferSize)

            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Lỗi khởi tạo WakeWord AudioRecord")
                    return@Thread
                }

                audioRecord.startRecording()
                Log.d(TAG, "Đã bật lắng nghe từ khóa / giọng nói ngầm...")

                var consecutiveVoiceFrames = 0

                while (isListening && !Thread.currentThread().isInterrupted) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val rms = calculateRMS(buffer, read)
                        if (rms > ENERGY_THRESHOLD) {
                            consecutiveVoiceFrames++
                            if (consecutiveVoiceFrames >= 3) { // Phát hiện giọng nói liên tục 3 khung hình (~300ms)
                                Log.d(TAG, "Phát hiện tiếng nói / Wake word! RMS: $rms")
                                isListening = false // Tạm dừng lắng nghe ngầm để nhường cho luồng ghi âm chính
                                onWakeWordDetected()
                                break
                            }
                        } else {
                            consecutiveVoiceFrames = Math.max(0, consecutiveVoiceFrames - 1)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi WakeWordEngine: ${e.message}", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (ignored: Exception) {}
            }
        }.apply { start() }
    }

    fun stopListening() {
        isListening = false
        listeningThread?.interrupt()
        listeningThread = null
    }

    private fun calculateRMS(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        return sqrt(sum / readSize)
    }
}
