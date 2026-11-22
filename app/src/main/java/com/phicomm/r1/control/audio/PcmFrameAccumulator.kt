package com.phicomm.r1.control.audio

/**
 * Accumulates arbitrary-sized 16-bit mono PCM byte chunks and emits exact
 * fixed-size frames (e.g., 1,920 bytes = 60 ms @ 16 kHz mono 16-bit PCM)
 * with zero sample loss or timing drift.
 */
class PcmFrameAccumulator(
    val frameBytes: Int = DEFAULT_60MS_MONO_16KHZ_BYTES
) {
    private val buffer = ByteArray(frameBytes)
    private var bufferedBytes = 0

    init {
        require(frameBytes > 0 && frameBytes % 2 == 0) {
            "frameBytes must be a positive even integer"
        }
    }

    val pendingBytes: Int
        get() = bufferedBytes

    fun append(pcmChunk: ByteArray, length: Int = pcmChunk.size, onFrame: (ByteArray) -> Unit) {
        var offset = 0
        var remaining = length.coerceIn(0, pcmChunk.size)
        while (remaining > 0) {
            val spaceLeft = frameBytes - bufferedBytes
            val toCopy = minOf(remaining, spaceLeft)
            System.arraycopy(pcmChunk, offset, buffer, bufferedBytes, toCopy)
            bufferedBytes += toCopy
            offset += toCopy
            remaining -= toCopy

            if (bufferedBytes == frameBytes) {
                onFrame(buffer.copyOf())
                bufferedBytes = 0
            }
        }
    }

    fun reset() {
        bufferedBytes = 0
    }

    companion object {
        const val DEFAULT_60MS_MONO_16KHZ_BYTES = 1920
    }
}
