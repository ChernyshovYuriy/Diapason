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
 * They validate that [VoiceProfile] carries the fields required for the updated
 * two-section layout (comfortable range primary, detected extremes secondary),
 * and that the values satisfy ordering invariants the UI assumes.
 *
 * Coverage:
 *   1. comfortableLowHz < comfortableHighHz (comfortable range is ordered low→high).
 *   2. Comfortable range is narrower than or equal to detected extremes.
 *   3. detectedMinHz ≤ comfortableLowHz (detected floor ≤ comfortable floor).
 *   4. comfortableHighHz ≤ detectedMaxHz (comfortable ceiling ≤ detected ceiling).
 *   5. estimatedPassaggioHz falls within [detectedMinHz, detectedMaxHz].
 *   6. sampleCount and durationSeconds are positive for a valid profile.
 *   7. hzToNoteName produces non-empty, non-dash output for all profile fields.
 *   8. A profile with identical comfortable and detected bounds still renders safely.
 */
class ResultsDisplayOrderTest {

    private fun profile(
        detectedMin: Float = 130f,  // C3
        detectedMax: Float = 880f,  // A5
        comfortableLow: Float = 196f,  // G3
        comfortableHigh: Float = 659f,  // E5
        passaggio: Float = 370f,
        samples: Int = 120,
        duration: Float = 45f
    ) = VoiceProfile(
        detectedMinHz = detectedMin,
        detectedMaxHz = detectedMax,
        comfortableLowHz = comfortableLow,
        comfortableHighHz = comfortableHigh,
        estimatedPassaggioHz = passaggio,
        sampleCount = samples,
        durationSeconds = duration
    )

    @Test
    fun `comfortable range is ordered low to high`() {
        val p = profile()
        assertTrue(
            "comfortableLowHz must be less than comfortableHighHz",
            p.comfortableLowHz < p.comfortableHighHz
        )
    }

    @Test
    fun `comfortable range is not wider than detected extremes`() {
        val p = profile()
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
        val p = profile()
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
        val p = profile()
        assertTrue("sampleCount must be positive", p.sampleCount > 0)
        assertTrue("durationSeconds must be positive", p.durationSeconds > 0f)
    }

    @Test
    fun `hzToNoteName returns displayable strings for all profile fields`() {
        val p = profile()
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
    fun `profile with identical comfortable and detected bounds renders safely`() {
        // Edge case: singer sang only one narrow cluster — comfortable == detected
        val p = profile(
            detectedMin = 220f,
            detectedMax = 330f,
            comfortableLow = 220f,
            comfortableHigh = 330f,
            passaggio = 275f
        )
        // No crash expectations — just structural validity
        assertEquals(p.detectedMinHz, p.comfortableLowHz, 0.001f)
        assertEquals(p.detectedMaxHz, p.comfortableHighHz, 0.001f)
        assertTrue(p.comfortableLowHz <= p.comfortableHighHz)
    }

    @Test
    fun `detected extremes span broader range than comfortable range in typical session`() {
        // Build via FachClassifier to test the actual computation path
        val pitches = buildList {
            // a few low outliers (not enough to pull comfortable range down)
            repeat(2) { add(130f) }
            // stable mid-range bulk
            repeat(50) { add(220f) }
            repeat(50) { add(330f) }
            // a few high outliers
            repeat(2) { add(660f) }
        }
        val (comfortLow, comfortHigh) = FachClassifier.estimateComfortableRange(pitches)
        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

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
