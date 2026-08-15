package com.phicomm.gemini.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OpusDecoder {
    private const val TAG = "OpusDecoder"

    fun decodeToWav(rawData: ByteArray, outputFile: File): Boolean {
        var codec: MediaCodec? = null
        try {
            Log.d(TAG, "Starting Opus decode on ${rawData.size} bytes")
            
            // 1. Create Decoder
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 16000, 1)
            
            // Opus CSD-0 (OpusHead)
            val csd0 = ByteArray(19)
            csd0[0] = 'O'.toByte()
            csd0[1] = 'p'.toByte()
            csd0[2] = 'u'.toByte()
            csd0[3] = 's'.toByte()
            csd0[4] = 'H'.toByte()
            csd0[5] = 'e'.toByte()
            csd0[6] = 'a'.toByte()
            csd0[7] = 'd'.toByte()
            csd0[8] = 1 // version
            csd0[9] = 1 // channels
            csd0[10] = 0 // pre-skip LSB
            csd0[11] = 0 // pre-skip MSB
            csd0[12] = (16000 and 0xFF).toByte() // input sample rate LSB
            csd0[13] = (16000 shr 8).toByte()
            csd0[14] = 0
            csd0[15] = 0
            csd0[16] = 0 // output gain LSB
            csd0[17] = 0 // output gain MSB
            csd0[18] = 0 // channel mapping family

            val csd1 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(0).apply { rewind() }
            val csd2 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(0).apply { rewind() }

            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
            format.setByteBuffer("csd-1", csd1)
            format.setByteBuffer("csd-2", csd2)

            codec.configure(format, null, null, 0)
            codec.start()

            val pcmStream = ByteArrayOutputStream()

            var inputOffset = 0
            var isEos = false
            val bufferInfo = MediaCodec.BufferInfo()

            var outSampleRate = 16000
            var outChannels = 1

            while (!isEos) {
                // Feed input buffers
                val inIndex = codec.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffers()[inIndex]
                    inBuffer.clear()

                    // Parse the 16-bit little endian length prefix
                    if (inputOffset + 2 <= rawData.size) {
                        val lenLsb = rawData[inputOffset].toInt() and 0xFF
                        val lenMsb = rawData[inputOffset + 1].toInt() and 0xFF
                        val packetLen = lenLsb or (lenMsb shl 8)
                        inputOffset += 2

                        if (inputOffset + packetLen <= rawData.size) {
                            inBuffer.put(rawData, inputOffset, packetLen)
                            inputOffset += packetLen
                            codec.queueInputBuffer(inIndex, 0, packetLen, 0, 0)
                        } else {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    } else {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }

                // Drain output buffers
                var outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                while (outIndex >= 0 || outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = codec.outputFormat
                        outSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        Log.d(TAG, "Decoder format changed: ${outSampleRate}Hz, $outChannels channels")
                        outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                        continue
                    }

                    val outBuffer = codec.getOutputBuffers()[outIndex]
                    val chunk = ByteArray(bufferInfo.size)
                    outBuffer.position(bufferInfo.offset)
                    outBuffer.get(chunk)
                    
                    pcmStream.write(chunk)
                    
                    codec.releaseOutputBuffer(outIndex, false)
                    
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                }
            }

            val pcmData = pcmStream.toByteArray()
            Log.d(TAG, "Decoded ${pcmData.size} bytes of PCM data. Writing WAV header...")
            
            writeWavFile(outputFile, pcmData, outSampleRate, outChannels)
            Log.d(TAG, "✅ WAV file saved to ${outputFile.absolutePath}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Opus: ${e.message}", e)
            return false
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {}
        }
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmData.size
        val totalAudioLen = totalDataLen + 36
        
        val header = ByteArray(44)
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalAudioLen and 0xff).toByte()
        header[5] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[6] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[7] = ((totalAudioLen shr 24) and 0xff).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte() // 'fmt ' chunk
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = blockAlign.toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalDataLen and 0xff).toByte()
        header[41] = ((totalDataLen shr 8) and 0xff).toByte()
        header[42] = ((totalDataLen shr 16) and 0xff).toByte()
        header[43] = ((totalDataLen shr 24) and 0xff).toByte()
        
        FileOutputStream(file).use { fos ->
            fos.write(header)
            fos.write(pcmData)
        }
    }
}
