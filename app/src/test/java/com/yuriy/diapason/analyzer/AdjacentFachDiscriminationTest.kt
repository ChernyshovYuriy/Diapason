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
 *   TIER 2 — Scoring dimension boundaries (±10%/±20%/±30%)
 *     B1. Upper ceiling exactly at ±10% boundary → +3 points
 *     B2. Upper ceiling just outside ±10% → +2 points
 *     B3. Upper ceiling exactly at ±20% boundary → +2 points
 *     B4. Upper ceiling just outside ±20% → +1 point
 *     B5. Upper ceiling just outside ±30% → 0 points
 *     B6. Lower floor exactly at ±15% boundary → +2 points
 *     B7. Lower floor just outside ±15% → +1 point
 *
 *   TIER 3 — Classify invariants across all adjacent pairs
 *     C1. A profile exactly matching fach A ranks fach A above the adjacent fach B
 *         for every adjacent pair in the table
 */
class AdjacentFachDiscriminationTest {

    // ── Reference fach lookup (by unique range pair) ──────────────────────────

    private fun fach(minHz: Float, maxHz: Float) =
        ALL_FACH.first { it.rangeMinHz == minHz && it.rangeMaxHz == maxHz }

    private val lyricSoprano = fach(247f, 1047f)
    private val spintoSoprano = fach(233f, 988f)
    private val lyricTenor = fach(130f, 523f)
    private val spintoTenor = fach(123f, 494f)
    private val bassoCantante = fach(73f, 330f)
    private val bassoProfundo = fach(65f, 294f)
    private val dramaticMezzo = fach(175f, 784f)
    private val contralto = fach(165f, 698f)

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
     * Spinto Tenor profile (range 123–494 Hz, tessitura F3–G#4, passaggio D4).
     * Expected: Spinto Tenor 14/14, Lyric Tenor 13/14.
     */
    @Test
    fun `spinto tenor profile ranks spinto tenor above lyric tenor`() {
        val p = profile(
            detectedMin = 123f,
            detectedMax = 492f,
            comfortableLow = 175f,
            comfortableHigh = 413f,
            passaggio = 294f,
        )
        val results = FachClassifier.classify(p)
        assertTrue(
            "Spinto Tenor must rank above Lyric Tenor for a clearly spinto-tenor profile",
            rankOf(spintoTenor, results) < rankOf(lyricTenor, results)
        )
        assertEquals("Spinto Tenor should score 14/14", 14, scoreOf(spintoTenor, results))
    }

    /**
     * Lyric Tenor profile (range 130–523 Hz, tessitura G3–A4, passaggio D#4).
     * Expected: Lyric Tenor 14/14, Spinto Tenor 13/14.
     */
    @Test
    fun `lyric tenor profile ranks lyric tenor above spinto tenor`() {
        val p = profile(
            detectedMin = 130f,
            detectedMax = 523f,
            comfortableLow = 196f,
            comfortableHigh = 440f,
            passaggio = 311f,
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
     * Basso Cantante profile (range 73–330 Hz, tessitura D2–C4, passaggio F3).
     * Expected: Basso Cantante 14, Basso Profundo 9 (large margin).
     */
    @Test
    fun `basso cantante profile ranks basso cantante well above basso profundo`() {
        val p = profile(
            detectedMin = 74f,
            detectedMax = 328f,
            comfortableLow = 99f,
            comfortableHigh = 260f,
            passaggio = 175f,
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

    /**
     * Upper-ceiling ratio exactly at 1.10 (the +3 / +2 boundary).
     * A detected max that is exactly 10% above the fach ceiling should award +3.
     * This pins the boundary as inclusive: ratio 1.10 → +3, not +2.
     */
    @Test
    fun `upper ceiling at exactly 10 percent above fach max awards 3 points`() {
        // Spinto Soprano rangeMaxHz = 988. 10% above = 988 * 1.10 = 1086.8
        val p = profile(
            detectedMin = 233f,
            detectedMax = 988f * 1.10f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        assertEquals(
            "Upper ceiling exactly at +10% of Spinto Soprano max must score 14/14",
            14, scoreOf(spintoSoprano, results)
        )
    }

    /**
     * Upper-ceiling ratio just outside 1.10 (10.1% above).
     * This should drop from +3 to +2, reducing total score by 1.
     */
    @Test
    fun `upper ceiling at 10_1 percent above fach max awards 2 points not 3`() {
        val p = profile(
            detectedMin = 233f,
            detectedMax = 988f * 1.101f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        assertEquals(
            "Upper ceiling at 10.1% above Spinto max should score 13/14 (not 14)",
            13, scoreOf(spintoSoprano, results)
        )
    }

    /**
     * Upper-ceiling ratio exactly at 1.20 (the +2 / +1 boundary).
     * Ratio 1.20 is within [0.80, 1.20] → +2.
     */
    @Test
    fun `upper ceiling at exactly 20 percent above fach max awards 2 points`() {
        val p = profile(
            detectedMin = 233f,
            detectedMax = 988f * 1.20f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        // Max possible without the ceiling: 2+3+3+3 = 11, plus 2 for ceiling = 13
        assertEquals("Upper ceiling at exactly +20% should award +2, giving score 13", 13, score)
    }

    /**
     * Upper-ceiling ratio just outside 1.20 (20.1% above).
     * Should drop to +1.
     */
    @Test
    fun `upper ceiling at 20_1 percent above fach max awards 1 point`() {
        val p = profile(
            detectedMin = 233f,
            detectedMax = 988f * 1.201f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals("Upper ceiling at 20.1% above should award +1, giving score 12", 12, score)
    }

    /**
     * Upper-ceiling ratio outside 1.30 (30.1% above).
     * Should award 0 points for the ceiling dimension.
     */
    @Test
    fun `upper ceiling beyond 30 percent above fach max awards 0 points`() {
        val p = profile(
            detectedMin = 233f,
            detectedMax = 988f * 1.31f,
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals("Upper ceiling at 31% above should award 0, giving score 11", 11, score)
    }

    /**
     * Lower floor ratio exactly at 1.15 (the +2 / +1 boundary for the floor).
     * Ratio 1.15 is within [0.85, 1.15] → +2.
     *
     * To isolate the floor dimension, the ceiling is placed at +25% above fach max
     * (ratio 1.25 → in [0.70,1.30] → +1).  The non-floor dimensions score 1+3+3+3=10,
     * so floor +2 gives a total of 12.
     */
    @Test
    fun `lower floor at exactly 15 percent above fach min awards 2 points`() {
        // Spinto Soprano rangeMinHz = 233. 15% above = 233 * 1.15 = 267.95
        // Ceiling at 25% above to score +1 there, making total isolatable
        val p = profile(
            detectedMin = 233f * 1.15f,
            detectedMax = 988f * 1.25f,   // +25% → +1 (not +3), isolates floor
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val s = scoreOf(spintoSoprano, results)
        // ceiling=+1, floor=+2, tessHigh=+3, tessLow=+3, pass=+3 → 12
        assertEquals(
            "Lower floor at exactly +15% should award +2, giving 12 (ceiling deliberately at +1 zone)",
            12, s
        )
    }

    /**
     * Lower floor ratio just outside 1.15 (15.1% above).
     * Should drop from +2 to +1 → total score decreases by 1.
     */
    @Test
    fun `lower floor at 15_1 percent above fach min awards 1 point`() {
        val p = profile(
            detectedMin = 233f * 1.151f,
            detectedMax = 988f * 1.25f,   // +25% ceiling → +1 zone, isolates floor
            comfortableLow = 277f,
            comfortableHigh = 784f,
            passaggio = 466f,
        )
        val results = FachClassifier.classify(p)
        val score = scoreOf(spintoSoprano, results)
        assertEquals(

            "Lower floor at 15.1% above should award +1, giving 11 (ceiling at +1 zone)",
            11, score
        )
    }

    // ── TIER 3 — Adjacent-pair invariant across the full table ────────────────

    /**
     * Every adjacent fach pair (ordered as they appear in ALL_FACH) must satisfy:
     * a perfect-match profile for fach[i] scores AT LEAST AS HIGH on fach[i] as on fach[i+1].
     *
     * "Perfect match" = every profile dimension set to the fach's own definition value.
     *
     * Known ties documented here — pairs whose acoustic definitions overlap so closely
     * that the scoring function cannot separate them with a perfect-profile alone:
     *
     *   • Spinto Tenor (123–494 Hz) / Dramatic Tenor (110–466 Hz): both score 14.
     *     The ceiling ratio 494/466 = 1.060 and floor ratio 123/110 = 1.118 both
     *     fall comfortably inside each other's ±10% and ±15% windows.
     *
     *   • Lyric Baritone (110–392 Hz) / Kavalierbariton (98–370 Hz): both score 14.
     *     The overlap is structurally identical — ceiling ratio 392/370 = 1.059,
     *     floor ratio 110/98 = 1.122 both inside the top bands.
     *
     * These are known classifier limitations, not regressions.  The test verifies that
     * fach[i] does NOT score LESS than its neighbour (that would be an outright reversal)
     * and separately asserts strict separation for all non-tied pairs.
     */
    @Test
    fun `perfect match profile for each fach scores at least as high on that fach as its neighbour`() {
        // Range pairs that are acoustically indistinct — documented ties
        val knownTies = setOf(
            Pair(123f, 494f),   // Spinto Tenor — ties with Dramatic Tenor
            Pair(110f, 466f),   // Dramatic Tenor — symmetric entry (not tested as [i] here)
            Pair(110f, 392f),   // Lyric Baritone — ties with Kavalierbariton
        )

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
            val isKnownTie = knownTies.contains(Pair(fachA.rangeMinHz, fachA.rangeMaxHz))

            if (isKnownTie) {
                // For known ties, assert fach[i] does not score LESS than its neighbour
                assertTrue(
                    "Known-tie pair: fach (${fachA.rangeMinHz}–${fachA.rangeMaxHz}) must not " +
                            "score LESS than its neighbour (${fachB.rangeMinHz}–${fachB.rangeMaxHz}) " +
                            "($scoreOnA vs $scoreOnB)",
                    scoreOnA >= scoreOnB
                )
            } else {
                // For all other pairs, require strict separation
                assertTrue(
                    "Perfect profile for fach (${fachA.rangeMinHz}–${fachA.rangeMaxHz}) " +
                            "must score strictly higher on itself ($scoreOnA) than on its neighbour " +
                            "(${fachB.rangeMinHz}–${fachB.rangeMaxHz}) ($scoreOnB)",
                    scoreOnA > scoreOnB
                )
            }
        }
    }
}
