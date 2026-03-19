package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge-case and realism tests for [FachClassifier.estimatePassaggio] and
 * [FachClassifier.estimateComfortableRange].
 *
 * These test distributions that real singers produce but the scenario tests
 * do not cover:
 *
 *  P1. Uniform distribution (monotone singer, all notes at same pitch)
 *      → passaggio falls at that pitch; comfortable range collapses to a point
 *
 *  P2. Bimodal distribution (singer spends time in chest AND head register)
 *      → comfortable range P20/P80 span the gap, detected extremes capture both
 *
 *  P3. Heavy chest voice with rare head notes
 *      → passaggio estimate must fall in the chest zone where variance is highest
 *         at the transition, not at the tail head-voice notes
 *
 *  P4. Variance window does not overflow at the end of the pitch list
 *      → last window must be computed without IndexOutOfBoundsException
 *
 *  P5. All samples within one semitone (very stable singer)
 *      → comfortable range is very narrow; detected min ≈ detected max
 *
 *  P6. Comfortable range is always within detected extremes regardless of
 *      distribution shape (property verified on several non-trivial inputs)
 */
class PassaggioEdgeCaseTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun comfortable(pitches: List<Float>) =
        FachClassifier.estimateComfortableRange(pitches)

    private fun extremes(pitches: List<Float>) =
        FachClassifier.estimateDetectedExtremes(pitches)

    private fun passaggio(pitches: List<Float>) =
        FachClassifier.estimatePassaggio(pitches)

    // ── P1. Uniform distribution ──────────────────────────────────────────────

    @Test
    fun `uniform distribution produces passaggio at that pitch`() {
        val pitches = List(60) { 440f }  // A4, held throughout
        val result = passaggio(pitches)
        assertEquals(
            "Passaggio of a uniform 440 Hz distribution should be 440 Hz",
            440f, result, 1f
        )
    }

    @Test
    fun `uniform distribution produces comfortable range that collapses near the single pitch`() {
        val pitches = List(60) { 330f }  // E4
        val (low, high) = comfortable(pitches)
        // P20 and P80 of identical values must both be that value
        assertEquals("Comfortable low must equal 330 Hz for uniform input", 330f, low, 0.1f)
        assertEquals("Comfortable high must equal 330 Hz for uniform input", 330f, high, 0.1f)
    }

    @Test
    fun `uniform distribution comfortable range is within detected extremes`() {
        val pitches = List(40) { 440f }
        val (low, high) = comfortable(pitches)
        val (dMin, dMax) = extremes(pitches)
        assertTrue("Comfortable low must be >= detected min", low >= dMin)
        assertTrue("Comfortable high must be <= detected max", high <= dMax)
    }

    // ── P2. Bimodal distribution ──────────────────────────────────────────────

    /**
     * A singer who spends equal time in chest voice (E3 = 165 Hz) and head voice
     * (A4 = 440 Hz) with a clean gap between them.
     *
     * P20 falls in the E3 cluster, P80 in the A4 cluster, so the comfortable range
     * should span the gap.  Detected extremes should reach both clusters.
     */
    @Test
    fun `bimodal distribution comfortable range spans both registers`() {
        val chest = List(35) { 165f }   // E3 — chest
        val head = List(35) { 440f }   // A4 — head
        val pitches = chest + head      // 70 samples total

        val (low, high) = comfortable(pitches)
        assertTrue(
            "Comfortable low ($low Hz) must be in the chest register (near 165 Hz)",
            low <= 200f
        )
        assertTrue(
            "Comfortable high ($high Hz) must be in the head register (near 440 Hz)",
            high >= 400f
        )
    }

    @Test
    fun `bimodal distribution detected extremes reach both cluster boundaries`() {
        val chest = List(10) { 165f } + List(10) { 175f }  // E3 cluster
        val head = List(10) { 430f } + List(10) { 440f }  // near-A4 cluster
        val pitches = chest + head

        val (dMin, dMax) = extremes(pitches)
        assertTrue("Detected min must reach chest cluster", dMin <= 170f)
        assertTrue("Detected max must reach head cluster", dMax >= 430f)
    }

    @Test
    fun `bimodal comfortable range stays within detected extremes`() {
        val pitches = List(30) { 196f } + List(30) { 523f }  // G3 + C5
        val (low, high) = comfortable(pitches)
        val (dMin, dMax) = extremes(pitches)
        assertTrue("Comfortable low must be >= detected min", low >= dMin)
        assertTrue("Comfortable high must be <= detected max", high <= dMax)
    }

    /**
     * The passaggio estimate uses a sliding variance window.  For a bimodal
     * distribution with a clean gap, the highest variance window is in the
     * transition zone between the two clusters — not in either stable cluster.
     * We verify the passaggio lands somewhere between the two clusters.
     */
    @Test
    fun `bimodal distribution passaggio estimate falls between the two clusters`() {
        // 30 frames at E3 (165 Hz), then 30 frames at B4 (494 Hz), no transition frames.
        // The variance is highest exactly at the window that straddles the gap.
        val pitches = List(30) { 165f } + List(30) { 494f }
        val result = passaggio(pitches)

        // The mean of a 15-sample window that spans the boundary (half 165, half 494)
        // ≈ (7.5×165 + 7.5×494) / 15 = 329.5 Hz — roughly E4.
        // We allow a generous ±100 Hz to accommodate window-edge effects.
        assertTrue(
            "Bimodal passaggio ($result Hz) must be between the two clusters (165–494 Hz)",
            result in 165f..494f
        )
        assertTrue(
            "Bimodal passaggio ($result Hz) must not be locked to chest cluster (165 Hz)",
            result > 200f
        )
        assertTrue(
            "Bimodal passaggio ($result Hz) must not be locked to head cluster (494 Hz)",
            result < 460f
        )
    }

    // ── P3. Heavy chest voice with rare head notes ────────────────────────────

    /**
     * Singer spends 80% of session in chest voice (G3 = 196 Hz) and only
     * touches head voice briefly (B4 = 494 Hz, 5 samples).
     *
     * Comfortable range (P20/P80) should be dominated by the chest cluster.
     * The head-voice notes are a minority so P80 might or might not reach them —
     * but the comfortable range must not EXCEED the detected extremes.
     */
    @Test
    fun `heavy chest voice with brief head notes comfortable range stays within extremes`() {
        val chest = List(40) { 196f }  // G3 — dominant
        val head = List(5) { 494f }  // B4 — brief
        val pitches = chest + head

        val (low, high) = comfortable(pitches)
        val (dMin, dMax) = extremes(pitches)

        assertTrue("Comfortable low ($low) must be >= detected min ($dMin)", low >= dMin)
        assertTrue("Comfortable high ($high) must be <= detected max ($dMax)", high <= dMax)
        assertTrue(
            "Comfortable low ($low Hz) must be in the dominant chest range",
            low in 185f..220f
        )
    }

    @Test
    fun `brief head notes do not move comfortable high above chest cluster`() {
        // 50 G3 samples (dominant), 2 B4 samples (insufficient to shift P80)
        val pitches = List(50) { 196f } + List(2) { 494f }
        val (_, high) = comfortable(pitches)

        // P80 of 52 samples where 50 are at 196: sorted[41] = 196 Hz → well below 494
        assertTrue(
            "Comfortable high ($high Hz) must not be pulled to B4 (494 Hz) by only 2 samples",
            high < 300f
        )
    }

    // ── P4. Variance window boundary — no out-of-bounds ──────────────────────

    @Test
    fun `passaggio variance window does not throw at end of list`() {
        // Minimum case for the variance path: exactly 30 samples.
        // The last window starts at index 30 - 15 = 15, ends at 30.
        val pitches = List(30) { idx -> 200f + idx * 5f }
        val result = passaggio(pitches)
        assertTrue("Passaggio must be positive for a monotone ascending input", result > 0f)
        assertTrue(
            "Passaggio ($result Hz) must be within the input range (200–345 Hz)",
            result in 200f..345f
        )
    }

    @Test
    fun `passaggio window works correctly for session with exactly 31 samples`() {
        val pitches = List(31) { 440f }
        val result = passaggio(pitches)
        assertEquals("Passaggio for 31 uniform samples should be 440 Hz", 440f, result, 1f)
    }

    // ── P5. Very stable singer (all within one semitone) ──────────────────────

    @Test
    fun `very stable singer detected range is very narrow`() {
        // All samples within a 25 Hz band around 440 Hz (~1 semitone)
        val pitches = List(60) { idx -> 430f + (idx % 5) * 5f }  // 430–450 Hz
        val (dMin, dMax) = extremes(pitches)

        assertTrue(
            "Detected range spread must be less than 30 Hz for stable singer",
            dMax - dMin < 30f
        )
        assertTrue("Detected min must be near 430 Hz", dMin in 425f..440f)
        assertTrue("Detected max must be near 450 Hz", dMax in 445f..460f)
    }

    @Test
    fun `very stable singer comfortable range is within detected extremes`() {
        val pitches = List(60) { idx -> 440f + (idx % 3) }  // 440, 441, 442
        val (low, high) = comfortable(pitches)
        val (dMin, dMax) = extremes(pitches)
        assertTrue("Comfortable low >= detected min", low >= dMin)
        assertTrue("Comfortable high <= detected max", high <= dMax)
    }

    // ── P6. Comfortable range ⊆ detected extremes (multiple distribution shapes)

    /**
     * Property: for any realistic pitch list the comfortable range must never
     * exceed the detected extremes.  Tested on five non-trivial shapes.
     */
    @Test
    fun `comfortable range is within detected extremes for multiple non-trivial distributions`() {
        data class Case(val label: String, val pitches: List<Float>)

        val cases = listOf(
            Case(
                "uniform",
                List(40) { 440f }
            ),
            Case(
                "ascending scale",
                List(40) { idx -> 196f + idx * 8f }  // G3 climbing
            ),
            Case(
                "bimodal chest+head",
                List(25) { 220f } + List(25) { 523f }
            ),
            Case(
                "skewed with outlier cluster",
                List(35) { 330f } + List(5) { 165f } + List(5) { 659f }
            ),
            Case(
                "descending then ascending (V-shape)",
                (0 until 20).map { 523f - it * 15f } + (0 until 20).map { 200f + it * 15f }
            ),
        )

        for ((label, pitches) in cases) {
            val (low, high) = comfortable(pitches)
            val (dMin, dMax) = extremes(pitches)

            assertTrue(
                "[$label] Comfortable low ($low) must be >= detected min ($dMin)",
                low >= dMin
            )
            assertTrue(
                "[$label] Comfortable high ($high) must be <= detected max ($dMax)",
                high <= dMax
            )
            assertTrue(
                "[$label] Comfortable low ($low) must be <= comfortable high ($high)",
                low <= high
            )
        }
    }
}
