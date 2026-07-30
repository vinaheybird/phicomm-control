package com.phicomm.gemini.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class AudioRecorder {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun startRecording(outputWavFile: File, maxDurationSeconds: Int = 10, onFinished: (File?) -> Unit) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "AudioRecord buffer size invalid: $minBufferSize")
            onFinished(null)
            return
        }

        val bufferSize = Math.max(minBufferSize, SAMPLE_RATE * 2)
        val rawPcmFile = File(outputWavFile.parentFile, "temp_record.pcm")

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed!")
                onFinished(null)
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                val data = ByteArray(1024)
                var totalBytesRead = 0
                val maxBytes = SAMPLE_RATE * 2 * maxDurationSeconds // 16bit = 2 bytes per sample

                FileOutputStream(rawPcmFile).use { fos ->
                    while (isRecording && totalBytesRead < maxBytes) {
                        val read = audioRecord?.read(data, 0, data.size) ?: 0
                        if (read > 0) {
                            fos.write(data, 0, read)
                            totalBytesRead += read
                        } else {
                            break
                        }
                    }
                }

                // Chuyển đổi PCM thành WAV chuẩn
                val wavCreated = convertPcmToWav(rawPcmFile, outputWavFile, SAMPLE_RATE, 1, 16)
                rawPcmFile.delete()

                if (wavCreated) {
                    onFinished(outputWavFile)
                } else {
                    onFinished(null)
                }
            }.apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi bắt đầu ghi âm: ${e.message}", e)
            onFinished(null)
        }
    }

    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord: ${e.message}")
        }
        audioRecord = null
    }

    private fun convertPcmToWav(pcmFile: File, wavFile: File, sampleRate: Int, channels: Int, bitDepth: Int): Boolean {
        if (!pcmFile.exists()) return false
        val pcmSize = pcmFile.length().toInt()
        val totalDataLen = pcmSize + 36
        val byteRate = sampleRate * channels * (bitDepth / 8)

        val header = ByteArray(44)
        // RIFF/WAVE header
        header[0] = 'R'.toByte(); header[1] = 'I'.toByte(); header[2] = 'F'.toByte(); header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte(); header[9] = 'A'.toByte(); header[10] = 'V'.toByte(); header[11] = 'E'.toByte()
        header[12] = 'f'.toByte(); header[13] = 'm'.toByte(); header[14] = 't'.toByte(); header[15] = ' '.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // subchunk1 size (16 for PCM)
        header[20] = 1; header[21] = 0 // Audio format 1 = PCM
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * (bitDepth / 8)).toByte(); header[33] = 0 // block align
        header[34] = bitDepth.toByte(); header[35] = 0 // bits per sample
        header[36] = 'd'.toByte(); header[37] = 'a'.toByte(); header[38] = 't'.toByte(); header[39] = 'a'.toByte()
        header[40] = (pcmSize and 0xff).toByte()
        header[41] = (pcmSize shr 8 and 0xff).toByte()
        header[42] = (pcmSize shr 16 and 0xff).toByte()
        header[43] = (pcmSize shr 24 and 0xff).toByte()

        return try {
            FileOutputStream(wavFile).use { out ->
                out.write(header, 0, 44)
                pcmFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "WAV header conversion error: ${e.message}")
            false
        }
    }
}
