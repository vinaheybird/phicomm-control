package com.phicomm.r1.control.wakeup

import android.util.Log
import cn.yunzhisheng.asrfix.JniAsrFix

interface WakeWordEngine {
    fun initialize(modelDirPathWithSlash: String, grammarPathsCsv: String): Result<Int>
    fun startWakeup(modelId: Int): Result<Unit>
    fun feedAudio(pcmBytes: ByteArray, length: Int): Int
    fun fetchRawResult(): String?
    fun getUtteranceBoundsMs(): Pair<Int, Int>
    fun stopSession()
    fun cancelSession()
    fun release()
    fun getVersion(): String?
}

class AsrFixWakeWordEngine(
    private val jniProvider: () -> JniAsrFix = { JniAsrFix.getInstance() },
    private val appKey: String = DEFAULT_APP_KEY
) : WakeWordEngine {

    private var jni: JniAsrFix? = null
    @Volatile
    private var initialized: Boolean = false

    override fun initialize(modelDirPathWithSlash: String, grammarPathsCsv: String): Result<Int> =
        runCatching {
            val engine = jniProvider()
            jni = engine

            val initRc = engine.initEngine(modelDirPathWithSlash, grammarPathsCsv)
            Log.i(TAG, "JniAsrFix.init(dir=$modelDirPathWithSlash, grammars=$grammarPathsCsv) = $initRc")
            if (initRc != 0) {
                error("JniAsrFix.init failed with code $initRc")
            }

            val modelNum = engine.getIntOption(JniAsrFix.OPT_MODEL_NUM)
            var defaultModelId = 0
            for (i in 0 until modelNum) {
                val id = engine.setIntOption(JniAsrFix.OPT_MODEL_INDEX, i)
                Log.i(TAG, "JniAsrFix model[$i/$modelNum] -> modelId=$id")
                if (i == 0) defaultModelId = id
            }
            if (modelNum < 2) {
                defaultModelId = engine.setIntOption(JniAsrFix.OPT_MODEL_INDEX, 0)
            }

            // Apply empirically verified post-init options from com.unisound.sdk.m.a
            engine.setIntOption(JniAsrFix.OPT_POST_INIT_FLAG, 1)
            engine.setIntOption(JniAsrFix.OPT_BEAM_TIMEOUT, 3000)
            engine.setIntOption(JniAsrFix.OPT_DECODER_CONFIG, 8)
            engine.setStringOption(JniAsrFix.OPT_APP_KEY, appKey)
            engine.setIntOption(JniAsrFix.OPT_WAKEUP_MODE, 1)

            initialized = true
            Log.i(
                TAG,
                "JniAsrFix initialized: version=${getVersion()}, modelNum=$modelNum, defaultModelId=$defaultModelId"
            )
            defaultModelId
        }

    override fun startWakeup(modelId: Int): Result<Unit> = runCatching {
        val engine = jni ?: error("JniAsrFix not initialized")
        check(initialized) { "JniAsrFix must be initialized before startWakeup" }
        val rc = engine.startRecognition(WAKEUP_GRAMMAR_TAG, modelId)
        Log.d(TAG, "JniAsrFix.start($WAKEUP_GRAMMAR_TAG, modelId=$modelId) = $rc")
        if (rc != 0) {
            error("JniAsrFix.start failed with code $rc")
        }
    }

    override fun feedAudio(pcmBytes: ByteArray, length: Int): Int {
        val engine = jni ?: return -1
        if (!initialized) return -1
        return engine.recognizePcm(pcmBytes, length)
    }

    override fun fetchRawResult(): String? = jni?.fetchResult()

    override fun getUtteranceBoundsMs(): Pair<Int, Int> {
        val engine = jni ?: return 0 to 0
        val startMs = engine.getIntOption(JniAsrFix.OPT_UTTERANCE_START_MS).coerceAtLeast(0)
        val endMs = engine.getIntOption(JniAsrFix.OPT_UTTERANCE_END_MS).coerceAtLeast(startMs)
        return startMs to endMs
    }

    override fun stopSession() {
        val engine = jni ?: return
        if (initialized) {
            runCatching { engine.stopRecognition() }
        }
    }

    override fun cancelSession() {
        val engine = jni ?: return
        if (initialized) {
            runCatching { engine.cancelRecognition() }
        }
    }

    override fun release() {
        val engine = jni ?: return
        if (initialized) {
            runCatching { engine.cancelRecognition() }
            runCatching { engine.releaseEngine() }
            initialized = false
            Log.i(TAG, "JniAsrFix released")
        }
        jni = null
    }

    override fun getVersion(): String? = runCatching { JniAsrFix.getVersion() }.getOrNull()

    companion object {
        private const val TAG = "R1WakeWord"
        private const val WAKEUP_GRAMMAR_TAG = "wakeup"
        private const val DEFAULT_APP_KEY = "phicomm_r1_local"
    }
}
