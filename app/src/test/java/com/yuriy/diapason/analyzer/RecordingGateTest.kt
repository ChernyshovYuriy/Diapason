package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RecordingGate]: progress toward the 40-sample minimum and the "seconds left" estimate. */
class RecordingGateTest {

    @Test
    fun `the gate is the analyzer's own minimum`() {
        assertEquals(MIN_ACCEPTED_SAMPLES, RecordingGate.MIN_SAMPLES)
    }

    @Test
    fun `isEnough flips exactly at the minimum`() {
        assertFalse(RecordingGate.isEnough(RecordingGate.MIN_SAMPLES - 1))
        assertTrue(RecordingGate.isEnough(RecordingGate.MIN_SAMPLES))
    }

    @Test
    fun `progress is clamped to 0 to 1`() {
        assertEquals(0f, RecordingGate.progress(0), 0f)
        assertEquals(0.5f, RecordingGate.progress(20), 0.0001f)
        assertEquals(1f, RecordingGate.progress(400), 0f)
    }

    @Test
    fun `secondsRemaining uses this session's own sample rate, rounded up`() {
        // 20 samples in 10 s = 2/s; 20 more needed = 10 s.
        assertEquals(10, RecordingGate.secondsRemaining(20, 10f))
        // 30 samples in 12 s = 2.5/s; 10 more = 4 s.
        assertEquals(4, RecordingGate.secondsRemaining(30, 12f))
    }

    @Test
    fun `secondsRemaining is at least 1 while below the gate`() {
        // 39 in 13 s = 3/s; 1 more = 0.33 s, shown as 1.
        assertEquals(1, RecordingGate.secondsRemaining(39, 13f))
    }

    @Test
    fun `secondsRemaining is capped at 60`() {
        // 3 samples in 60 s = 0.05/s; 37 more would be 740 s.
        assertEquals(60, RecordingGate.secondsRemaining(3, 60f))
    }

    @Test
    fun `secondsRemaining is 0 once the gate is met`() {
        assertEquals(0, RecordingGate.secondsRemaining(RecordingGate.MIN_SAMPLES, 5f))
    }

    @Test
    fun `secondsRemaining is null when there is too little data for a rate`() {
        assertNull(RecordingGate.secondsRemaining(0, 10f))
        assertNull(RecordingGate.secondsRemaining(2, 10f))
        assertNull(RecordingGate.secondsRemaining(10, 0f))
    }
}
