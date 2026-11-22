package com.phicomm.r1.control.audio

import com.phicomm.r1.control.model.MonoChannelStrategy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmChannelSelectorTest {

    private fun leBytesOf(vararg samples: Short): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { idx, s ->
            out[idx * 2] = (s.toInt() and 0xFF).toByte()
            out[idx * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun extractChannel0AndChannel1FromStereoInterleaved() {
        // Stereo frames: (1000, -500), (2000, -1000), (3000, -1500)
        val stereo = leBytesOf(1000, -500, 2000, -1000, 3000, -1500)

        val ch0 = PcmChannelSelector.extract(stereo, stereo.size, 2, MonoChannelStrategy.CHANNEL_0)
        assertArrayEquals(leBytesOf(1000, 2000, 3000), ch0.monoPcm)
        assertEquals(2160, ch0.channel0Rms)
        assertEquals(1080, ch0.channel1Rms)

        val ch1 = PcmChannelSelector.extract(stereo, stereo.size, 2, MonoChannelStrategy.CHANNEL_1)
        assertArrayEquals(leBytesOf(-500, -1000, -1500), ch1.monoPcm)
        assertEquals(2160, ch1.channel0Rms)
        assertEquals(1080, ch1.channel1Rms)
    }

    @Test
    fun extractAverageMixFromStereoInterleaved() {
        val stereo = leBytesOf(1000, 2000, -400, 200)
        val mix = PcmChannelSelector.extract(stereo, stereo.size, 2, MonoChannelStrategy.AVERAGE_MIX)
        assertArrayEquals(leBytesOf(1500, -100), mix.monoPcm)
    }

    @Test
    fun passthroughMonoPreservesSamples() {
        val mono = leBytesOf(400, -400, 400, -400)
        val res = PcmChannelSelector.extract(mono, mono.size, 1, MonoChannelStrategy.CHANNEL_0)
        assertArrayEquals(mono, res.monoPcm)
        assertEquals(400, res.channel0Rms)
        assertEquals(0, res.channel1Rms)
    }

    @Test
    fun handlesEmptyAndTruncatedInputGracefully() {
        val empty = PcmChannelSelector.extract(ByteArray(0), 0, 2, MonoChannelStrategy.CHANNEL_0)
        assertEquals(0, empty.monoPcm.size)
        assertEquals(0, empty.channel0Rms)

        val tiny = PcmChannelSelector.extract(byteArrayOf(1, 2, 3), 3, 2, MonoChannelStrategy.CHANNEL_0)
        assertEquals(0, tiny.monoPcm.size)
    }
}
