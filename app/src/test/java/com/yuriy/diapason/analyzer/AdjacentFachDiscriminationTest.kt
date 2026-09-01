package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [FachClassifier.classify] at the hardest decision boundaries:
 * pairs of adjacent voice types whose acoustic ranges significantly overlap.
 *
 * These are the cases where the classifier is most likely to be wrong and where
 * a code change could silently flip a result.  Each test documents which fach
 * should win for a given profile and WHY (the score difference is shown in the
 * comment so future maintainers understand the margin).
 *
 * Tests are grouped by difficulty tier:
 *
 *   TIER 1 — Clearly separated profiles (large margin, both directions)
 *     A1. Lyric Soprano vs Spinto Soprano
 *     A2. Lyric Tenor vs Spinto Tenor
 *     A3. Basso Profundo vs Basso Cantante
 *     A4. Dramatic Mezzo vs Contralto
 *
 *   TIER 2 — Scoring dimension boundaries (floor/ceiling, both sides of ±)
 *     Floor is weighted higher (0-3, finer bands) than ceiling (0-2, coarser
 *     bands) — see the comment above FachClassifier.classify()'s floor block.
 *     Every boundary is tested on both sides of the symmetric ratio band
 *     (detected value above AND below the fach's reference value) — the
 *     original set only covered the upper half; the lower half was a
 *     coverage gap found and closed during a second, separate audit pass.
 *     B1. Lower floor exactly at ±10% boundary → +3 points
 *     B2. Lower floor just outside ±10% → +2 points
 *     B3. Lower floor exactly at ±20% boundary → +2 points
 *     B4. Lower floor just outside ±20% → +1 point
 *     B5. Lower floor just outside ±30% → 0 points
 *     B6. Upper ceiling exactly at ±15% boundary → +2 points
 *     B7. Upper ceiling just outside ±15% → +1 point
 *
 *   TIER 2c — tessHigh / tessLow / passaggio boundaries
 *     tessHigh and tessLow share floor's identical 3-tier formula but never had
 *     boundary tests before; passaggio's tolerance-multiplier tiers didn't
 *     either — both closed in the same audit pass as TIER 2's lower half,
 *     table-driven rather than one test per case given the volume.
 *
 *   TIER 3 — Classify invariants across all adjacent pairs
 *     C1. A profile exactly matching fach A ranks fach A above the adjacent fach B
 *         for every adjacent pair in the table
 */
class AdjacentFachDiscriminationTest {

    // ── Reference fach lookup (by unique range pair) ──────────────────────────

    private fun fach(minHz: Float, maxHz: Float) =
        ALL_FACH.first { it.rangeMinHz == minHz && it.rangeMaxHz == maxHz }

    private val coloraturaSoprano = fach(262f, 1568f)
    private val lyricColoraturaSoprano = fach(262f, 1397f)
    private val lyricSoprano = fach(247f, 1047f)
    private val spintoSoprano = fach(233f, 988f)
    private val lyricTenor = fach(130f, 523f)
    private val spintoTenor = fach(123f, 494f)
    private val bassoCantante = fach(73f, 330f)
    private val bassoProfundo = fach(65f, 294f)
    private val dramaticMezzo = fach(175f, 784f)
    private val contralto = fach(165f, 698f)
    private val dramaticTenor = fach(110f, 466f)

    // ── Profile factory ───────────────────────────────────────────────────────

    private fun profile(
        detectedMin: Float,
        detectedMax: Float,
        comfortableLow: Float,
        comfortableHigh: Float,
        passaggio: Float,
        sampleCount: Int = 60,
    ) = VoiceProfile(
        detectedMinHz = detectedMin,
        detectedMaxHz = detectedMax,
        comfortableLowHz = comfortableLow,
        comfortableHighHz = comfortableHigh,
        estimatedPassaggioHz = passaggio,
        sampleCount = sampleCount,
        durationSeconds = 40f,
    )

    private fun rankOf(target: FachDefinition, results: List<FachMatch>): Int =
        results.indexOfFirst { it.fach.rangeMinHz == target.rangeMinHz && it.fach.rangeMaxHz == target.rangeMaxHz }

    private fun scoreOf(target: FachDefinition, results: List<FachMatch>): Int =
        results.first { it.fach.rangeMinHz == target.rangeMinHz && it.fach.rangeMaxHz == target.rangeMaxHz }.score

    // ── TIER 1 — Clearly separated profiles ──────────────────────────────────

    /**
     * A realistic Coloratura Soprano session: detected max at E6 (1319 Hz), comfortable
     * range A4–C6, passaggio at E5.
     *
     * Expected: Coloratura Soprano 11/14, Lyric Coloratura Soprano 10/14.
     * Key differentiator: passaggio (Coloratura E5=659 Hz vs Lyric Coloratura C5=523 Hz).
     *
     * This profile specifically validates that rangeMaxHz=1568 Hz (G6) allows a singer who
     * reaches E6 to score 2 pts for the Coloratura ceiling rather than 0 pts (which was the
     * result with the previous erroneous rangeMaxHz=2093 Hz, causing Lyric Coloratura to
     * outscore Coloratura).
     */
    @Test
    fun `coloratura soprano profile ranks coloratura above lyric coloratura`() {
        val p = profile(
            detectedMin = 262f,   // C4
            detectedMax = 1319f,  // E6 — a high but realistic session ceiling
            comfortableLow = 440f,  // A4
            comfortableHigh = 1047f, // C6
            passaggio = 659f,       // E5
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Coloratura Soprano must rank above Lyric Coloratura for a coloratura profile " +
                    "(Coloratura rank=${rankOf(coloraturaSoprano, results)}, " +
                    "Lyric Coloratura rank=${rankOf(lyricColoraturaSoprano, results)})",
            rankOf(coloraturaSoprano, results) < rankOf(lyricColoraturaSoprano, results)
        )
    }

    /**
     * A profile closely matching Spinto Soprano definition parameters.
     * Expected: Spinto Soprano 14/14, Lyric Soprano 13/14 (tessiture high misses by ~0.9%).
     */
    @Test
    fun `spinto soprano profile ranks spinto soprano above lyric soprano`() {
        val p = profile(
            detectedMin = 233f,
            detectedMax = 985f,
            comfortableLow = 278f,
            comfortableHigh = 780f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Spinto Soprano must rank above Lyric Soprano for a clearly spinto profile " +
                    "(Spinto rank=${rankOf(spintoSoprano, results)}, Lyric rank=${
                        rankOf(
                            lyricSoprano,
                            results
                        )
                    })",
            rankOf(spintoSoprano, results) < rankOf(lyricSoprano, results)
        )
        assertEquals(
            "Spinto Soprano should score 14/14 for its own parameters",
            14, scoreOf(spintoSoprano, results)
        )
    }

    /**
     * A profile closely matching Lyric Soprano definition parameters.
     * Expected: Lyric Soprano 14/14, Spinto Soprano 13/14 (tessiture high misses).
     */
    @Test
    fun `lyric soprano profile ranks lyric soprano above spinto soprano`() {
        val p = profile(
            detectedMin = 247f,
            detectedMax = 1047f,
            comfortableLow = 295f,
            comfortableHigh = 880f,
            passaggio = 494f,
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Lyric Soprano must rank above Spinto Soprano for a clearly lyric profile",
            rankOf(lyricSoprano, results) < rankOf(spintoSoprano, results)
        )
    }

    /**
     * Spinto Tenor profile (range 123–494 Hz, tessitura F3–G#4, passaggio D#4).
     * Expected: Spinto Tenor 14/14, Lyric Tenor 13/14.
     */
    @Test
    fun `spinto tenor profile ranks spinto tenor above lyric tenor`() {
        val p = profile(
            detectedMin = 123f,
            detectedMax = 492f,
            comfortableLow = 175f,
            comfortableHigh = 413f,
            passaggio = 311f,
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Spinto Tenor must rank above Lyric Tenor for a clearly spinto-tenor profile",
            rankOf(spintoTenor, results) < rankOf(lyricTenor, results)
        )
        assertEquals("Spinto Tenor should score 14/14", 14, scoreOf(spintoTenor, results))
    }

    /**
     * Lyric Tenor profile (range 130–523 Hz, tessitura G3–A4, passaggio E4).
     * Expected: Lyric Tenor 14/14, Spinto Tenor 13/14.
     */
    @Test
    fun `lyric tenor profile ranks lyric tenor above spinto tenor`() {
        val p = profile(
            detectedMin = 130f,
            detectedMax = 523f,
            comfortableLow = 196f,
            comfortableHigh = 440f,
            passaggio = 330f,
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Lyric Tenor must rank above Spinto Tenor for a clearly lyric-tenor profile",
            rankOf(lyricTenor, results) < rankOf(spintoTenor, results)
        )
    }

    /**
     * Basso Profundo profile (range 65–294 Hz, tessitura E2–A3, passaggio D#3).
     * Expected: Basso Profundo 14, Basso Cantante 9 (large margin).
     */
    @Test
    fun `basso profundo profile ranks basso profundo well above basso cantante`() {
        val p = profile(
            detectedMin = 65f,
            detectedMax = 292f,
            comfortableLow = 82f,
            comfortableHigh = 218f,
            passaggio = 155f,
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Basso Profundo must rank above Basso Cantante for a profundo profile",
            rankOf(bassoProfundo, results) < rankOf(bassoCantante, results)
        )
        val profundoScore = scoreOf(bassoProfundo, results)
        val cantanteScore = scoreOf(bassoCantante, results)
        assertTrue(
            "Basso Profundo ($profundoScore) should outscore Basso Cantante ($cantanteScore) by at least 3 pts",
            profundoScore - cantanteScore >= 3
        )
    }

    /**
     * Basso Cantante profile (range 73–330 Hz, tessitura D2–C4, passaggio F#3).
     * Expected: Basso Cantante 14, Basso Profundo 9 (large margin).
     */
    @Test
    fun `basso cantante profile ranks basso cantante well above basso profundo`() {
        val p = profile(
            detectedMin = 74f,
            detectedMax = 328f,
            comfortableLow = 99f,
            comfortableHigh = 260f,
            passaggio = 185f,
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Basso Cantante must rank above Basso Profundo for a cantante profile",
            rankOf(bassoCantante, results) < rankOf(bassoProfundo, results)
        )
    }

    /**
     * Dramatic Mezzo profile (range 175–784 Hz, tessitura A3–D#5, passaggio F#4).
     * Expected: Dramatic Mezzo 14, Contralto 10 (clear margin).
     */
    @Test
    fun `dramatic mezzo profile ranks dramatic mezzo above contralto`() {
        val p = profile(
            detectedMin = 176f,
            detectedMax = 782f,
            comfortableLow = 221f,
            comfortableHigh = 620f,
            passaggio = 370f,
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Dramatic Mezzo must rank above Contralto for a dramatic-mezzo profile",
            rankOf(dramaticMezzo, results) < rankOf(contralto, results)
        )
        assertEquals("Dramatic Mezzo should score 14/14", 14, scoreOf(dramaticMezzo, results))
    }

    /**
     * Contralto profile (range 165–698 Hz, tessitura G3–C5, passaggio E4).
     * Expected: Contralto 14, Dramatic Mezzo 10 (clear margin).
     */
    @Test
    fun `contralto profile ranks contralto above dramatic mezzo`() {
        val p = profile(
            detectedMin = 166f,
            detectedMax = 696f,
            comfortableLow = 197f,
            comfortableHigh = 521f,
            passaggio = 330f,
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Contralto must rank above Dramatic Mezzo for a contralto profile",
            rankOf(contralto, results) < rankOf(dramaticMezzo, results)
        )
        assertEquals("Contralto should score 14/14", 14, scoreOf(contralto, results))
    }

    // ── TIER 2 — Scoring dimension boundary conditions ────────────────────────
    //
    // Floor is isolated by holding the ceiling at an exact match (detectedMax =
    // rangeMaxHz → its own top tier, +2). Ceiling is isolated by holding the
    // floor at an exact match (detectedMin = rangeMinHz → its own top tier, +3).
    // tessHigh/tessLow/passaggio are exact matches throughout (+3 each = +9).

    /**
     * Lower-floor ratio exactly at 1.10 (the +3 / +2 boundary).
     * Ceiling held at an exact match (+2). Baseline excluding floor: 2+9=11.
     */
    @Test
    fun `lower floor at exactly 10 percent above fach min awards 3 points`() {
        // Spinto Soprano rangeMinHz = 233. 10% above = 233 * 1.10 = 256.3
        val p = profile(
            detectedMin = 233f * 1.10f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        assertEquals(
            "Lower floor exactly at +10% of Spinto Soprano min must score 14/14 (11 baseline + 3)",
            14, scoreOf(spintoSoprano, results)
        )
    }

    /**
     * Lower-floor ratio just outside 1.10 (10.1% above). Drops from +3 to +2.
     */
    @Test
    fun `lower floor at 10_1 percent above fach min awards 2 points not 3`() {
        val p = profile(
            detectedMin = 233f * 1.101f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        assertEquals(
            "Lower floor at 10.1% above Spinto min should score 13/14 (11 baseline + 2)",
            13, scoreOf(spintoSoprano, results)
        )
    }

    /**
     * Lower-floor ratio exactly at 1.20 (the +2 / +1 boundary).
     */
    @Test
    fun `lower floor at exactly 20 percent above fach min awards 2 points`() {
        val p = profile(
            detectedMin = 233f * 1.20f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals("Lower floor at exactly +20% should award +2, giving 13 (11 baseline + 2)", 13, score)
    }

    /**
     * Lower-floor ratio just outside 1.20 (20.1% above). Drops to +1.
     */
    @Test
    fun `lower floor at 20_1 percent above fach min awards 1 point`() {
        val p = profile(
            detectedMin = 233f * 1.201f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals("Lower floor at 20.1% above should award +1, giving 12 (11 baseline + 1)", 12, score)
    }

    /**
     * Lower-floor ratio outside 1.30 (30.1% above). Awards 0 points.
     */
    @Test
    fun `lower floor beyond 30 percent above fach min awards 0 points`() {
        val p = profile(
            detectedMin = 233f * 1.31f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals("Lower floor at 31% above should award 0, giving 11 (11 baseline + 0)", 11, score)
    }

    /**
     * Upper-ceiling ratio exactly at 1.15 (the +2 / +1 boundary for the ceiling).
     * Floor held at an exact match (+3). Baseline excluding ceiling: 3+9=12.
     */
    @Test
    fun `upper ceiling at exactly 15 percent above fach max awards 2 points`() {
        // Spinto Soprano rangeMaxHz = 988. 15% above = 988 * 1.15 = 1136.2
        val p = profile(
            detectedMin = 233f,
            detectedMax = 988f * 1.15f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val s = scoreOf(spintoSoprano, results)
        assertEquals(
            "Upper ceiling at exactly +15% should award +2, giving 14 (12 baseline + 2)",
            14, s
        )
    }

    /**
     * Upper-ceiling ratio just outside 1.15 (15.1% above). Drops from +2 to +1.
     */
    @Test
    fun `upper ceiling at 15_1 percent above fach max awards 1 point`() {
        val p = profile(
            detectedMin = 233f,
            detectedMax = 988f * 1.151f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals(
            "Upper ceiling at 15.1% above should award +1, giving 13 (12 baseline + 1)",
            13, score
        )
    }

    // ── TIER 2b — Lower half of the symmetric ratio bands ─────────────────────
    //
    // Every test above scaled detectedMin/detectedMax UPWARD from the fach's
    // reference value. The bands (`0.90f..1.10f` etc.) are symmetric around 1.0,
    // but the downward half was never exercised — found during a second,
    // separate audit pass. Mirrors the tests above exactly, just below 1.0.

    @Test
    fun `lower floor at exactly 10 percent below fach min awards 3 points`() {
        val p = profile(
            detectedMin = 233f * 0.90f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        assertEquals(
            "Lower floor exactly at -10% of Spinto Soprano min must score 14/14 (11 baseline + 3)",
            14, scoreOf(spintoSoprano, results)
        )
    }

    @Test
    fun `lower floor at 10_1 percent below fach min awards 2 points not 3`() {
        val p = profile(
            detectedMin = 233f * 0.899f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        assertEquals(
            "Lower floor at 10.1% below Spinto min should score 13/14 (11 baseline + 2)",
            13, scoreOf(spintoSoprano, results)
        )
    }

    @Test
    fun `lower floor at exactly 20 percent below fach min awards 2 points`() {
        val p = profile(
            detectedMin = 233f * 0.80f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals("Lower floor at exactly -20% should award +2, giving 13 (11 baseline + 2)", 13, score)
    }

    @Test
    fun `lower floor at 20_1 percent below fach min awards 1 point`() {
        val p = profile(
            detectedMin = 233f * 0.799f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals("Lower floor at 20.1% below should award +1, giving 12 (11 baseline + 1)", 12, score)
    }

    @Test
    fun `lower floor beyond 30 percent below fach min awards 0 points`() {
        val p = profile(
            detectedMin = 233f * 0.69f,
            detectedMax = 988f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals("Lower floor at 31% below should award 0, giving 11 (11 baseline + 0)", 11, score)
    }

    @Test
    fun `upper ceiling at exactly 15 percent below fach max awards 2 points`() {
        val p = profile(
            detectedMin = 233f,
            detectedMax = 988f * 0.85f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val s = scoreOf(spintoSoprano, results)
        assertEquals(
            "Upper ceiling at exactly -15% should award +2, giving 14 (12 baseline + 2)",
            14, s
        )
    }

    @Test
    fun `upper ceiling at 15_1 percent below fach max awards 1 point`() {
        val p = profile(
            detectedMin = 233f,
            detectedMax = 988f * 0.849f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals(
            "Upper ceiling at 15.1% below should award +1, giving 13 (12 baseline + 1)",
            13, score
        )
    }

    // ── TIER 2c — tessHigh / tessLow / passaggio boundaries ───────────────────
    //
    // These three dimensions never had exact-boundary tests at all before this
    // — found during the same second audit pass — despite tessHigh/tessLow
    // sharing floor's identical 3-tier formula, meaning a silent off-by-one on
    // either during a refactor would have gone completely uncaught. Table-driven
    // rather than one test per case (matching the individual style above) to
    // avoid several hundred lines of near-identical copy-paste across three
    // dimensions with up to 10 boundary points apiece.

    /**
     * tessHigh ratio boundaries (comfortableHigh / fach.tessituraMaxHz), both
     * above and below the reference. Isolated by holding floor(+3), ceiling(+2),
     * tessLow(+3), and passaggio(+3) at exact matches — baseline 11.
     */
    @Test
    fun `tessitura high boundaries award the correct tier on both sides of the reference`() {
        val cases = listOf(
            1.10f to 3, 1.101f to 2, 1.20f to 2, 1.201f to 1, 1.31f to 0,
            0.90f to 3, 0.899f to 2, 0.80f to 2, 0.799f to 1, 0.69f to 0,
        )
        for ((multiplier, expectedPts) in cases) {
            val p = profile(
                detectedMin = 233f,
                detectedMax = 988f,
                comfortableLow = 277f,
                comfortableHigh = 784f * multiplier,
                passaggio = 466f,
            )
            val score = scoreOf(spintoSoprano, FachClassifier.classify(p))
            assertEquals(
                "tessHigh at ${multiplier}x tessituraMaxHz should award $expectedPts pts, " +
                        "giving ${11 + expectedPts} (11 baseline + $expectedPts) — got $score",
                11 + expectedPts, score
            )
        }
    }

    /**
     * tessLow ratio boundaries (comfortableLow / fach.tessituraMinHz), both
     * sides. Isolated by holding floor(+3), ceiling(+2), tessHigh(+3), and
     * passaggio(+3) at exact matches — baseline 11.
     */
    @Test
    fun `tessitura low boundaries award the correct tier on both sides of the reference`() {
        val cases = listOf(
            1.10f to 3, 1.101f to 2, 1.20f to 2, 1.201f to 1, 1.31f to 0,
            0.90f to 3, 0.899f to 2, 0.80f to 2, 0.799f to 1, 0.69f to 0,
        )
        for ((multiplier, expectedPts) in cases) {
            val p = profile(
                detectedMin = 233f,
                detectedMax = 988f,
                comfortableLow = 277f * multiplier,
                comfortableHigh = 784f,
                passaggio = 466f,
            )
            val score = scoreOf(spintoSoprano, FachClassifier.classify(p))
            assertEquals(
                "tessLow at ${multiplier}x tessituraMinHz should award $expectedPts pts, " +
                        "giving ${11 + expectedPts} (11 baseline + $expectedPts) — got $score",
                11 + expectedPts, score
            )
        }
    }

    /**
     * Passaggio tolerance boundaries (|estimatedPassaggioHz - fach.passaggioHz|
     * vs. tol = fach.passaggioHz * 0.10). Unlike the ratio-based dimensions
     * above, this formula uses an absolute difference, so it is already
     * symmetric by construction (`abs()`) — exercising one direction (passaggio
     * above the reference) fully covers the logic. Isolated by holding floor(+3),
     * ceiling(+2), tessHigh(+3), and tessLow(+3) at exact matches — baseline 11.
     *
     * The "exactly at" cases use a tiny inward margin (0.02, far larger than
     * float rounding error at this magnitude but far smaller than the ~46 Hz
     * gap between tiers) rather than the literal boundary value: unlike the
     * ratio dimensions above, where production's own multiply-then-divide
     * mirrors the test's math almost exactly, this formula computes
     * `passaggio - fach.passaggioHz` after the test has already added the two
     * together — an add-then-subtract round trip that does not reliably recover
     * the original value bit-for-bit (confirmed empirically: the literal `tol *
     * 2` boundary landed a few millionths past the cutoff and flipped tiers).
     */
    @Test
    fun `passaggio tolerance boundaries award the correct tier`() {
        val tol = 466f * 0.10f
        val margin = 0.02f
        val cases = listOf(
            tol - margin to 3,
            tol + 0.1f to 2,
            tol * 2 - margin to 2,
            tol * 2 + 0.1f to 1,
            tol * 3.5f - margin to 1,
            tol * 3.5f + 0.1f to 0,
        )
        for ((diff, expectedPts) in cases) {
            val p = profile(
                detectedMin = 233f,
                detectedMax = 988f,
                comfortableLow = 277f,
                comfortableHigh = 784f,
                passaggio = 466f + diff,
            )
            val score = scoreOf(spintoSoprano, FachClassifier.classify(p))
            assertEquals(
                "passaggio diff=$diff (tol=$tol) should award $expectedPts pts, " +
                        "giving ${11 + expectedPts} (11 baseline + $expectedPts) — got $score",
                11 + expectedPts, score
            )
        }
    }

    // ── TIER 3 — Adjacent-pair invariant across the full table ────────────────

    /**
     * Every adjacent fach pair (ordered as they appear in ALL_FACH) must satisfy:
     * a perfect-match profile for fach[i] scores strictly higher on fach[i] than on
     * fach[i+1] — anything else would be an outright reversal.
     *
     * "Perfect match" = every profile dimension set to the fach's own definition value.
     *
     * No exact ties currently exist among adjacent pairs (verified by running every
     * pair through classify() directly, not assumed). This is a correction: an
     * earlier version of this test carved out Spinto Tenor/Dramatic Tenor and Lyric
     * Baritone/Kavalierbariton as "known ties, both score 14," which was true before
     * the ceiling/floor scoring swap (KNOWN_ISSUES.md #6) but silently stopped being
     * true after it — that swap changed which ratio tiers apply to which measurement,
     * and these two pairs now separate by exactly 1 point (14 vs 13) instead of tying.
     * The carve-out's own assertion (`>=`, not `==`) never caught the drift because a
     * 14-vs-13 result still satisfies "not less than." Found during a later, separate
     * audit pass — see KNOWN_ISSUES.md's "Inherent architectural limitations" for the
     * corrected acoustic-overlap note. Both pairs remain genuinely close (checked
     * directly: a 1-point margin is the smallest that occurs anywhere in the table —
     * but it is not unique to these two; 4 other adjacent pairs separate by exactly
     * 1 point too), just no longer identical.
     */
    @Test
    fun `perfect match profile for each fach scores strictly higher on itself than on its neighbour`() {
        val adjacentPairs = ALL_FACH.zipWithNext()

        for ((fachA, fachB) in adjacentPairs) {
            val profileA = VoiceProfile(
                detectedMinHz = fachA.rangeMinHz,
                detectedMaxHz = fachA.rangeMaxHz,
                comfortableLowHz = fachA.tessituraMinHz,
                comfortableHighHz = fachA.tessituraMaxHz,
                estimatedPassaggioHz = fachA.passaggioHz,
                sampleCount = 60,
                durationSeconds = 40f,
            )
            val results = FachClassifier.classify(profileA)
            val scoreOnA = scoreOf(fachA, results)
            val scoreOnB = scoreOf(fachB, results)

            assertTrue(
                "Perfect profile for fach (${fachA.rangeMinHz}–${fachA.rangeMaxHz}) " +
                        "must score strictly higher on itself ($scoreOnA) than on its neighbour " +
                        "(${fachB.rangeMinHz}–${fachB.rangeMaxHz}) ($scoreOnB)",
                scoreOnA > scoreOnB
            )
        }
    }

    // ── TIER 3b — Ranked-list tie-break order is deterministic ────────────────
    //
    // classify() relies on Kotlin's sortedByDescending being a stable sort: when
    // two fachs tie exactly, the results list must show them in ALL_FACH's
    // declaration order, not whichever order the sort implementation happens to
    // produce. This matters because the Results screen shows a ranked list — an
    // unstable sort would make which fach appears at a tied rank nondeterministic
    // across runs or Kotlin stdlib versions. The existing determinism test above
    // only confirms the same profile gives the same order on repeated calls,
    // which would also hold for a merely-deterministic-but-unstable sort — it
    // does not confirm the order actually follows declaration order on a tie.
    //
    // A perfect Spinto Tenor profile produces a genuine, verified 13-13 tie for
    // 2nd place between Lyric Tenor (130–523 Hz) and Dramatic Tenor (110–466 Hz)
    // under the current classify() formula — found by running the classifier
    // directly rather than trusting the TIER 3 comment above, which predates the
    // ceiling/floor scoring swap (KNOWN_ISSUES.md #6) and now describes a pair
    // that no longer ties exactly (Lyric Baritone/Kavalierbariton and Spinto
    // Tenor/Dramatic Tenor both now separate by 1 point against their own
    // "primary" fach's perfect profile — a stale-comment finding worth a follow-up,
    // not fixed here since it doesn't affect this test's correctness).

    @Test
    fun `tied fachs preserve ALL_FACH declaration order in the ranked results`() {
        val profile = VoiceProfile(
            detectedMinHz = spintoTenor.rangeMinHz,
            detectedMaxHz = spintoTenor.rangeMaxHz,
            comfortableLowHz = spintoTenor.tessituraMinHz,
            comfortableHighHz = spintoTenor.tessituraMaxHz,
            estimatedPassaggioHz = spintoTenor.passaggioHz,
            sampleCount = 60,
            durationSeconds = 40f,
        )
        val results = FachClassifier.classify(profile)
        val lyricTenorScore = scoreOf(lyricTenor, results)
        val dramaticTenorScore = scoreOf(dramaticTenor, results)
        assertEquals(
            "Precondition: this profile must actually tie Lyric Tenor and Dramatic Tenor " +
                    "for this test to prove anything about tie-break order",
            lyricTenorScore, dramaticTenorScore
        )

        val lyricTenorRank = rankOf(lyricTenor, results)
        val dramaticTenorRank = rankOf(dramaticTenor, results)
        assertTrue(
            "On a tie, Lyric Tenor (declared before Dramatic Tenor in ALL_FACH) must rank " +
                    "before it (rank $lyricTenorRank vs $dramaticTenorRank) — classify() relies on " +
                    "sortedByDescending being a stable sort for a deterministic ranked list",
            lyricTenorRank < dramaticTenorRank
        )
    }
}
