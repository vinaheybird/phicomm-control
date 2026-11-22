package cn.yunzhisheng.asrfix

/**
 * Exact JNI bridge for stock Phicomm R1 offline wake-word library `/system/lib/libasrfix.so`.
 * Package and class name MUST remain `cn.yunzhisheng.asrfix.JniAsrFix`.
 * Verified against stock `Unisound.apk` in `docs/re/wakeup-engine-contract.md`.
 */
class JniAsrFix private constructor() {

    fun initEngine(amDirPathWithSlash: String, grammarPathsCsv: String): Int =
        init(amDirPathWithSlash, grammarPathsCsv)

    fun startRecognition(grammarTag: String, modelId: Int): Int =
        start(grammarTag, modelId)

    fun recognizePcm(pcm: ByteArray, len: Int): Int =
        recognize(pcm, len)

    fun fetchResult(): String? = getResult()

    fun stopRecognition(): Int = stop()

    fun cancelRecognition(): Int = cancel()

    fun releaseEngine() = release()

    fun resetGrammar(grammarPath: String, ext: String): Int = reset(grammarPath, ext)

    fun setIntOption(id: Int, value: Int): Int = setOptionInt(id, value)

    fun getIntOption(id: Int): Int = getOptionInt(id)

    fun setStringOption(id: Int, value: String): Int = setOptionString(id, value)

    fun getStringOption(id: Int, defaultVal: String): String? = getOptionString(id, defaultVal)

    fun queryEngineIdle(): Int = isEngineIdle()

    private external fun init(amPath: String, grammarPaths: String): Int
    private external fun start(grammarTag: String, modelId: Int): Int
    private external fun recognize(pcm: ByteArray, len: Int): Int
    private external fun getResult(): String?
    private external fun stop(): Int
    private external fun cancel(): Int
    private external fun release()
    private external fun reset(grammarPath: String, ext: String): Int
    private external fun setOptionInt(id: Int, value: Int): Int
    private external fun getOptionInt(id: Int): Int
    private external fun setOptionString(id: Int, value: String): Int
    private external fun getOptionString(id: Int, defaultVal: String): String?
    private external fun isEngineIdle(): Int
    private external fun check_wav_end(): Int
    private external fun setActiveNet(netIndex: Int): Int
    private external fun isactive(pcm: ByteArray, len: Int): Int
    private external fun search(a: String, b: String): String?
    private external fun trackInfo(flag: Int)

    // Public native grammar-compiler methods required by libasrfix.so JNI_OnLoad RegisterNatives
    external fun compileDynamicUserData(handle: Long, a: String, b: String): Int
    external fun compileUserData(handle: Long, a: String, b: String, c: String): Int
    external fun destroyUserDataCompiler(handle: Long)
    external fun getTagsInfo(handle: Long): String?
    external fun grammarCompilerGetOptionString(handle: Long, id: Int): String?
    external fun grammarCompilerSetOptionInt(handle: Long, id: Int, value: Int): Int
    external fun initUserDataCompiler(configPath: String): Long
    external fun loadCompiledJsgf(handle: Long, jsgfPath: String): Int
    external fun loadGrammarStr(grammar: String): Int
    external fun partialCompileUserData(handle: Long, a: String, b: String, c: String, d: String, e: String): Int
    external fun unloadGrammar(grammarTag: String): Int

    companion object {
        const val RESULT_CONTINUE = 0
        const val RESULT_ACTIVE = 1
        const val RESULT_READY = 2
        const val RESULT_END = 3

        const val OPT_POST_INIT_FLAG = 0
        const val OPT_BEAM_TIMEOUT = 1
        const val OPT_DECODER_CONFIG = 6
        const val OPT_APP_KEY = 9
        const val OPT_MODEL_INDEX = 16
        const val OPT_WAKEUP_MODE = 22
        const val OPT_MODEL_NUM = 100
        const val OPT_UTTERANCE_START_MS = 101
        const val OPT_UTTERANCE_END_MS = 102

        init {
            System.loadLibrary("asrfix")
        }

        @Volatile
        private var instance: JniAsrFix? = null

        @JvmStatic
        fun getInstance(): JniAsrFix =
            instance ?: synchronized(this) {
                instance ?: JniAsrFix().also { instance = it }
            }

        @JvmStatic
        fun checkCrc(filePath: String): Boolean = crcCheck(filePath) == 0

        @JvmStatic
        private external fun crcCheck(filePath: String): Int

        @JvmStatic
        external fun getVersion(): String?

        @JvmStatic
        external fun compileDecodeNet(a: String, b: String): Int
    }
}
