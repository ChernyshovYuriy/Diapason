package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FachClassifier.classify].
 *
 * Pure JVM — [android.util.Log] is stubbed via `isReturnDefaultValues = true`
 * in the `testOptions` block of `app/build.gradle.kts`, so the Log calls
 * inside `classify` do not throw.
 *
 * ── Identification strategy ────────────────────────────────────────────────
 * [FachDefinition.nameRes] is an Android `@StringRes` Int — not a human-
 * readable string in a unit test.  Instead, we identify each fach by its
 * unique `(rangeMinHz, rangeMaxHz)` pair, which is stable and meaningful.
 * Each winning-fach assertion documents the expected voice type name as a
 * comment so test failures are immediately readable.
 *
 * ── Tests ──────────────────────────────────────────────────────────────────
 *  C1.  A profile built from a fach's own definition parameters ranks
 *       that fach first with a perfect score (14/14).
 *  C2.  Five cross-category profiles: soprano, tenor, bass, baritone,
 *       contralto each rank their expected fach first.
 *  C3.  The returned list covers all 19 fachs — no entry is dropped.
 *  C4.  The list is sorted descending by score (invariant of classify).
 *  C5.  All scores are in [0, 14] — no over- or under-flow.
 *  C6.  A profile far outside every fach's range still returns all 19
 *       fachs without crashing (graceful degradation).
 *  C7.  A female voice profile never ranks a male fach first, and vice
 *       versa — basic cross-gender separation.
 */
class FachClassifierClassifyTest {

    // ── Profile factory ───────────────────────────────────────────────────────

    /**
     * Builds a [VoiceProfile] whose Hz values exactly match [fach]'s own
     * parameters, producing a perfect 14/14 score for that fach.
     */
    private fun perfectProfileFor(fach: FachDefinition) = VoiceProfile(
        detectedMinHz = fach.rangeMinHz,
        detectedMaxHz = fach.rangeMaxHz,
        comfortableLowHz = fach.tessituraMinHz,
        comfortableHighHz = fach.tessituraMaxHz,
        estimatedPassaggioHz = fach.passaggioHz,
        sampleCount = 60,
        durationSeconds = 30f
    )

    /**
     * Identifies a [FachDefinition] by its unique `(rangeMinHz, rangeMaxHz)`
     * pair — stable across localisations and resource-ID changes.
     */
    private fun fachByRange(minHz: Float, maxHz: Float): FachDefinition =
        ALL_FACH.first { it.rangeMinHz == minHz && it.rangeMaxHz == maxHz }

    // ── C1. Perfect score for exact-match profile ─────────────────────────────

    /**
     * When every profile field equals a fach's own definition values, that fach
     * must score 14/14 and rank first.  We verify this for a representative
     * spread of voice categories rather than all 19 to keep the test readable.
     */
    @Test
    fun `profile built from fach definition parameters scores 14 and ranks first`() {
        val testFachs = listOf(
            fachByRange(247f, 1047f),  // Lyric Soprano
            fachByRange(130f, 523f),   // Lyric Tenor
            fachByRange(87f, 349f),   // Dramatic Baritone
            fachByRange(65f, 294f),   // Basso Profundo
            fachByRange(165f, 698f),   // Contralto
        )

        for (fach in testFachs) {
            val profile = perfectProfileFor(fach)
            val results = FachClassifier.classify(profile)

            assertEquals(
                "Perfect-match fach should score 14/14 " +
                        "(rangeMin=${fach.rangeMinHz} rangeMax=${fach.rangeMaxHz})",
                14, results.first().score
            )
            assertEquals(
                "Perfect-match fach must rank first " +
                        "(rangeMin=${fach.rangeMinHz} rangeMax=${fach.rangeMaxHz})",
                fach.rangeMinHz, results.first().fach.rangeMinHz, 0.01f
            )
            assertEquals(
                "Perfect-match fach rangeMax must match " +
                        "(rangeMin=${fach.rangeMinHz} rangeMax=${fach.rangeMaxHz})",
                fach.rangeMaxHz, results.first().fach.rangeMaxHz, 0.01f
            )
        }
    }

    // ── C2. Cross-category ranking ────────────────────────────────────────────

