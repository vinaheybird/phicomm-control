package com.phicomm.r1.control.wakeup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordResultParserTest {

    @Test
    fun acceptsWakeWordMeetingIdleThreshold() {
        val parsed = WakeWordResultParser.parse("小讯小讯\n-6.42\n", -9.1f)
        assertNotNull(parsed)
        assertEquals("小讯小讯", parsed!!.phrase)
        assertEquals(-6.42f, parsed.confidenceScore, 0.001f)
        assertTrue(parsed.accepted)
    }

    @Test
    fun rejectsWakeWordBelowIdleThresholdButAcceptsUnderPlaybackThreshold() {
        val raw = "小讯小讯\n-10.5\n"
        val idle = WakeWordResultParser.parse(raw, -9.1f)
        assertNotNull(idle)
        assertFalse(idle!!.accepted)

        val playback = WakeWordResultParser.parse(raw, -11.3f)
        assertNotNull(playback)
        assertTrue(playback!!.accepted)
    }

    @Test
    fun rejectsFallbackSilenceAndMalformedResults() {
        assertNull(WakeWordResultParser.parse(null, -9.1f))
        assertNull(WakeWordResultParser.parse("", -9.1f))
        assertNull(WakeWordResultParser.parse("\n-20.0", -9.1f))
        assertNull(WakeWordResultParser.parse("小讯小讯\nnot_a_float", -9.1f))
    }
}
