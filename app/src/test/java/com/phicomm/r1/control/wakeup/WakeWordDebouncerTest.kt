package com.phicomm.r1.control.wakeup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordDebouncerTest {

    @Test
    fun suppressesDuplicatesWithinCooldownWindow() {
        val debouncer = WakeWordDebouncer(1500L)
        assertTrue(debouncer.tryAcquire(10_000L))
        assertFalse(debouncer.tryAcquire(10_500L))
        assertFalse(debouncer.tryAcquire(11_499L))
        assertTrue(debouncer.tryAcquire(11_500L))
    }

    @Test
    fun resetAllowsImmediateReTrigger() {
        val debouncer = WakeWordDebouncer(1500L)
        assertTrue(debouncer.tryAcquire(10_000L))
        debouncer.reset()
        assertTrue(debouncer.tryAcquire(10_100L))
    }
}
