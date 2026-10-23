package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** [RangeTest] — step timing, and each measurement taken from the exercise made for it. */
class RangeTestTest {

    private fun hz(midi: Float) = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
    private fun midi(hz: Float) = (12 * ln(hz / 440.0) / ln(2.0) + 69).toFloat()

    /** ~3 accepted samples a second, as in production. */
    private val samplesPerSecond = 3

    private class Take {
        val pitches = mutableListOf<Float>()
        val timesMs = mutableListOf<Long>()
        fun add(seconds: Float, hz: Float) {
            pitches += hz
            timesMs += (seconds * 1000).toLong()
        }
    }

    /** A3 held, a leaping song between A3 and E4, a slide up to E5 wobbling at the A4 break, a slide down to A2. */
    private fun fullTest(): Take {
        val take = Take()
        val note = RangeTest.Step.ComfortableNote
        val song = RangeTest.Step.Song
        val up = RangeTest.Step.SlideUp
        val down = RangeTest.Step.SlideDown
        fun times(step: RangeTest.Step) = (0 until step.seconds * samplesPerSecond).map {
            it to RangeTest.stepStartSeconds(step) + it.toFloat() / samplesPerSecond
        }
        times(note).forEach { (_, t) -> take.add(t, hz(57f)) }
        // Leaps of a third or fourth, as real melodies have — the up-and-down a whole-session
        // passaggio estimate mistakes for a break.
        val melody = listOf(57f, 61f, 57f, 62f, 59f, 64f, 60f, 64f)
        times(song).forEach { (i, t) -> take.add(t, hz(melody[i % melody.size])) }
        val upCount = up.seconds * samplesPerSecond
        times(up).forEach { (i, t) ->
            val base = 57f + 19f * i / (upCount - 1) // A3 → E5
            // Around the break (A4) the voice flips back and forth.
            val m = if (abs(base - 69f) < 2f) 69f + if (i % 2 == 0) 1.5f else -1.5f else base
            take.add(t, hz(m))
        }
        val downCount = down.seconds * samplesPerSecond
        times(down).forEach { (i, t) -> take.add(t, hz(76f - 31f * i / (downCount - 1))) } // E5 → A2
        return take
    }

    @Test
    fun `steps run back to back and the test lasts their total`() {
        assertEquals(RangeTest.STEPS.sumOf { it.seconds }, RangeTest.TOTAL_SECONDS)
        assertEquals(RangeTest.Step.ComfortableNote, RangeTest.stepAt(0f))
        assertEquals(RangeTest.Step.Song, RangeTest.stepAt(RangeTest.Step.ComfortableNote.seconds.toFloat()))
        assertEquals(RangeTest.Step.SlideDown, RangeTest.stepAt(RangeTest.TOTAL_SECONDS - 0.1f))
        assertEquals(RangeTest.Step.SlideDown, RangeTest.stepAt(RangeTest.TOTAL_SECONDS + 5f))
        assertEquals(RangeTest.Step.ComfortableNote.seconds, RangeTest.secondsLeftInStep(0f))
        assertEquals(0, RangeTest.secondsLeftInStep(RangeTest.TOTAL_SECONDS + 5f))
    }

    @Test
    fun `extremes come from the slides`() {
        val take = fullTest()
        val profile = RangeTest.profileOf(take.pitches, take.timesMs, RangeTest.TOTAL_SECONDS.toFloat())
        assertEquals(45f, midi(profile.detectedMinHz), 1f)
        assertEquals(76f, midi(profile.detectedMaxHz), 1f)
    }

    @Test
    fun `the comfortable range comes from the note and the song, not the slides`() {
        val take = fullTest()
        val profile = RangeTest.profileOf(take.pitches, take.timesMs, RangeTest.TOTAL_SECONDS.toFloat())
        assertTrue(midi(profile.comfortableLowHz) >= 56.5f)
        assertTrue(midi(profile.comfortableHighHz) <= 64.5f)
        // Taken from everything, the slides would stretch it.
        val (allLow, allHigh) = FachClassifier.estimateComfortableRange(take.pitches)
        assertTrue(midi(allHigh) - midi(allLow) > midi(profile.comfortableHighHz) - midi(profile.comfortableLowHz))
    }

    @Test
    fun `the passaggio comes from the slides, where the voice breaks`() {
        val take = fullTest()
        val profile = RangeTest.profileOf(take.pitches, take.timesMs, RangeTest.TOTAL_SECONDS.toFloat())
        assertEquals(69f, midi(profile.estimatedPassaggioHz), 2f)
        // From everything, the song's leaps would win.
        assertTrue(abs(midi(FachClassifier.estimatePassaggio(take.pitches)) - 69f) > 2f)
    }

    @Test
    fun `a test stopped during the song falls back to all samples for the passaggio`() {
        val full = fullTest()
        val cutoffMs = RangeTest.stepStartSeconds(RangeTest.Step.SlideUp) * 1000L
        val kept = full.timesMs.indices.filter { full.timesMs[it] < cutoffMs }
        val pitches = kept.map { full.pitches[it] }
        val profile = RangeTest.profileOf(pitches, kept.map { full.timesMs[it] }, 20f)
        assertEquals(FachClassifier.estimatePassaggio(pitches), profile.estimatedPassaggioHz, 0.01f)
        assertEquals(pitches.size, profile.sampleCount)
    }

    @Test
    fun `too little from the note and the song falls back to all samples for the comfortable range`() {
        val full = fullTest()
        // Silent through the first two steps: only the slides were heard.
        val kept = full.timesMs.indices.filter {
            full.timesMs[it] >= RangeTest.stepStartSeconds(RangeTest.Step.SlideUp) * 1000L
        }
        val pitches = kept.map { full.pitches[it] }
        val profile = RangeTest.profileOf(pitches, kept.map { full.timesMs[it] }, RangeTest.TOTAL_SECONDS.toFloat())
        val expected = FachClassifier.estimateComfortableRange(pitches)
        assertEquals(expected.first, profile.comfortableLowHz, 0.01f)
        assertEquals(expected.second, profile.comfortableHighHz, 0.01f)
    }
}
