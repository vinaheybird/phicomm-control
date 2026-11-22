package com.phicomm.r1.control.audio

import com.phicomm.r1.control.model.MonoChannelStrategy
import kotlin.math.sqrt

data class ChannelExtractionResult(
    val monoPcm: ByteArray,
    val channel0Rms: Int,
    val channel1Rms: Int
)

/**
 * Extracts 16-bit signed little-endian mono PCM from raw 4-mic HAL output
 * (either 1-channel mono or 2-channel interleaved stereo) and calculates
 * per-channel RMS energy for live hardware verification.
 */
object PcmChannelSelector {

    fun extract(
        rawPcm: ByteArray,
        length: Int,
        captureChannelMode: Int,
        strategy: MonoChannelStrategy
    ): ChannelExtractionResult {
        val validLength = length.coerceIn(0, rawPcm.size)
        if (validLength == 0) {
            return ChannelExtractionResult(ByteArray(0), 0, 0)
        }

        if (captureChannelMode == 1 || strategy == MonoChannelStrategy.PASSTHROUGH_MONO) {
            val evenLen = validLength and -2
            val out = rawPcm.copyOfRange(0, evenLen)
            val rms = computeMonoRms(out, evenLen)
            return ChannelExtractionResult(out, rms, 0)
        }

        // 2-channel interleaved 16-bit LE PCM: 4 bytes per stereo frame [ch0_lo, ch0_hi, ch1_lo, ch1_hi]
        val frameCount = validLength / 4
        if (frameCount == 0) {
            return ChannelExtractionResult(ByteArray(0), 0, 0)
        }

        val monoOut = ByteArray(frameCount * 2)
        var sumSq0 = 0.0
        var sumSq1 = 0.0

        var inIdx = 0
        var outIdx = 0
        for (i in 0 until frameCount) {
            val s0 = readLeShort(rawPcm, inIdx)
            val s1 = readLeShort(rawPcm, inIdx + 2)
            sumSq0 += s0.toDouble() * s0.toDouble()
            sumSq1 += s1.toDouble() * s1.toDouble()

            val selected: Int = when (strategy) {
                MonoChannelStrategy.CHANNEL_0 -> s0
                MonoChannelStrategy.CHANNEL_1 -> s1
                MonoChannelStrategy.AVERAGE_MIX -> (s0 + s1) / 2
                MonoChannelStrategy.PASSTHROUGH_MONO -> s0
            }

            monoOut[outIdx] = (selected and 0xFF).toByte()
            monoOut[outIdx + 1] = ((selected shr 8) and 0xFF).toByte()

            inIdx += 4
            outIdx += 2
        }

        val rms0 = sqrt(sumSq0 / frameCount).toInt()
        val rms1 = sqrt(sumSq1 / frameCount).toInt()
        return ChannelExtractionResult(monoOut, rms0, rms1)
    }

    fun computeMonoRms(pcm16Le: ByteArray, length: Int = pcm16Le.size): Int {
        val samples = (length.coerceIn(0, pcm16Le.size)) / 2
        if (samples == 0) return 0
        var sumSq = 0.0
        var idx = 0
        for (i in 0 until samples) {
            val sample = readLeShort(pcm16Le, idx)
            sumSq += sample.toDouble() * sample.toDouble()
            idx += 2
        }
        return sqrt(sumSq / samples).toInt()
    }

    private fun readLeShort(buf: ByteArray, offset: Int): Int {
        val lo = buf[offset].toInt() and 0xFF
        val hi = buf[offset + 1].toInt()
        return (hi shl 8) or lo
    }
}
