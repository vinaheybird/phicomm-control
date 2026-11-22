package com.phicomm.r1.control.wakeup

/**
 * Pure-Kotlin debounce filter that suppresses duplicate wake-word detections
 * occurring within [cooldownMs] of the last accepted trigger.
 */
class WakeWordDebouncer(
    private val cooldownMs: Long
) {
    private var lastAcceptedMs: Long = Long.MIN_VALUE

    init {
        require(cooldownMs >= 0L) { "cooldownMs must be >= 0" }
    }

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        if (lastAcceptedMs != Long.MIN_VALUE && (nowMs - lastAcceptedMs) < cooldownMs) {
            return false
        }
        lastAcceptedMs = nowMs
        return true
    }

    @Synchronized
    fun reset() {
        lastAcceptedMs = Long.MIN_VALUE
    }
}
