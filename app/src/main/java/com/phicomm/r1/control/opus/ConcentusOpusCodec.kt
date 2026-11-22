package com.phicomm.r1.control.opus

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import io.github.jaredmdobson.concentus.OpusSignal
import kotlin.math.PI
import kotlin.math.sin

data class OpusBenchmarkReport(
    val framesTested: Int,
    val avgEncodeUs: Long,
    val maxEncodeUs: Long,
    val avgDecodeUs: Long,
    val avgPacketBytes: Int
)

/**
 * Pure-JVM / Android RFC-6716 Opus encoder and decoder built on Concentus.
 * Configured for Xiaozhi Cloud: 16 kHz, mono, 60 ms frames (960 samples / 1,920 bytes).
 */
class ConcentusOpusCodec(
    override val sampleRateHz: Int = 16000,
    override val channels: Int = 1,
    override val frameDurationMs: Int = 60,
    bitrateBps: Int = 24000,
    complexity: Int = 2
) : OpusCodec {

    override val samplesPerFrame: Int = (sampleRateHz * frameDurationMs) / 1000
    override val pcmBytesPerFrame: Int = samplesPerFrame * channels * 2

    private val encoder: OpusEncoder = OpusEncoder(sampleRateHz, channels, OpusApplication.OPUS_APPLICATION_VOIP).apply {
        setBitrate(bitrateBps)
        setComplexity(complexity)
        setSignalType(OpusSignal.OPUS_SIGNAL_VOICE)
    }

    private val decoder: OpusDecoder = OpusDecoder(sampleRateHz, channels)
    private val encodeShortBuf = ShortArray(samplesPerFrame * channels)
    private val encodeOutBuf = ByteArray(MAX_OPUS_PACKET_BYTES)
    private val decodeShortBuf = ShortArray(samplesPerFrame * channels)

    @Synchronized
    override fun encode(pcm16Le: ByteArray): ByteArray {
        require(pcm16Le.size == pcmBytesPerFrame) {
            "Expected $pcmBytesPerFrame PCM bytes (${frameDurationMs}ms @ ${sampleRateHz}Hz), got ${pcm16Le.size}"
        }
        bytesToLeShorts(pcm16Le, encodeShortBuf)
        val encodedLen = encoder.encode(
            encodeShortBuf,
            0,
            samplesPerFrame,
            encodeOutBuf,
            0,
            encodeOutBuf.size
        )
        check(encodedLen > 0) { "Opus encode failed with code $encodedLen" }
        return encodeOutBuf.copyOf(encodedLen)
    }

    @Synchronized
    override fun decode(opusPacket: ByteArray): ByteArray {
        require(opusPacket.isNotEmpty()) { "Opus packet must not be empty" }
        val decodedSamples = decoder.decode(
            opusPacket,
            0,
            opusPacket.size,
            decodeShortBuf,
            0,
            samplesPerFrame,
            false
        )
        check(decodedSamples > 0) { "Opus decode failed with code $decodedSamples" }
        val out = ByteArray(decodedSamples * channels * 2)
        leShortsToBytes(decodeShortBuf, decodedSamples * channels, out)
        return out
    }

    override fun close() {
        // Pure JVM objects; nothing native to release.
    }

    fun runBenchmark(frameCount: Int = 50): OpusBenchmarkReport {
        val toneFrame = generateSineFramePcm(440.0)
        var totalEncodeUs = 0L
        var maxEncodeUs = 0L
        var totalDecodeUs = 0L
        var totalPacketBytes = 0L

        // Warm up JIT/ART before timing
        repeat(3) {
            val pkt = encode(toneFrame)
            decode(pkt)
        }

        for (i in 0 until frameCount) {
            val t0 = System.nanoTime()
            val pkt = encode(toneFrame)
            val t1 = System.nanoTime()
            val decoded = decode(pkt)
            val t2 = System.nanoTime()

            check(decoded.size == pcmBytesPerFrame) {
                "Decoded size mismatch: expected $pcmBytesPerFrame, got ${decoded.size}"
            }

            val encUs = (t1 - t0) / 1000L
            val decUs = (t2 - t1) / 1000L
            totalEncodeUs += encUs
            if (encUs > maxEncodeUs) maxEncodeUs = encUs
            totalDecodeUs += decUs
            totalPacketBytes += pkt.size
        }

        return OpusBenchmarkReport(
            framesTested = frameCount,
            avgEncodeUs = totalEncodeUs / frameCount,
            maxEncodeUs = maxEncodeUs,
            avgDecodeUs = totalDecodeUs / frameCount,
            avgPacketBytes = (totalPacketBytes / frameCount).toInt()
        )
    }

    private fun generateSineFramePcm(freqHz: Double): ByteArray {
        val pcm = ByteArray(pcmBytesPerFrame)
        for (i in 0 until samplesPerFrame) {
            val sample = (sin(2.0 * PI * freqHz * i / sampleRateHz) * 12000.0).toInt().toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    companion object {
        private const val MAX_OPUS_PACKET_BYTES = 1275

        private fun bytesToLeShorts(src: ByteArray, dst: ShortArray) {
            var byteIdx = 0
            for (i in dst.indices) {
                val lo = src[byteIdx].toInt() and 0xFF
                val hi = src[byteIdx + 1].toInt()
                dst[i] = ((hi shl 8) or lo).toShort()
                byteIdx += 2
            }
        }

        private fun leShortsToBytes(src: ShortArray, count: Int, dst: ByteArray) {
            var byteIdx = 0
            for (i in 0 until count) {
                val s = src[i].toInt()
                dst[byteIdx] = (s and 0xFF).toByte()
                dst[byteIdx + 1] = ((s shr 8) and 0xFF).toByte()
                byteIdx += 2
            }
        }
    }
}
