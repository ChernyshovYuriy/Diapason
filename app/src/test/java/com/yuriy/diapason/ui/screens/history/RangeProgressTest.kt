package com.yuriy.diapason.ui.screens.history

import com.yuriy.diapason.data.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [buildRangeProgress]: chart data for "Your range over time". */
class RangeProgressTest {

    private fun session(
        id: String, ts: Long,
        min: Float = 98f, max: Float = 392f, low: Float = 131f, high: Float = 330f, pass: Float = 294f,
    ) = SessionRecord(
        id = id, timestampMs = ts, durationSeconds = 30f,
        detectedMinHz = min, detectedMaxHz = max, comfortableLowHz = low, comfortableHighHz = high,
        passaggioHz = pass, sampleCount = 80,
        topFachKey = null, topFachScore = null, topFachMaxScore = null, isPartial = false,
    )

    @Test
    fun `one session is not a trend`() {
        assertNull(buildRangeProgress(emptyList()))
        assertNull(buildRangeProgress(listOf(session("a", 1))))
        assertNotNull(buildRangeProgress(listOf(session("a", 1), session("b", 2))))
    }

    @Test
    fun `points run oldest to newest whatever the input order`() {
        val p = buildRangeProgress(listOf(session("c", 30), session("a", 10), session("b", 20)))!!
        assertEquals(listOf(10L, 20L, 30L), p.points.map { it.timestampMs })
    }

    @Test
    fun `only the newest twenty sessions are plotted`() {
        val p = buildRangeProgress((1L..25L).map { session("s$it", it) })!!
        assertEquals(RANGE_PROGRESS_MAX_SESSIONS, p.points.size)
        assertEquals(6L, p.points.first().timestampMs)
        assertEquals(25L, p.points.last().timestampMs)
    }

    @Test
    fun `pitches are plotted as MIDI semitones`() {
        val p = buildRangeProgress(listOf(session("a", 1, max = 440f), session("b", 2)))!!
        assertEquals(69f, p.points.first().detectedMax, 0.01f)   // A4
    }

    @Test
    fun `the axis snaps outward to Cs and contains every plotted pitch`() {
        val p = buildRangeProgress(listOf(session("a", 1), session("b", 2, min = 82f, max = 523f)))!!
        assertEquals(0, p.minMidi % 12)
        assertEquals(0, p.maxMidi % 12)
        p.points.forEach {
            assertTrue(it.detectedMin >= p.minMidi && it.detectedMax <= p.maxMidi)
            assertTrue(it.comfortableLow >= p.minMidi && it.comfortableHigh <= p.maxMidi)
        }
        assertEquals(p.minMidi, p.octaveLines.first())
        assertEquals(p.maxMidi, p.octaveLines.last())
    }

    @Test
    fun `the axis is at least one octave tall`() {
        // Every pitch the same: still a usable one-octave axis.
        val flat = session("a", 1, min = 262f, max = 262f, low = 262f, high = 262f, pass = 262f)
        val p = buildRangeProgress(listOf(flat, flat.copy(id = "b", timestampMs = 2)))!!
        assertTrue(p.maxMidi - p.minMidi >= 12)
    }
}
