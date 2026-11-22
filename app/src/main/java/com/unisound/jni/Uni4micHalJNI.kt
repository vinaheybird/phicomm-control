package com.unisound.jni

/**
 * Exact JNI bridge for stock Phicomm R1 library `/system/lib/libUni4micHalJNI.so`.
 * Package and class name MUST remain `com.unisound.jni.Uni4micHalJNI`.
 * Verified against stock `Unisound.apk` and ELF symbols in `docs/re/4mic-hal-contract.md`.
 */
class Uni4micHalJNI private constructor() {

    @Volatile
    private var isInitialized: Boolean = false

    @Synchronized
    fun init(mode: Int): Int {
        if (isInitialized) return 0
        val status = initHal(mode)
        if (status == 0) {
            isInitialized = true
        }
        return status
    }

    @Synchronized
    fun release(): Int {
        if (!isInitialized) return -1
        val status = releaseHal()
        if (status == 0) {
            isInitialized = false
        }
        return status
    }

    private external fun initHal(mode: Int): Int
    private external fun releaseHal(): Int

    external fun openAudioIn(ch: Int): Long
    external fun closeAudioIn(handle: Long): Int
    external fun startRecorder(handle: Long): Int
    external fun stopRecorder(handle: Long): Int
    external fun readData(handle: Long, buf: ByteArray, len: Int): Int

    external fun close4MicAlgorithm(flag: Int): Int
    external fun set4MicWakeUpStatus(status: Int): Int
    external fun get4MicDoaResult(): Int
    external fun get4MicBoardVersion(): String?
    external fun set4MicDebugMode(mode: Int): Int
    external fun set4MicDelayTime(ms: Int): Int
    external fun set4MicUtteranceTimeLen(ms: Int): Int
    external fun set4MicWakeupStartLen(len: Int): Int
    external fun set4MicOneShotStartLen(len: Int): Int
    external fun set4MicOneShotReady(ready: Int): Int
    external fun get4MicOneShotReady(): Int

    companion object {
        init {
            System.loadLibrary("Uni4micHalJNI")
        }

        @Volatile
        private var instance: Uni4micHalJNI? = null

        @JvmStatic
        fun getInstance(): Uni4micHalJNI =
            instance ?: synchronized(this) {
                instance ?: Uni4micHalJNI().also { instance = it }
            }
    }
}