    /**
     * Lyric Soprano profile (range 247–1047 Hz, tessitura D4–A5, passaggio B4).
     * Expected winner: Lyric Soprano (rangeMin=247, rangeMax=1047).
     */
    @Test
    fun `lyric soprano profile ranks lyric soprano first`() {
        val profile = VoiceProfile(
            detectedMinHz = 247f,
            detectedMaxHz = 1047f,
            comfortableLowHz = 294f,
            comfortableHighHz = 880f,
            estimatedPassaggioHz = 494f,
            sampleCount = 60, durationSeconds = 30f
        )
        val winner = FachClassifier.classify(profile).first().fach
        assertEquals(
            "Winner rangeMin should be 247 (Lyric Soprano)",
            247f,
            winner.rangeMinHz,
            0.01f
        )
        assertEquals(
            "Winner rangeMax should be 1047 (Lyric Soprano)",
            1047f,
            winner.rangeMaxHz,
            0.01f
        )
    }

    /**
     * Lyric Tenor profile (range 130–523 Hz, tessitura G3–A4, passaggio D#4).
     * Expected winner: Lyric Tenor (rangeMin=130, rangeMax=523).
     */
    @Test
    fun `lyric tenor profile ranks lyric tenor first`() {
        val profile = VoiceProfile(
            detectedMinHz = 130f,
            detectedMaxHz = 523f,
            comfortableLowHz = 196f,
            comfortableHighHz = 440f,
            estimatedPassaggioHz = 311f,
            sampleCount = 60, durationSeconds = 30f
        )
        val winner = FachClassifier.classify(profile).first().fach
        assertEquals("Winner rangeMin should be 130 (Lyric Tenor)", 130f, winner.rangeMinHz, 0.01f)
        assertEquals("Winner rangeMax should be 523 (Lyric Tenor)", 523f, winner.rangeMaxHz, 0.01f)
    }

    /**
     * Basso Profundo profile (range 65–294 Hz, tessitura E2–A3, passaggio D#3).
     * Expected winner: Basso Profundo (rangeMin=65, rangeMax=294).
     */
    @Test
    fun `basso profundo profile ranks basso profundo first`() {
        val profile = VoiceProfile(
            detectedMinHz = 65f,
            detectedMaxHz = 294f,
            comfortableLowHz = 82f,
            comfortableHighHz = 220f,
            estimatedPassaggioHz = 155f,
            sampleCount = 60, durationSeconds = 30f
        )
        val winner = FachClassifier.classify(profile).first().fach
        assertEquals("Winner rangeMin should be 65 (Basso Profundo)", 65f, winner.rangeMinHz, 0.01f)
        assertEquals(
            "Winner rangeMax should be 294 (Basso Profundo)",
            294f,
            winner.rangeMaxHz,
            0.01f
        )
    }

    /**
     * Dramatic Baritone profile (range 87–349 Hz, tessitura B2–D4, passaggio G3).
     * Expected winner: Dramatic Baritone (rangeMin=87, rangeMax=349).
     */
    @Test
    fun `dramatic baritone profile ranks dramatic baritone first`() {
        val profile = VoiceProfile(
            detectedMinHz = 87f,
            detectedMaxHz = 349f,
            comfortableLowHz = 123f,
            comfortableHighHz = 294f,
            estimatedPassaggioHz = 196f,
            sampleCount = 60, durationSeconds = 30f
        )
        val winner = FachClassifier.classify(profile).first().fach
        assertEquals(
            "Winner rangeMin should be 87 (Dramatic Baritone)",
            87f,
            winner.rangeMinHz,
            0.01f
        )
        assertEquals(
            "Winner rangeMax should be 349 (Dramatic Baritone)",
            349f,
            winner.rangeMaxHz,
            0.01f
        )
    }

    /**
     * Contralto profile (range 165–698 Hz, tessitura G3–C5, passaggio E4).
     * Expected winner: Contralto (rangeMin=165, rangeMax=698).
     */
    @Test
    fun `contralto profile ranks contralto first`() {
        val profile = VoiceProfile(
            detectedMinHz = 165f,
            detectedMaxHz = 698f,
            comfortableLowHz = 196f,
            comfortableHighHz = 523f,
            estimatedPassaggioHz = 330f,
            sampleCount = 60, durationSeconds = 30f
        )
        val winner = FachClassifier.classify(profile).first().fach
        assertEquals("Winner rangeMin should be 165 (Contralto)", 165f, winner.rangeMinHz, 0.01f)
        assertEquals("Winner rangeMax should be 698 (Contralto)", 698f, winner.rangeMaxHz, 0.01f)
    }

    // ── C3. All 19 fachs are present in results ───────────────────────────────

