package com.yuriy.diapason.analyzer

import com.yuriy.diapason.analyzer.FixtureRegressionTest.Companion.fixtureNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Regression test suite driven by JSON fixture files.
 *
 * Each fixture in `src/test/resources/fixtures/` bundles a pitch/confidence trace
 * (stored as Hz + confidence pairs — never raw audio) together with behavioral
 * assertions expressed in musical note names with semitone tolerances.
 *
 * The tests replay each trace through the same acceptance filter that
 * [VoiceAnalyzer] uses in production, then invoke the acoustic-analysis
 * functions and assert within the fixture's declared tolerances.
 *
 * ── Test matrix ────────────────────────────────────────────────────────────
 *   5 fixtures × 8 tests = 40 parameterized test cases.
 *
 *   T1  fixture_loads_and_has_enough_accepted_frames
 *   T2  fixture_all_accepted_pitches_are_in_valid_range
 *   T3  fixture_detected_min_matches_expected_note
 *   T4  fixture_detected_max_matches_expected_note
 *   T5  fixture_comfortable_low_matches_expected_note
 *   T6  fixture_comfortable_high_matches_expected_note
 *   T7  fixture_passaggio_matches_expected_note
 *   T8  fixture_invariant_comfortable_range_is_within_detected_extremes
 *   T9  fixture_invariant_detected_min_le_detected_max
 *   T10 fixture_builds_a_valid_VoiceProfile_with_consistent_fields
 *
 * ── Adding a new fixture ────────────────────────────────────────────────────
 *   1. Create `src/test/resources/fixtures/<id>.json`  (see CAPTURING.md).
 *   2. Add the filename stem to [fixtureNames] below — no other changes needed.
 *
 * ── No Android dependencies ────────────────────────────────────────────────
 *   Pure JVM tests.  No Robolectric, no device, no Context needed.
 */
