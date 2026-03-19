package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for analyzer logic.
 *
 * These tests are pure JVM — no Android framework needed.
 *
 * Coverage goals:
 *   1. Brief noisy spikes do NOT become the comfortable range.
 *   2. Stable repeated singing DOES influence the comfortable range.
 *   3. Detected extremes remain broader than (or equal to) comfortable range.
 *   4. Neighbor validation stops a lone outlier from setting the detected extreme.
 *   5. Low sample counts behave safely (no crash, sane fallback values).
 *   6. hzToNoteName sanity checks.
 *   7. estimatePassaggio falls back gracefully on sparse data.
 */
class FachClassifierTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Repeat [value] [count] times into a list — simulates stable sustained singing. */
    private fun repeat(value: Float, count: Int): List<Float> = List(count) { value }

    /**
     * Build a realistic session: a core of stable mid-range samples, bookended by small
     * clusters at the extremes (like a singer warming down to their lowest then up to highest).
     */
    private fun typicalSession(
        lowCluster: Float,
        midLow: Float,
        midHigh: Float,
        highCluster: Float,
        clusterCount: Int = 5,
        midCount: Int = 40
    ): List<Float> {
        val mid = List(midCount) { i ->
            // alternate between midLow and midHigh to fill the comfortable zone
            if (i % 2 == 0) midLow else midHigh
        }
        return repeat(lowCluster, clusterCount) + mid + repeat(highCluster, clusterCount)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Comfortable range — noisy single-frame spikes are excluded
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A single very-high outlier sample (a stray falsetto note) surrounded by a large
     * block of mid-range singing must NOT shift comfortableHighHz to the outlier value.
     * The P80 of a 1-in-50 spike stays well below the spike.
     */
    @Test
    fun `single high spike does not become comfortable high`() {
        // 49 samples at 330 Hz (E4), 1 spike at 1047 Hz (C6)
        val pitches = repeat(330f, 49) + listOf(1047f)

        val (low, high) = FachClassifier.estimateComfortableRange(pitches)

        // P80 of 49×330 + 1×1047: index 40 (80% of 50) in sorted list = 330 Hz
        assertTrue(
            "Comfortable high ($high Hz) should be near the stable zone (~330 Hz), not near the spike (1047 Hz)",
            high < 400f
        )
        assertTrue(
            "Comfortable low ($low Hz) should be near the stable zone (~330 Hz)",
            low >= 300f && low <= 350f
        )
    }

    /**
     * A single very-low outlier (one deep chest note) in a session otherwise sung
     * in the upper register must NOT pull comfortableLowHz down to the outlier.
     */
    @Test
    fun `single low spike does not become comfortable low`() {
        // 1 sample at 82 Hz (E2 bass), 49 samples at 440 Hz (A4)
        val pitches = listOf(82f) + repeat(440f, 49)

        val (low, _) = FachClassifier.estimateComfortableRange(pitches)

        // P20 of sorted [82, 440×49]: index 10 (20% of 50) = 440 Hz
        assertTrue(
            "Comfortable low ($low Hz) should be near the stable zone (~440 Hz), not the outlier (82 Hz)",
            low > 400f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Stable repeated singing DOES influence comfortable range
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * When the singer spends equal time at two distinct pitches, both should appear
     * in the comfortable range (one near P20, one near P80).
     */
    @Test
    fun `two equally sung pitches both appear in comfortable range`() {
        // 25 samples at 220 Hz (A3), 25 samples at 440 Hz (A4)
        val pitches = repeat(220f, 25) + repeat(440f, 25)

        val (low, high) = FachClassifier.estimateComfortableRange(pitches)

        assertTrue(
            "Comfortable low ($low Hz) should be near 220 Hz",
            low in 200f..240f
        )
        assertTrue(
            "Comfortable high ($high Hz) should be near 440 Hz",
            high in 420f..460f
        )
    }

    /**
     * If the singer spends the majority of time in a middle band, the comfortable range
     * should land within that band regardless of brief extremes at either end.
     */
    @Test
    fun `comfortable range tracks the majority singing zone`() {
        // 5 low, 40 mid (262–392 Hz), 5 high
        val pitches = typicalSession(
            lowCluster = 196f,  // G3 — bass floor excursion
            midLow     = 262f,  // C4
            midHigh    = 392f,  // G4
            highCluster= 523f,  // C5 — soprano stretch
            clusterCount = 5,
            midCount     = 40
        )

        val (low, high) = FachClassifier.estimateComfortableRange(pitches)

        assertTrue("Comfortable low ($low) should be in mid zone ≥ 250 Hz", low >= 250f)
        assertTrue("Comfortable high ($high) should be in mid zone ≤ 420 Hz", high <= 420f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Detected extremes are always ≥ comfortable range
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `detected min is always less than or equal to comfortable low`() {
        val pitches = typicalSession(196f, 262f, 392f, 523f)

        val (detectedMin, _) = FachClassifier.estimateDetectedExtremes(pitches)
        val (comfortableLow, _) = FachClassifier.estimateComfortableRange(pitches)

        assertTrue(
            "Detected min ($detectedMin Hz) should be ≤ comfortable low ($comfortableLow Hz)",
            detectedMin <= comfortableLow
        )
    }

    @Test
    fun `detected max is always greater than or equal to comfortable high`() {
        val pitches = typicalSession(196f, 262f, 392f, 523f)

        val (_, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
        val (_, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        assertTrue(
            "Detected max ($detectedMax Hz) should be ≥ comfortable high ($comfortableHigh Hz)",
            detectedMax >= comfortableHigh
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Neighbor validation — lone outlier does NOT become detected extreme
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One sample at 1760 Hz (A6) has no neighbor within 2 semitones.
     * The next sample is at 523 Hz (C5). The validated max should be 523 Hz.
     */
    @Test
    fun `lone high outlier without neighbor is excluded from detected max`() {
        // bulk at 330 Hz (E4), neighbor cluster around 523 Hz (C5), lone spike at 1760 Hz (A6)
        val pitches = repeat(330f, 30) + repeat(523f, 5) + listOf(1760f)

        val (_, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        assertTrue(
            "Detected max ($detectedMax Hz) should be near 523 Hz, not the lone outlier 1760 Hz",
            detectedMax < 600f
        )
    }

    /**
     * One sample at 65 Hz (C2) with no neighbor.
     * The next lowest is at 196 Hz (G3). Validated min should be near 196 Hz.
     */
    @Test
    fun `lone low outlier without neighbor is excluded from detected min`() {
        val pitches = listOf(65f) + repeat(196f, 5) + repeat(330f, 30)

        val (detectedMin, _) = FachClassifier.estimateDetectedExtremes(pitches)

        assertTrue(
            "Detected min ($detectedMin Hz) should be near 196 Hz, not the lone outlier 65 Hz",
            detectedMin > 150f
        )
    }

    /**
     * Two consecutive samples near the ceiling DO qualify as the detected max because
     * they neighbor each other within 2 semitones.
     */
    @Test
    fun `two adjacent high samples qualify as detected max`() {
        // Two samples at 880 Hz (A5) and 932 Hz (~A#5): ratio = 932/880 ≈ 1.059 < 1.1225
        val pitches = repeat(330f, 30) + listOf(880f, 932f)

        val (_, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        assertTrue(
            "Detected max ($detectedMax Hz) should be near 932 Hz since it has a neighbor",
            detectedMax > 900f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Low sample counts — safe fallback behaviour
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `estimateDetectedExtremes with 1 sample returns that sample for both min and max`() {
        val pitches = listOf(440f)
        val (min, max) = FachClassifier.estimateDetectedExtremes(pitches)
        assertEquals(440f, min, 0.01f)
        assertEquals(440f, max, 0.01f)
    }

    @Test
    fun `estimateComfortableRange with fewer than 10 samples falls back to raw min max`() {
        val pitches = listOf(200f, 300f, 400f, 500f)
        val (low, high) = FachClassifier.estimateComfortableRange(pitches)
        assertEquals(200f, low, 0.01f)
        assertEquals(500f, high, 0.01f)
    }

    @Test
    fun `estimateDetectedExtremes with empty list returns 0 for both`() {
        val (min, max) = FachClassifier.estimateDetectedExtremes(emptyList())
        assertEquals(0f, min, 0.01f)
        assertEquals(0f, max, 0.01f)
    }

    @Test
    fun `estimateComfortableRange with empty list returns 0 for both`() {
        val (low, high) = FachClassifier.estimateComfortableRange(emptyList())
        assertEquals(0f, low, 0.01f)
        assertEquals(0f, high, 0.01f)
    }

    @Test
    fun `estimatePassaggio with fewer than 30 samples returns mean without crash`() {
        val pitches = listOf(220f, 330f, 440f)
        val passaggio = FachClassifier.estimatePassaggio(pitches)
        val expected = (220f + 330f + 440f) / 3f
        assertEquals(expected, passaggio, 1f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. hzToNoteName sanity checks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `A4 at 440 Hz maps to A4`() {
        assertEquals("A4", FachClassifier.hzToNoteName(440f))
    }

    @Test
    fun `C4 middle C at 261_63 Hz maps to C4`() {
        assertEquals("C4", FachClassifier.hzToNoteName(261.63f))
    }

    @Test
    fun `C2 at 65_41 Hz maps to C2`() {
        assertEquals("C2", FachClassifier.hzToNoteName(65.41f))
    }

    @Test
    fun `zero or negative Hz returns dash`() {
        assertEquals("—", FachClassifier.hzToNoteName(0f))
        assertEquals("—", FachClassifier.hzToNoteName(-1f))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Detected extremes stay stable with varied but realistic data
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A session that includes a genuine low cluster and high cluster: both should
     * survive neighbor validation because each has a neighbouring sample.
     */
    @Test
    fun `genuine low and high clusters survive neighbor validation`() {
        // Soprano exercise: C4 (261 Hz) cluster at the bottom, B5 (988 Hz) at the top
        val pitches = repeat(261f, 3) + repeat(277f, 3) +   // C4–C#4 cluster
                repeat(523f, 20) +                           // C5 mid zone
                repeat(932f, 3) + repeat(988f, 3)           // A#5–B5 cluster

        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        assertTrue("Detected min should capture the C4 cluster", detectedMin < 280f)
        assertTrue("Detected max should capture the B5 cluster", detectedMax > 900f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. hzToNoteName — out-of-MIDI-range fallback
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Frequencies that map to MIDI values outside [0, 127] (i.e. below C-1 or
     * above G9) are outside the standard piano range.  The function falls back
     * to returning the Hz value formatted as a plain string ("X Hz") rather
     * than a note name.  This branch must not crash and must produce a
     * human-readable, non-empty result.
     */
    @Test
    fun `very high Hz above MIDI 127 returns Hz format string not a note name`() {
        // MIDI 127 = G9 ≈ 12544 Hz.  Anything above this triggers the fallback.
        val veryHigh = 15000f
        val result = FachClassifier.hzToNoteName(veryHigh)

        assertTrue(
            "hzToNoteName($veryHigh) should contain the Hz value, got: '$result'",
            result.contains("15000")
        )
        // Must not look like a standard note name (letter + octave number)
        assertTrue(
            "hzToNoteName($veryHigh) should not be a standard note name, got: '$result'",
            result.contains("Hz")
        )
    }

    @Test
    fun `very low Hz below MIDI 0 returns Hz format string not a note name`() {
        // C-1 ≈ 8.18 Hz (MIDI 0).  Below this the MIDI calculation goes negative.
        val tooLow = 5f
        val result = FachClassifier.hzToNoteName(tooLow)

        assertTrue(
            "hzToNoteName($tooLow) should contain the Hz value, got: '$result'",
            result.contains("5")
        )
        assertTrue(
            "hzToNoteName($tooLow) should not be a standard note name, got: '$result'",
            result.contains("Hz")
        )
    }

    @Test
    fun `hzToNoteName fallback result is non-empty for extreme values`() {
        listOf(1f, 3f, 20000f, 50000f).forEach { hz ->
            val result = FachClassifier.hzToNoteName(hz)
            assertTrue("hzToNoteName($hz) must return a non-empty string", result.isNotEmpty())
            assertTrue("hzToNoteName($hz) must not return the dash sentinel", result != "—")
        }
    }
}
