package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scenario-based tests that validate the analyzer's behavior against realistic
 * vocal sessions.
 *
 * Each test name reads as a product behavior statement, not an implementation
 * detail.  The fixture sessions live in [Fixtures]; the [SessionReplay] helper
 * applies the same acceptance rules as [VoiceAnalyzer] so the tests exercise
 * exactly the logic the production app uses.
 *
 * Test map:
 *  1.  ascending_warmup_detects_correct_floor_and_ceiling
 *  2.  descending_warmup_produces_same_extremes_as_ascending
 *  3.  high_spike_changes_extreme_only_when_it_has_a_neighbor
 *  4.  low_spike_changes_extreme_only_when_it_has_a_neighbor
 *  5.  noisy_glide_passaggio_estimate_lands_near_unstable_center
 *  6.  stable_mid_range_produces_narrow_comfortable_band
 *  7.  session_with_too_few_voiced_samples_fails_safely
 *  8.  long_silence_gaps_do_not_contaminate_pitch_data
 *  9.  repeatedly_confirmed_boundary_notes_survive_neighbor_validation
 * 10.  frames_that_lose_confidence_near_extremes_are_excluded
 * 11.  stable_repeated_top_note_expands_comfortable_high
 */
class AnalyzerScenarioTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 1 — Ascending warm-up
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `ascending warmup detects correct floor and ceiling`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.ASCENDING_WARMUP)
        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        // Floor should be at or very close to G3 (196 Hz) — start note
        assertTrue(
            "Detected min ($detectedMin Hz) should be near G3 (196 Hz)",
            detectedMin <= 200f
        )
        // Ceiling should be at or close to G4 (392 Hz) — top note
        assertTrue(
            "Detected max ($detectedMax Hz) should be near G4 (392 Hz)",
            detectedMax >= 390f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 2 — Descending produces the same result as ascending
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `descending warmup produces same extremes as ascending warmup`() {
        val pitchesUp   = SessionReplay.acceptedPitches(Fixtures.ASCENDING_WARMUP)
        val pitchesDown = SessionReplay.acceptedPitches(Fixtures.DESCENDING_WARMUP)

        val (minUp,   maxUp)   = FachClassifier.estimateDetectedExtremes(pitchesUp)
        val (minDown, maxDown) = FachClassifier.estimateDetectedExtremes(pitchesDown)

        assertEquals("Detected min should be direction-independent", minUp, minDown, 5f)
        assertEquals("Detected max should be direction-independent", maxUp, maxDown, 5f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 3 — Brief high spike
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `high spike changes detected extreme only when it has a neighbor`() {
        // The fixture has ONE isolated A5 (880 Hz) frame with no neighbor.
        // Neighbor-validation should reject it; detected max should stay near C5.
        val pitches = SessionReplay.acceptedPitches(Fixtures.HIGH_SPIKE_OVER_STABLE_CORE)
        val (_, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        assertTrue(
            "Detected max ($detectedMax Hz) should be near C5 (523 Hz), not A5 (880 Hz) — spike has no neighbor",
            detectedMax <= 600f
        )
    }

    @Test
    fun `high spike does not widen comfortable range`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.HIGH_SPIKE_OVER_STABLE_CORE)
        val (_, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        // P80 of [20×E4, 20×C5, 1×A5] is solidly in the C5 cluster
        assertTrue(
            "Comfortable high ($comfortableHigh Hz) must not be pulled to A5 spike",
            comfortableHigh < 700f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 4 — Brief low spike
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `low spike changes detected extreme only when it has a neighbor`() {
        // The fixture has ONE isolated E2 (82 Hz) frame with no neighbor.
        val pitches = SessionReplay.acceptedPitches(Fixtures.LOW_SPIKE_OVER_STABLE_CORE)
        val (detectedMin, _) = FachClassifier.estimateDetectedExtremes(pitches)

        assertTrue(
            "Detected min ($detectedMin Hz) should be near C5 (523 Hz), not E2 (82 Hz) — spike has no neighbor",
            detectedMin >= 400f
        )
    }

    @Test
    fun `low spike does not widen comfortable range`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.LOW_SPIKE_OVER_STABLE_CORE)
        val (comfortableLow, _) = FachClassifier.estimateComfortableRange(pitches)

        assertTrue(
            "Comfortable low ($comfortableLow Hz) must not be pulled toward E2 spike",
            comfortableLow >= 400f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 5 — Noisy glide through passaggio
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `noisy glide passaggio estimate lands near the unstable center`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.NOISY_GLIDE_THROUGH_PASSAGGIO)
        val passaggio = FachClassifier.estimatePassaggio(pitches)

        // The unstable region is centered on E4 (330 Hz) with ±40 Hz variance.
        // The passaggio estimate should land somewhere in [280, 380].
        assertTrue(
            "Passaggio ($passaggio Hz) should be near the glide center around E4 (330 Hz)",
            passaggio in 270f..400f
        )
    }

    @Test
    fun `stable regions flanking the passaggio glide do not dominate the passaggio estimate`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.NOISY_GLIDE_THROUGH_PASSAGGIO)
        val passaggio = FachClassifier.estimatePassaggio(pitches)

        // Stable C4 (262 Hz) or G4 (392 Hz) blocks have low variance;
        // the algorithm should prefer the noisy E4 zone.
        assertTrue(
            "Passaggio ($passaggio Hz) should not be locked to the stable C4 zone (262 Hz)",
            passaggio > 280f
        )
        assertTrue(
            "Passaggio ($passaggio Hz) should not be locked to the stable G4 zone (392 Hz)",
            passaggio < 420f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 6 — Stable mid, unstable edges
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `stable mid range singing produces a narrow comfortable band`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.STABLE_MID_UNSTABLE_EDGES)
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        // 40 out of ~52 frames are at A4 (440 Hz), so P20 and P80 should both be near A4.
        assertTrue(
            "Comfortable low ($comfortableLow Hz) should be near A4 (440 Hz)",
            comfortableLow in 400f..460f
        )
        assertTrue(
            "Comfortable high ($comfortableHigh Hz) should be near A4 (440 Hz)",
            comfortableHigh in 420f..480f
        )
    }

    @Test
    fun `unstable edges still expand detected range beyond comfortable range`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.STABLE_MID_UNSTABLE_EDGES)
        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        assertTrue("Detected min should be below comfortable low", detectedMin <= comfortableLow)
        assertTrue("Detected max should be above comfortable high", detectedMax >= comfortableHigh)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 7 — Too few valid samples
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `session with too few valid samples fails safely without crashing`() {
        // The fixture contains 10 voiced frames — below the 20-sample minimum.
        val profile = SessionReplay.buildProfile(Fixtures.TOO_FEW_VALID_SAMPLES)

        assertNull(
            "buildProfile should return null when fewer than 20 samples are accepted",
            profile
        )
    }

    @Test
    fun `accepted pitch extraction from a very short session does not crash`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.TOO_FEW_VALID_SAMPLES)
        assertEquals("Expected 10 accepted samples from the short session", 10, pitches.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 8 — Long silence gaps
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `long silence gaps do not contaminate pitch data`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.LONG_SILENCE_GAPS)

        // Silence frames are all isVoiced=false and hz=0 — all should be rejected.
        assertTrue(
            "No accepted pitch should be zero or negative",
            pitches.all { it > 0f }
        )
        assertEquals(
            "Only the 30 voiced frames should be accepted",
            30, pitches.size
        )
    }

    @Test
    fun `session with long gaps between phrases still builds a valid profile`() {
        // 30 voiced samples is >= 20, so profile should succeed.
        val profile = SessionReplay.buildProfile(Fixtures.LONG_SILENCE_GAPS)
        assertNotNull("Profile should be non-null with 30 valid voiced frames", profile)
    }

    @Test
    fun `silence gaps do not shift the detected extremes`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.LONG_SILENCE_GAPS)
        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        // Sung notes are E4 (330), A4 (440), C5 (523) — no silence should sneak in.
        assertTrue("Detected min should be near E4 (330 Hz)", detectedMin in 300f..350f)
        assertTrue("Detected max should be near C5 (523 Hz)", detectedMax in 510f..545f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 9 — Repeated boundary notes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `repeatedly confirmed boundary notes survive neighbor validation`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.REPEATED_STABLE_BOUNDARIES)
        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        // E3 (165 Hz) and B4 (494 Hz) each appear in multiple separate clusters.
        assertTrue(
            "E3 ($detectedMin Hz) should be the detected min as it appears in multiple clusters",
            detectedMin <= 170f
        )
        assertTrue(
            "B4 ($detectedMax Hz) should be the detected max as it appears in multiple clusters",
            detectedMax >= 490f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 10 — Confidence drops near extremes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `frames that lose confidence near the extremes are excluded from analysis`() {
        // The fixture fades confidence from 1.0 to 0.0 at both floor (E3) and
        // ceiling (E5).  Frames below MIN_CONFIDENCE = 0.80 are rejected.
        val pitches = SessionReplay.acceptedPitches(Fixtures.CONFIDENCE_DROPS_NEAR_EXTREMES)
        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        // High-confidence core: G3 (196 Hz) to B4 (494 Hz)
        assertTrue(
            "Detected min ($detectedMin Hz) should not include low-confidence E3 frames",
            detectedMin >= 185f  // should be near G3 (196 Hz), not E3 (165 Hz)
        )
        assertTrue(
            "Detected max ($detectedMax Hz) should not include low-confidence E5 frames",
            detectedMax <= 520f  // should be near B4 (494 Hz), not E5 (659 Hz)
        )
    }

    @Test
    fun `only high confidence frames contribute to comfortable range`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.CONFIDENCE_DROPS_NEAR_EXTREMES)
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        // Comfortable range should reflect the confident core (G3–B4), not the
        // noisy fade-out regions.
        assertTrue(
            "Comfortable low ($comfortableLow Hz) should be within the confident core",
            comfortableLow >= 185f
        )
        assertTrue(
            "Comfortable high ($comfortableHigh Hz) should be within the confident core",
            comfortableHigh <= 520f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 10b — Confidence fades from high (some extreme frames pass)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * When extreme-range frames START with high confidence and only fade to zero
     * partway through, the first few frames ARE accepted and DO have neighbors.
     * The correct conservative behavior is that those frames expand the detected
     * extremes — the analyzer should not silently discard them.
     *
     * This test documents and pins that behavior to prevent a future refactor from
     * accidentally suppressing genuinely-sung extreme notes.
     */
    @Test
    fun `extreme frames with initially high confidence are correctly included in detected range`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.CONFIDENCE_FADES_FROM_HIGH)

        // 3 accepted frames at E3 (165 Hz) form a valid neighbor cluster
        val e3Count = pitches.count { it in 160f..170f }
        assertTrue("At least 2 E3 frames should pass the confidence filter", e3Count >= 2)

        val (detectedMin, _) = FachClassifier.estimateDetectedExtremes(pitches)
        assertTrue(
            "Detected min ($detectedMin Hz) should include E3 (165 Hz) since it has neighbors",
            detectedMin <= 170f
        )
    }

    @Test
    fun `comfortable range is not pulled to extremes even when some extreme frames pass confidence filter`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.CONFIDENCE_FADES_FROM_HIGH)
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        // 3 frames at E3 out of ~45 total = ~7% — P20 is still in the core (G3+)
        assertTrue(
            "Comfortable low ($comfortableLow Hz) should stay in the G3–A4 core, " +
                "not pulled to the 3-frame E3 cluster",
            comfortableLow >= 185f
        )
        assertTrue(
            "Comfortable high ($comfortableHigh Hz) should stay in the A4–B4 core",
            comfortableHigh <= 520f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenario 11 — Stable repeated top note expands comfortable high
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `stable repeated top note expands comfortable high toward that note`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.REPEATED_STABLE_TOP_NOTE)
        val (_, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        // 30 frames at B4 (494 Hz) in a session of ~65 total frames.
        // P80 index ≈ frame 52.  Since B4 accounts for ~46% of frames,
        // the sorted list has many B4 entries pushing P80 toward B4.
        assertTrue(
            "Comfortable high ($comfortableHigh Hz) should be pulled toward B4 (494 Hz) by the repeated top note",
            comfortableHigh >= 460f
        )
    }

    @Test
    fun `repeatedly confirmed top note passes neighbor validation`() {
        val pitches = SessionReplay.acceptedPitches(Fixtures.REPEATED_STABLE_TOP_NOTE)
        val (_, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        assertTrue(
            "Detected max ($detectedMax Hz) should be near B4 (494 Hz)",
            detectedMax >= 490f
        )
    }
}
