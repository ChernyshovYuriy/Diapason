package com.yuriy.diapason.ui

import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.VoiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests verifying the data invariants that the results screen relies on.
 *
 * These tests do NOT spin up any Android framework, Compose, or instrumentation.
 * They validate ordering invariants the UI assumes hold on any [VoiceProfile]
 * produced by [FachClassifier].
 *
 * Coverage:
 *   1. comfortableLowHz < comfortableHighHz (comfortable range is ordered low→high).
 *   2. Comfortable range is narrower than or equal to detected extremes.
 *   3. estimatedPassaggioHz falls within [detectedMinHz, detectedMaxHz].
 *   4. sampleCount and durationSeconds are positive for a valid profile.
 *   5. hzToNoteName produces non-empty, non-dash output for all profile fields.
 *   6. A session with a single sustained pitch produces comfortable == detected bounds.
 *   7. Detected extremes span broader range than comfortable range in a typical session.
 */
class ResultsDisplayOrderTest {

    // A realistic pitch list: two low outliers, a mid-range bulk, two high outliers.
    // The outliers ensure detected extremes are wider than comfortable range.
    private val typicalPitches: List<Float> = buildList {
        repeat(2) { add(130f) }   // low outliers (C3)
        repeat(50) { add(220f) }  // stable mid bulk (A3)
        repeat(50) { add(330f) }  // stable mid bulk (E4)
        repeat(2) { add(660f) }   // high outliers (E5)
    }

    private fun profileFromPitches(pitches: List<Float>): VoiceProfile {
        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)
        val passaggio = FachClassifier.estimatePassaggio(pitches)
        return VoiceProfile(
            detectedMinHz = detectedMin,
            detectedMaxHz = detectedMax,
            comfortableLowHz = comfortableLow,
            comfortableHighHz = comfortableHigh,
            estimatedPassaggioHz = passaggio,
            sampleCount = pitches.size,
            durationSeconds = pitches.size * 0.16f,
        )
    }

    @Test
    fun `comfortable range is ordered low to high`() {
        val p = profileFromPitches(typicalPitches)
        assertTrue(
            "comfortableLowHz must be less than comfortableHighHz",
            p.comfortableLowHz < p.comfortableHighHz
        )
    }

    @Test
    fun `comfortable range is not wider than detected extremes`() {
        val p = profileFromPitches(typicalPitches)
        assertTrue(
            "comfortableLowHz must be >= detectedMinHz",
            p.comfortableLowHz >= p.detectedMinHz
        )
        assertTrue(
            "comfortableHighHz must be <= detectedMaxHz",
            p.comfortableHighHz <= p.detectedMaxHz
        )
    }

    @Test
    fun `passaggio falls within detected extremes`() {
        val p = profileFromPitches(typicalPitches)
        assertTrue(
            "estimatedPassaggioHz must be >= detectedMinHz",
            p.estimatedPassaggioHz >= p.detectedMinHz
        )
        assertTrue(
            "estimatedPassaggioHz must be <= detectedMaxHz",
            p.estimatedPassaggioHz <= p.detectedMaxHz
        )
    }

    @Test
    fun `session metadata is positive`() {
        val p = profileFromPitches(typicalPitches)
        assertTrue("sampleCount must be positive", p.sampleCount > 0)
        assertTrue("durationSeconds must be positive", p.durationSeconds > 0f)
    }

    @Test
    fun `hzToNoteName returns displayable strings for all profile fields`() {
        val p = profileFromPitches(typicalPitches)
        listOf(
            p.comfortableLowHz,
            p.comfortableHighHz,
            p.detectedMinHz,
            p.detectedMaxHz,
            p.estimatedPassaggioHz
        ).forEach { hz ->
            val name = FachClassifier.hzToNoteName(hz)
            assertTrue("Note name should not be empty for $hz Hz", name.isNotEmpty())
            assertNotEquals("Note name should not be '—' for valid Hz $hz", "—", name)
        }
    }

    @Test
    fun `single sustained pitch produces comfortable range equal to detected bounds`() {
        // A singer who only held one note — comfortable range collapses to the single pitch.
        val pitches = List(30) { 220f }
        val p = profileFromPitches(pitches)
        assertTrue(p.comfortableLowHz <= p.comfortableHighHz)
        assertTrue(p.detectedMinHz <= p.detectedMaxHz)
        assertEquals(p.detectedMinHz, p.comfortableLowHz, 0.001f)
        assertEquals(p.detectedMaxHz, p.comfortableHighHz, 0.001f)
    }

    @Test
    fun `detected extremes span broader range than comfortable range in typical session`() {
        val (comfortLow, comfortHigh) = FachClassifier.estimateComfortableRange(typicalPitches)
        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(typicalPitches)

        assertTrue(
            "Detected min ($detectedMin) should be ≤ comfortable low ($comfortLow)",
            detectedMin <= comfortLow
        )
        assertTrue(
            "Detected max ($detectedMax) should be ≥ comfortable high ($comfortHigh)",
            detectedMax >= comfortHigh
        )
    }
}