    @Test
    fun `classify returns all 19 fach entries`() {
        val profile = perfectProfileFor(fachByRange(247f, 1047f))  // Lyric Soprano
        val results = FachClassifier.classify(profile)
        assertEquals("classify must return one entry per fach", ALL_FACH.size, results.size)
    }

    @Test
    fun `classify result contains every fach from ALL_FACH exactly once`() {
        val profile = perfectProfileFor(fachByRange(130f, 523f))  // Lyric Tenor
        val resultFachs = FachClassifier.classify(profile).map { it.fach }
        for (fach in ALL_FACH) {
            assertTrue(
                "ALL_FACH entry (rangeMin=${fach.rangeMinHz}, rangeMax=${fach.rangeMaxHz}) " +
                        "is missing from classify() results",
                resultFachs.any { it.rangeMinHz == fach.rangeMinHz && it.rangeMaxHz == fach.rangeMaxHz }
            )
        }
    }

    // ── C4. Results are sorted descending by score ────────────────────────────

    @Test
    fun `classify returns results sorted descending by score`() {
        val profile = perfectProfileFor(fachByRange(247f, 1047f))
        val results = FachClassifier.classify(profile)
        for (i in 0 until results.size - 1) {
            assertTrue(
                "Score at index $i (${results[i].score}) must be ≥ score at index ${i + 1} " +
                        "(${results[i + 1].score})",
                results[i].score >= results[i + 1].score
            )
        }
    }

    // ── C5. All scores are in [0, 14] ─────────────────────────────────────────

    @Test
    fun `all scores are between 0 and 14 inclusive`() {
        val profile = perfectProfileFor(fachByRange(130f, 523f))
        val results = FachClassifier.classify(profile)
        for (match in results) {
            assertTrue(
                "Score ${match.score} for (rangeMin=${match.fach.rangeMinHz}) " +
                        "is below 0",
                match.score >= 0
            )
            assertTrue(
                "Score ${match.score} for (rangeMin=${match.fach.rangeMinHz}) " +
                        "exceeds maxScore ${match.maxScore}",
                match.score <= match.maxScore
            )
        }
    }

    @Test
    fun `maxScore is 14 for every entry`() {
        val profile = perfectProfileFor(fachByRange(65f, 294f))
        FachClassifier.classify(profile).forEach { match ->
            assertEquals(
                "maxScore must always be 14 (rangeMin=${match.fach.rangeMinHz})",
                14, match.maxScore
            )
        }
    }

    // ── C6. Graceful degradation for an extreme out-of-range profile ──────────

    /**
     * A profile with ranges far outside every defined fach — e.g. a robot voice
     * spanning 10 Hz to 10000 Hz — must still return all 19 fachs without crashing.
     * All scores may legitimately be 0 or very low.
     */
    @Test
    fun `extreme out-of-range profile does not crash and returns all fachs`() {
        val extreme = VoiceProfile(
            detectedMinHz = 10f,
            detectedMaxHz = 10000f,
            comfortableLowHz = 50f,
            comfortableHighHz = 5000f,
            estimatedPassaggioHz = 2500f,
            sampleCount = 20, durationSeconds = 10f
        )
        val results = FachClassifier.classify(extreme)
        assertEquals(
            "Must return all 19 fachs even for out-of-range profile",
            ALL_FACH.size,
            results.size
        )
        results.forEach { match ->
            assertTrue("Score must be ≥ 0", match.score >= 0)
            assertTrue("Score must be ≤ 14", match.score <= match.maxScore)
        }
    }

    // ── C7. Cross-gender separation ───────────────────────────────────────────

    /**
     * A clear soprano profile (range C4–C7, tessiture D4–E6) must not rank
     * any bass or baritone fach first.  The soprano ceiling (rangeMax ≥ 880 Hz)
     * is used to identify female-voice fachs; bass/baritone tops out at ≤ 392 Hz.
     */
    @Test
    fun `soprano profile does not rank a bass or baritone fach first`() {
        val soprano = VoiceProfile(
            detectedMinHz = 247f,
            detectedMaxHz = 1047f,
            comfortableLowHz = 294f,
            comfortableHighHz = 880f,
            estimatedPassaggioHz = 494f,
            sampleCount = 60, durationSeconds = 30f
        )
        val winner = FachClassifier.classify(soprano).first().fach
        // Bass and baritone fachs all have rangeMaxHz ≤ 392 Hz
        assertTrue(
            "Soprano profile must not rank a bass/baritone fach first " +
                    "(winner rangeMax=${winner.rangeMaxHz})",
            winner.rangeMaxHz > 600f
        )
    }

