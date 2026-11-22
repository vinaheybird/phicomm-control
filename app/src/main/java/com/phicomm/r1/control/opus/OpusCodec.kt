package com.phicomm.r1.control.opus

interface OpusCodec : AutoCloseable {
    val sampleRateHz: Int
    val channels: Int
    val frameDurationMs: Int
    val samplesPerFrame: Int
    val pcmBytesPerFrame: Int

    /** Encodes exactly [pcmBytesPerFrame] bytes (16-bit LE mono PCM) into a single Opus packet. */
    fun encode(pcm16Le: ByteArray): ByteArray

    /** Decodes a single Opus packet into [pcmBytesPerFrame] bytes (16-bit LE mono PCM). */
    fun decode(opusPacket: ByteArray): ByteArray
}