@RunWith(Parameterized::class)
class FixtureRegressionTest(private val fixtureName: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtureNames(): List<String> = listOf(
            "lyric_tenor_warmup",
            "lyric_soprano_scale",
            "dramatic_mezzo_full_range",
            "bass_baritone_exercise",
            "mezzo_passaggio_exercise"
        )
    }

    // ── T1: Fixture loads and produces enough accepted frames ─────────────────

    /**
     * Basic smoke test: the fixture file exists, parses without error, and
     * enough frames survive the confidence + range filter to constitute a
     * meaningful session (≥ the fixture's own [FixtureAssertions.minAcceptedFrames]).
     *
     * A failure here indicates either a broken JSON file or a change to the
     * acceptance thresholds in [SessionReplay] that is rejecting formerly-valid frames.
     */
    @Test
    fun `fixture loads and has enough accepted frames`() {
        val fixture = FixtureLoader.load(fixtureName)
        val pitches = SessionReplay.acceptedPitches(fixture.toPitchSamples())

        assertTrue(
            "[$fixtureName] expected ≥ ${fixture.assertions.minAcceptedFrames} accepted frames " +
                    "but only ${pitches.size} survived the filter. " +
                    "Either the fixture has too few high-confidence samples or MIN_CONFIDENCE changed.",
            pitches.size >= fixture.assertions.minAcceptedFrames
        )
    }

    // ── T2: All accepted pitches are inside the valid range ──────────────────

    /**
     * Every pitch that survives [SessionReplay.acceptedPitches] must lie within
     * [SessionReplay.MIN_PITCH_HZ]..[SessionReplay.MAX_PITCH_HZ].
     *
     * This is an invariant of the acceptance filter itself.  A failure here means
     * the filter's bounds check was removed or weakened.
     */
    @Test
    fun `fixture all accepted pitches are in valid range`() {
        val fixture = FixtureLoader.load(fixtureName)
        val pitches = SessionReplay.acceptedPitches(fixture.toPitchSamples())

        pitches.forEachIndexed { i, hz ->
            assertTrue(
                "[$fixtureName] accepted pitch at index $i ($hz Hz) is outside " +
                        "[${SessionReplay.MIN_PITCH_HZ}, ${SessionReplay.MAX_PITCH_HZ}]",
                hz in SessionReplay.MIN_PITCH_HZ..SessionReplay.MAX_PITCH_HZ
            )
        }
    }

    // ── T3: Detected floor matches expected note ──────────────────────────────

    /**
     * The neighbor-validated detected minimum should match the fixture's declared
     * floor note within [FixtureAssertions.semitoneTol] semitones.
     *
     * A failure here typically means:
     *  - The neighbor-validation semitone window changed (2-semitone rule in
     *    [FachClassifier.estimateDetectedExtremes]).
     *  - An outlier rejection that should occur no longer occurs (regression).
     *  - A genuine extreme note that should survive is now being dropped.
     */
    @Test
    fun `fixture detected min matches expected note`() {
        val fixture = FixtureLoader.load(fixtureName)
        val expected = fixture.assertions.detectedMinNote ?: return
        val pitches = SessionReplay.acceptedPitches(fixture.toPitchSamples())

        val (detectedMin, _) = FachClassifier.estimateDetectedExtremes(pitches)

        FixtureAssertHelper.assertWithinSemitones(
            label = "[$fixtureName] detectedMin",
            actualHz = detectedMin,
            expectedNote = expected,
            toleranceSemitones = fixture.assertions.semitoneTol
        )
    }

    // ── T4: Detected ceiling matches expected note ────────────────────────────

    @Test
    fun `fixture detected max matches expected note`() {
        val fixture = FixtureLoader.load(fixtureName)
        val expected = fixture.assertions.detectedMaxNote ?: return
        val pitches = SessionReplay.acceptedPitches(fixture.toPitchSamples())

        val (_, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        FixtureAssertHelper.assertWithinSemitones(
            label = "[$fixtureName] detectedMax",
            actualHz = detectedMax,
            expectedNote = expected,
            toleranceSemitones = fixture.assertions.semitoneTol
        )
    }

    // ── T5: Comfortable-range low matches expected note ───────────────────────

    /**
     * The P20 of accepted pitches should land within [FixtureAssertions.semitoneTol]
     * semitones of the declared comfortable-low note.
     *
     * A failure here means the percentile calculation changed, or the fixture's
     * distribution of pitches is different from what the assertion expected —
     * which would indicate a regression in how samples are accepted or ordered.
     */
    @Test
    fun `fixture comfortable low matches expected note`() {
        val fixture = FixtureLoader.load(fixtureName)
        val expected = fixture.assertions.comfortableLowNote ?: return
        val pitches = SessionReplay.acceptedPitches(fixture.toPitchSamples())

        val (comfortableLow, _) = FachClassifier.estimateComfortableRange(pitches)

        FixtureAssertHelper.assertWithinSemitones(
            label = "[$fixtureName] comfortableLow",
            actualHz = comfortableLow,
            expectedNote = expected,
            toleranceSemitones = fixture.assertions.semitoneTol
        )
    }

    // ── T6: Comfortable-range high matches expected note ──────────────────────

    @Test
    fun `fixture comfortable high matches expected note`() {
        val fixture = FixtureLoader.load(fixtureName)
        val expected = fixture.assertions.comfortableHighNote ?: return
        val pitches = SessionReplay.acceptedPitches(fixture.toPitchSamples())

        val (_, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        FixtureAssertHelper.assertWithinSemitones(
            label = "[$fixtureName] comfortableHigh",
            actualHz = comfortableHigh,
            expectedNote = expected,
            toleranceSemitones = fixture.assertions.semitoneTol
        )
    }

    // ── T7: Passaggio estimate matches expected note ──────────────────────────

    /**
     * The maximum-variance window should centre near the declared passaggio note,
     * within [FixtureAssertions.passaggioTol] semitones (intentionally wider than
     * the range tolerances because passaggio is inherently approximate — a 3-note
     * band rather than a single pitch).
     *
     * Only run when ≥ 30 frames are available; [FachClassifier.estimatePassaggio]
     * falls back to the mean for shorter sessions and the mean is not a meaningful
     * passaggio estimate.
     */
    @Test
    fun `fixture passaggio matches expected note`() {
        val fixture = FixtureLoader.load(fixtureName)
        val expected = fixture.assertions.passaggioNote ?: return
        val pitches = SessionReplay.acceptedPitches(fixture.toPitchSamples())

        // Passaggio estimate is unreliable with fewer than 30 accepted frames.
        if (pitches.size < 30) return

        val passaggio = FachClassifier.estimatePassaggio(pitches)

        FixtureAssertHelper.assertWithinSemitones(
            label = "[$fixtureName] passaggio",
            actualHz = passaggio,
            expectedNote = expected,
            toleranceSemitones = fixture.assertions.passaggioTol
        )
    }

    // ── T8: Invariant — comfortable ⊆ detected extremes ─────────────────────

    /**
     * The comfortable range must always be a sub-interval of the detected range.
     * Comfortable endpoints are percentile-based so they can never exceed the
     * absolute min/max of the accepted sample set — unless the implementation
     * is broken.
     *
     * This invariant must hold for every input; replay on all fixtures gives it
     * breadth across different pitch distributions.
     */
    @Test
    fun `fixture invariant comfortable range is within detected extremes`() {
        val fixture = FixtureLoader.load(fixtureName)
        val pitches = SessionReplay.acceptedPitches(fixture.toPitchSamples())
        if (pitches.size < 10) return  // comfortable range fallback for tiny sets is not comparable

        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        assertTrue(
            "[$fixtureName] comfortableLow ($comfortableLow Hz) must be ≥ detectedMin ($detectedMin Hz)",
            comfortableLow >= detectedMin
        )
        assertTrue(
            "[$fixtureName] comfortableHigh ($comfortableHigh Hz) must be ≤ detectedMax ($detectedMax Hz)",
            comfortableHigh <= detectedMax
        )
    }

    // ── T9: Invariant — detected min ≤ detected max ──────────────────────────

    @Test
    fun `fixture invariant detected min is less than or equal to detected max`() {
        val fixture = FixtureLoader.load(fixtureName)
        val pitches = SessionReplay.acceptedPitches(fixture.toPitchSamples())

        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)

        assertTrue(
            "[$fixtureName] detectedMin ($detectedMin Hz) must be ≤ detectedMax ($detectedMax Hz)",
            detectedMin <= detectedMax
        )
    }

    // ── T10: buildProfile produces consistent field values ────────────────────

    /**
     * [SessionReplay.buildProfile] must wire the same computation through as
     * calling each [FachClassifier] function directly.  This test ensures the
     * glue code in [SessionReplay] stays in sync with the classifier API.
     *
     * It also verifies that [sampleCount] in the profile equals the number of
     * accepted pitches, confirming no double-counting or off-by-one in the bridge.
     */
    @Test
    fun `fixture builds a VoiceProfile whose fields match direct classifier calls`() {
        val fixture = FixtureLoader.load(fixtureName)
        val samples = fixture.toPitchSamples()
        val profile = SessionReplay.buildProfile(samples)

        assertNotNull(
            "[$fixtureName] buildProfile returned null — fixture has too few accepted frames. " +
                    "Increase the frame count or lower minAcceptedFrames.",
            profile
        )
        profile!! // smart-cast after assertNotNull

        val pitches = SessionReplay.acceptedPitches(samples)

        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)

        assertEquals(
            "[$fixtureName] profile.detectedMinHz",
            detectedMin, profile.detectedMinHz, 0.01f
        )
        assertEquals(
            "[$fixtureName] profile.detectedMaxHz",
            detectedMax, profile.detectedMaxHz, 0.01f
        )
        assertEquals(
            "[$fixtureName] profile.comfortableLowHz",
            comfortableLow, profile.comfortableLowHz, 0.01f
        )
        assertEquals(
            "[$fixtureName] profile.comfortableHighHz",
            comfortableHigh, profile.comfortableHighHz, 0.01f
        )
        assertEquals(
            "[$fixtureName] profile.sampleCount should equal accepted pitch count",
            pitches.size, profile.sampleCount
        )
    }
}