    /**
     * A clear bass profile (range E2–E4, tessiture G2–C4) must not rank
     * any soprano or mezzo fach first.  Soprano/mezzo fachs have rangeMaxHz ≥ 698 Hz.
     */
    @Test
    fun `bass profile does not rank a soprano or mezzo fach first`() {
        val bass = VoiceProfile(
            detectedMinHz = 65f,
            detectedMaxHz = 294f,
            comfortableLowHz = 82f,
            comfortableHighHz = 220f,
            estimatedPassaggioHz = 155f,
            sampleCount = 60, durationSeconds = 30f
        )
        val winner = FachClassifier.classify(bass).first().fach
        // Soprano / mezzo / contralto fachs all have rangeMaxHz ≥ 698 Hz
        assertTrue(
            "Bass profile must not rank a soprano/mezzo fach first " +
                    "(winner rangeMax=${winner.rangeMaxHz})",
            winner.rangeMaxHz < 500f
        )
    }

    /**
     * Score breakdown list must be non-empty for every entry and must contain
     * exactly 5 elements (one per scoring dimension).
     */
    @Test
    fun `every FachMatch has a score breakdown with exactly 5 entries`() {
        val profile = perfectProfileFor(fachByRange(247f, 1047f))
        val results = FachClassifier.classify(profile)
        for (match in results) {
            assertEquals(
                "scoreBreakdown must have 5 entries (one per scoring dimension) " +
                        "for fach rangeMin=${match.fach.rangeMinHz}",
                5, match.scoreBreakdown.size
            )
        }
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    /**
     * [FachClassifier.classify] is a pure function: calling it twice with the
     * same [VoiceProfile] must return an identical ranked list.  Non-determinism
     * (e.g. from an unordered map or a mutable sort) would cause the UI to show
     * a different "best match" on each navigation to the results screen.
     */
    @Test
    fun `classify is deterministic - same profile always produces the same ordered results`() {
        val profile = perfectProfileFor(fachByRange(130f, 523f))  // Lyric Tenor

        val first = FachClassifier.classify(profile)
        val second = FachClassifier.classify(profile)

        assertEquals(
            "classify must return the same number of results on every call",
            first.size,
            second.size
        )
        first.zip(second).forEachIndexed { index, (a, b) ->
            assertEquals(
                "Result at rank $index must have the same score on both calls " +
                        "(${a.fach.rangeMinHz}–${a.fach.rangeMaxHz})",
                a.score, b.score
            )
            assertEquals(
                "Result at rank $index must point to the same fach on both calls",
                a.fach.rangeMinHz, b.fach.rangeMinHz, 0.01f
            )
            assertEquals(
                "Result at rank $index rangeMax must match",
                a.fach.rangeMaxHz, b.fach.rangeMaxHz, 0.01f
            )
        }
    }

    // ── Score breakdown content ───────────────────────────────────────────────

    /**
     * Awarded points (score > 0) must use a "+" prefix in the breakdown entry,
     * and zero points must use a "  0" prefix.  This convention is relied on
     * by the Logcat output and any future UI that renders the breakdown.
     */
    @Test
    fun `score breakdown entries for awarded points start with a plus sign`() {
        // A perfect-match profile will have "+3", "+2", etc. for every dimension.
        val fach = fachByRange(247f, 1047f)  // Lyric Soprano
        val results = FachClassifier.classify(perfectProfileFor(fach))
        val winner = results.first()

        assertEquals("Winner should score 14/14", 14, winner.score)

        winner.scoreBreakdown.forEach { entry ->
            assertTrue(
                "Every breakdown entry for a perfect-match must start with '+' (got: '$entry')",
                entry.trimStart().startsWith("+")
            )
        }
    }

    @Test
    fun `score breakdown entries for zero points start with zero marker`() {
        // Build a profile that perfectly matches Lyric Soprano.
        // The Contrabass (Oktavist) fach will score 0 on every dimension.
        val profile = perfectProfileFor(fachByRange(247f, 1047f))  // Lyric Soprano
        val results = FachClassifier.classify(profile)

        // Contrabass is rangeMin=43, rangeMax=220 — the furthest from a soprano
        val contrabass = results.first { it.fach.rangeMinHz == 43f }

        contrabass.scoreBreakdown.forEach { entry ->
            assertTrue(
                "Every breakdown entry for Contrabass against a soprano profile " +
                        "should start with '  0' (got: '$entry')",
                entry.startsWith("  0")
            )
        }
    }
}
