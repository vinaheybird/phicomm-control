package com.phicomm.r1.control.opus

import com.phicomm.r1.control.audio.PcmChannelSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ConcentusOpusCodecTest {

    @Test
    fun encodesAndDecodes60MsMono16KhzVoiceFrame() {
        val codec = ConcentusOpusCodec(
            sampleRateHz = 16000,
            channels = 1,
            frameDurationMs = 60,
            bitrateBps = 24000,
            complexity = 2
        )

        assertEquals(960, codec.samplesPerFrame)
        assertEquals(1920, codec.pcmBytesPerFrame)

        // Generate 5 consecutive 60ms 440Hz tone frames so Opus encoder warms up past initial lookahead
        var lastDecoded = ByteArray(0)
        var lastPacketSize = 0
        for (frameIndex in 0 until 5) {
            val pcm = ByteArray(1920)
            for (i in 0 until 960) {
                val sampleIdx = frameIndex * 960 + i
                val s = (sin(2.0 * PI * 440.0 * sampleIdx / 16000.0) * 12000.0).toInt().toShort()
                pcm[i * 2] = (s.toInt() and 0xFF).toByte()
                pcm[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
            }
            val packet = codec.encode(pcm)
            lastPacketSize = packet.size
            assertTrue("Encoded Opus packet should be compact", packet.size in 10..400)

            lastDecoded = codec.decode(packet)
            assertEquals(1920, lastDecoded.size)
        }

        val decodedRms = PcmChannelSelector.computeMonoRms(lastDecoded)
        assertTrue("Decoded RMS ($decodedRms) should preserve strong voice signal energy", decodedRms > 3000)
        assertTrue("Packet size ($lastPacketSize) should be > 0", lastPacketSize > 0)

        val bench = codec.runBenchmark(20)
        assertEquals(20, bench.framesTested)
        assertTrue(bench.avgPacketBytes > 0)
        codec.close()
    }
}
