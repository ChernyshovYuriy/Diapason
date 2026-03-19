package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariant and property-style tests for the voice analyzer.
 *
 * These tests describe rules that must hold for ALL valid inputs, not just one
 * specific scenario.  They run the same invariant check against several
 * independently constructed datasets to give them breadth without needing a
 * property-testing library.
 *
 * Invariants tested:
 *  I1.  comfortable range is never wider than detected extremes
 *  I2.  comfortable low is always <= comfortable high
 *  I3.  detected min is always <= detected max
 *  I4.  adding an outlier outside the majority range must not widen comfortable range
 *  I5.  adding more samples of an extreme note may widen comfortable range toward it
 *  I6.  removing invalid / low-confidence samples never crashes analysis
 *  I7.  low sample count fails safely and conservatively
 *  I8.  passaggio estimate is always within detected extremes for sufficient data
 *  I9.  hzToNoteName is always deterministic for the same input
 *  I10. estimateComfortableRange and estimateDetectedExtremes handle single-value lists
 *  I11. comfortable range is stable when the majority pitch distribution does not change
 *  I12. detected extremes widen monotonically as valid samples are added
 */
class AnalyzerInvariantTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: run an invariant assertion across multiple representative datasets
    // ─────────────────────────────────────────────────────────────────────────

    private val representativeDatasets: List<Pair<String, List<Float>>> = listOf(
        "ascending warmup" to SessionReplay.acceptedPitches(Fixtures.ASCENDING_WARMUP),
        "descending warmup" to SessionReplay.acceptedPitches(Fixtures.DESCENDING_WARMUP),
        "stable mid" to SessionReplay.acceptedPitches(Fixtures.STABLE_MID_UNSTABLE_EDGES),
        "long gaps" to SessionReplay.acceptedPitches(Fixtures.LONG_SILENCE_GAPS),
        "repeated boundaries" to SessionReplay.acceptedPitches(Fixtures.REPEATED_STABLE_BOUNDARIES),
        "repeated top note" to SessionReplay.acceptedPitches(Fixtures.REPEATED_STABLE_TOP_NOTE),
    )

    private fun forAllDatasets(
        invariantName: String,
        check: (label: String, pitches: List<Float>) -> Unit
    ) {
        representativeDatasets.forEach { (label, pitches) ->
            check("[$invariantName] dataset='$label'", pitches)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I1. Comfortable range never wider than detected extremes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `comfortable low is always greater than or equal to detected min`() {
        forAllDatasets("I1-low") { ctx, pitches ->
            val (detectedMin, _) = FachClassifier.estimateDetectedExtremes(pitches)
            val (comfortableLow, _) = FachClassifier.estimateComfortableRange(pitches)
            assertTrue(
                "$ctx: comfortableLow ($comfortableLow) must be >= detectedMin ($detectedMin)",
                comfortableLow >= detectedMin
            )
        }
    }

    @Test
    fun `comfortable high is always less than or equal to detected max`() {
        forAllDatasets("I1-high") { ctx, pitches ->
            val (_, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
            val (_, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)
            assertTrue(
                "$ctx: comfortableHigh ($comfortableHigh) must be <= detectedMax ($detectedMax)",
                comfortableHigh <= detectedMax
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I2. comfortable low <= comfortable high
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `comfortable low is always less than or equal to comfortable high`() {
        forAllDatasets("I2") { ctx, pitches ->
            val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)
            assertTrue(
                "$ctx: comfortableLow ($comfortableLow) must be <= comfortableHigh ($comfortableHigh)",
                comfortableLow <= comfortableHigh
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I3. detected min <= detected max
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `detected min is always less than or equal to detected max`() {
        forAllDatasets("I3") { ctx, pitches ->
            val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
            assertTrue(
                "$ctx: detectedMin ($detectedMin) must be <= detectedMax ($detectedMax)",
                detectedMin <= detectedMax
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I4. Adding an isolated outlier must not widen comfortable range
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `adding an isolated high outlier does not widen comfortable high`() {
        // Base: 50 frames solidly at A4 (440 Hz)
        val base = List(50) { 440f }
        val (_, comfortableHighBefore) = FachClassifier.estimateComfortableRange(base)

        // Append one isolated E6 (1319 Hz) spike — no neighbor
        val withOutlier = base + listOf(Fixtures.E6)
        val (_, comfortableHighAfter) = FachClassifier.estimateComfortableRange(withOutlier)

        // P80 of 51 samples where 50 are at 440 Hz and 1 is at 1319 Hz:
        // sorted[40] = 440 Hz — the outlier is at index 50, well above P80.
        assertEquals(
            "Adding an isolated high outlier must not widen comfortable high",
            comfortableHighBefore, comfortableHighAfter, 5f
        )
    }

    @Test
    fun `adding an isolated low outlier does not narrow comfortable low`() {
        // Base: 50 frames solidly at A4 (440 Hz)
        val base = List(50) { 440f }
        val (comfortableLowBefore, _) = FachClassifier.estimateComfortableRange(base)

        // Append one isolated E2 (82 Hz) spike — no neighbor
        val withOutlier = listOf(Fixtures.E2) + base
        val (comfortableLowAfter, _) = FachClassifier.estimateComfortableRange(withOutlier)

        // P20 of 51 samples where 50 are at 440 Hz and 1 is at 82 Hz:
        // sorted[10] = 440 Hz — the low outlier sits at index 0, below P20.
        assertEquals(
            "Adding an isolated low outlier must not narrow comfortable low",
            comfortableLowBefore, comfortableLowAfter, 5f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I5. Increasing stable boundary sample count may widen comfortable range
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `adding many confirmed high samples can widen comfortable high toward them`() {
        // Start with 40 frames at E4 (330 Hz) — comfortable high ≈ E4
        val base = List(40) { Fixtures.E4 }
        val (_, comfortableHighSmall) = FachClassifier.estimateComfortableRange(base)

        // Now add 40 more frames at B4 (494 Hz) — they should push P80 up
        val extended = base + List(40) { Fixtures.B4 }
        val (_, comfortableHighExtended) = FachClassifier.estimateComfortableRange(extended)

        assertTrue(
            "Comfortable high ($comfortableHighExtended Hz) should increase toward B4 (494 Hz) " +
                    "when many B4 samples are added (was $comfortableHighSmall Hz)",
            comfortableHighExtended > comfortableHighSmall
        )
    }

    @Test
    fun `adding many confirmed low samples can widen comfortable low toward them`() {
        val base = List(40) { Fixtures.A4 }
        val (comfortableLowSmall, _) = FachClassifier.estimateComfortableRange(base)

        val extended = List(40) { Fixtures.G3 } + base
        val (comfortableLowExtended, _) = FachClassifier.estimateComfortableRange(extended)

        assertTrue(
            "Comfortable low ($comfortableLowExtended Hz) should decrease toward G3 (196 Hz) " +
                    "when many G3 samples are added (was $comfortableLowSmall Hz)",
            comfortableLowExtended < comfortableLowSmall
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I6. Removing low-confidence samples never crashes analysis
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `filtering out all low confidence samples does not crash`() {
        // All samples have confidence just below the threshold — all are rejected.
        val allLowConfidence = buildSession {
            repeat(50) { sustainedNote(440f, 1, confidence = 0.79f) }
        }
        // acceptedPitches should return empty list without throwing
        val pitches = SessionReplay.acceptedPitches(allLowConfidence)
        assertTrue("All low-confidence samples should be rejected", pitches.isEmpty())

        // Downstream calls with an empty list must also not throw
        val (min, max) = FachClassifier.estimateDetectedExtremes(pitches)
        assertEquals(0f, min, 0.01f)
        assertEquals(0f, max, 0.01f)

        val (low, high) = FachClassifier.estimateComfortableRange(pitches)
        assertEquals(0f, low, 0.01f)
        assertEquals(0f, high, 0.01f)
    }

    @Test
    fun `filtering out all out of range samples does not crash`() {
        // Frequencies outside [60, 2200] Hz are rejected by SessionReplay.
        val outOfRange = buildSession {
            frame(hz = 30f, confidence = 1.0f)   // below MIN_PITCH_HZ
            frame(hz = 3000f, confidence = 1.0f) // above MAX_PITCH_HZ
        }
        val pitches = SessionReplay.acceptedPitches(outOfRange)
        assertTrue("Out-of-range frequencies should be rejected", pitches.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I7. Low sample count fails safely and conservatively
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `session with exactly 19 accepted samples returns null profile`() {
        val session = buildSession { sustainedNote(440f, 19) }
        assertNull(
            "buildProfile should return null with only 19 samples (< 20 minimum)",
            SessionReplay.buildProfile(session)
        )
    }

    @Test
    fun `session with exactly 20 accepted samples returns a valid profile`() {
        val session = buildSession { sustainedNote(440f, 20) }
        assertNotNull(
            "buildProfile should succeed with exactly 20 samples",
            SessionReplay.buildProfile(session)
        )
    }

    @Test
    fun `estimateComfortableRange with 9 samples falls back to raw min and max`() {
        // Below the 10-sample threshold, the function falls back to min/max.
        val pitches = listOf(200f, 220f, 250f, 300f, 350f, 400f, 440f, 460f, 500f) // 9 samples
        val (low, high) = FachClassifier.estimateComfortableRange(pitches)
        assertEquals("Fallback low should be raw min", 200f, low, 0.01f)
        assertEquals("Fallback high should be raw max", 500f, high, 0.01f)
    }

    @Test
    fun `estimatePassaggio with fewer than 30 samples returns mean without crashing`() {
        val pitches = listOf(220f, 330f, 440f)
        val passaggio = FachClassifier.estimatePassaggio(pitches)
        val expected = (220f + 330f + 440f) / 3f
        assertEquals("Passaggio fallback should be the arithmetic mean", expected, passaggio, 1f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I8. Passaggio is always within detected extremes for sufficient data
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `passaggio estimate is always within detected extremes`() {
        forAllDatasets("I8") { ctx, pitches ->
            if (pitches.size < 30) return@forAllDatasets  // passaggio not reliable below 30

            val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
            val passaggio = FachClassifier.estimatePassaggio(pitches)

            assertTrue(
                "$ctx: passaggio ($passaggio Hz) must be within detected range ($detectedMin–$detectedMax Hz)",
                passaggio in detectedMin..detectedMax
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I9. hzToNoteName is deterministic
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `hzToNoteName returns the same result for every call with the same input`() {
        val testFrequencies = listOf(65f, 130f, 196f, 262f, 330f, 440f, 523f, 880f, 1047f)
        testFrequencies.forEach { hz ->
            val first = FachClassifier.hzToNoteName(hz)
            val second = FachClassifier.hzToNoteName(hz)
            assertEquals("hzToNoteName($hz) must be deterministic", first, second)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I10. Single-value lists are handled without crashing
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `single sample list returns that sample as both detected min and max`() {
        val (min, max) = FachClassifier.estimateDetectedExtremes(listOf(440f))
        assertEquals("Single-sample min should equal the sample", 440f, min, 0.01f)
        assertEquals("Single-sample max should equal the sample", 440f, max, 0.01f)
    }

    @Test
    fun `single sample list for comfortable range falls back without crashing`() {
        val (low, high) = FachClassifier.estimateComfortableRange(listOf(440f))
        // Size < 10 → falls back to min/max = 440
        assertEquals(440f, low, 0.01f)
        assertEquals(440f, high, 0.01f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I11. Comfortable range is stable when majority pitch distribution is unchanged
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `appending samples outside the current comfortable range does not move comfortable range if they are few`() {
        // Core: 80 samples at A4 (440 Hz) — comfortable range is locked to A4
        val core = List(80) { 440f }
        val (coreLow, coreHigh) = FachClassifier.estimateComfortableRange(core)

        // Append 4 frames at C6 (1047 Hz) — only 4.8% of total (84 samples)
        // P80 index = 67, which is still 440 Hz
        val extended = core + List(4) { Fixtures.C6 }
        val (extLow, extHigh) = FachClassifier.estimateComfortableRange(extended)

        assertEquals(
            "Comfortable low should not shift with a 5% minority at C6",
            coreLow,
            extLow,
            5f
        )
        assertEquals(
            "Comfortable high should not shift with a 5% minority at C6",
            coreHigh,
            extHigh,
            5f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I12. Detected extremes widen monotonically as valid samples are added
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `detected max only widens or stays the same when more valid high samples are added`() {
        // Start with a 20-sample session, then add higher and higher notes with neighbors
        val base = List(20) { 440f }  // A4
        val (_, maxBase) = FachClassifier.estimateDetectedExtremes(base)

        val plusC5 = base + listOf(Fixtures.C5, Fixtures.C5) // two samples → neighbor exists
        val (_, maxC5) = FachClassifier.estimateDetectedExtremes(plusC5)

        val plusE5 = plusC5 + listOf(Fixtures.E5, Fixtures.E5)
        val (_, maxE5) = FachClassifier.estimateDetectedExtremes(plusE5)

        assertTrue(
            "Detected max should grow when C5 cluster is added ($maxBase → $maxC5)",
            maxC5 >= maxBase
        )
        assertTrue(
            "Detected max should grow when E5 cluster is added ($maxC5 → $maxE5)",
            maxE5 >= maxC5
        )
    }

    @Test
    fun `detected min only widens or stays the same when more valid low samples are added`() {
        val base = List(20) { 440f }  // A4
        val (minBase, _) = FachClassifier.estimateDetectedExtremes(base)

        val plusA3 = base + listOf(220f, 220f) // A3 cluster
        val (minA3, _) = FachClassifier.estimateDetectedExtremes(plusA3)

        val plusE3 = plusA3 + listOf(Fixtures.E3, Fixtures.E3)
        val (minE3, _) = FachClassifier.estimateDetectedExtremes(plusE3)

        assertTrue(
            "Detected min should shrink when A3 cluster is added ($minBase → $minA3)",
            minA3 <= minBase
        )
        assertTrue(
            "Detected min should shrink when E3 cluster is added ($minA3 → $minE3)",
            minE3 <= minA3
        )
    }
}
