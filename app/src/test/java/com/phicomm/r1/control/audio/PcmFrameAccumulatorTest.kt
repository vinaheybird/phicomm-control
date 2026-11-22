package com.phicomm.r1.control.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmFrameAccumulatorTest {

    @Test
    fun emitsExact60MsFramesFrom37Point5MsChunks() {
        // 37.5ms mono = 1200 bytes; 8 chunks = 9600 bytes = exactly 5 frames of 1920 bytes (60ms)
        val acc = PcmFrameAccumulator(1920)
        val emitted = mutableListOf<ByteArray>()

        val sourceStream = ByteArray(9600) { (it % 251).toByte() }
        for (chunkIdx in 0 until 8) {
            val chunk = sourceStream.copyOfRange(chunkIdx * 1200, (chunkIdx + 1) * 1200)
            acc.append(chunk) { frame -> emitted.add(frame) }
        }

        assertEquals(5, emitted.size)
        assertEquals(0, acc.pendingBytes)
        emitted.forEach { assertEquals(1920, it.size) }

        val reconstructed = ByteArray(9600)
        emitted.forEachIndexed { i, frame ->
            System.arraycopy(frame, 0, reconstructed, i * 1920, 1920)
        }
        assertTrue(sourceStream.contentEquals(reconstructed))
    }

    @Test
    fun retainsRemainderAndResetsCleanly() {
        val acc = PcmFrameAccumulator(1920)
        val emitted = mutableListOf<ByteArray>()
        acc.append(ByteArray(2500)) { emitted.add(it) }
        assertEquals(1, emitted.size)
        assertEquals(580, acc.pendingBytes)

        acc.reset()
        assertEquals(0, acc.pendingBytes)
    }
}
