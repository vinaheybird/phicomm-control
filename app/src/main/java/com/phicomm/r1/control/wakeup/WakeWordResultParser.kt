package com.phicomm.r1.control.wakeup

data class ParsedWakeWordResult(
    val phrase: String,
    val confidenceScore: Float,
    val accepted: Boolean,
    val appliedThreshold: Float
)

/**
 * Pure-Kotlin parser matching stock `com.unisound.sdk.l.a(float, String)` semantics:
 * `JniAsrFix.getResult()` returns newline-delimited lines where the last non-empty
 * line carries the float confidence score and preceding text carries the matched phrase.
 */
object WakeWordResultParser {

    fun parse(rawResult: String?, threshold: Float): ParsedWakeWordResult? {
        if (rawResult.isNullOrBlank()) return null
        val lines = rawResult
            .replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.size < 2) return null

        val score = lines.last().toFloatOrNull() ?: return null
        if (!score.isFinite()) return null

        val phrase = lines.dropLast(1).joinToString(" ").trim()
        if (phrase.isEmpty()) return null

        return ParsedWakeWordResult(
            phrase = phrase,
            confidenceScore = score,
            accepted = score >= threshold,
            appliedThreshold = threshold
        )
    }
}
