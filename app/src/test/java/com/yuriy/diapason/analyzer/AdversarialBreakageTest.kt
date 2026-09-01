package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Deliberately adversarial tests written to try to BREAK the core analyzer logic —
 * not to confirm it works, but to find inputs where it doesn't.
 *
 * Every test here either (a) demonstrates a real defect with a concrete failing
 * assertion, or (b) proves a suspected weak spot is actually safe. Findings are
 * written up in the accompanying review notes; this file is the evidence.
 */
class AdversarialBreakageTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. hzToNoteName — non-finite input (FIXED — regression guard)
    // ─────────────────────────────────────────────────────────────────────────
    //
    // YinPitchDetectorTest already documents that a flat CMNDF (e.g. true silence)
    // makes parabolic interpolation compute 0.0/0.0 = NaN for the pitch. That NaN
    // was previously kept out of hzToNoteName only because VoiceAnalyzer happens to
    // gate every sample through `pitchHz in MIN_PITCH_HZ..MAX_PITCH_HZ` before it
    // is ever passed on — but hzToNoteName is a public function called directly
    // from five UI screens on stored/replayed session data, with no guard of its
    // own, and previously threw `IllegalArgumentException: Cannot round NaN value.`
    // Fixed by rejecting non-finite input the same way zero/negative is rejected.

    @Test
    fun `hzToNoteName returns the dash sentinel for NaN instead of crashing`() {
        assertEquals("—", FachClassifier.hzToNoteName(Float.NaN))
    }

    @Test
    fun `hzToNoteName returns the dash sentinel for positive infinity`() {
        assertEquals("—", FachClassifier.hzToNoteName(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `hzToNoteName returns the dash sentinel for negative infinity`() {
        assertEquals("—", FachClassifier.hzToNoteName(Float.NEGATIVE_INFINITY))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. estimateDetectedExtremes — symmetric isolated outliers (NOT a distinct
    //    bug — see writeup; documented here instead of "fixed")
    // ─────────────────────────────────────────────────────────────────────────
    //
    // My first attempt to "fix" this — fall back to the raw min/max whenever
    // neighbor-validation collapses stableMin == stableMax — broke two existing,
    // deliberately-asserted tests in EstimateDetectedExtremesStressTest:
    //   `one isolated high outlier is rejected by both implementations`  (30×330 + 1760)
    //   `two isolated outliers at both extremes — both rejected`         (55 + 30×330 + 2000)
    // Both of those ALSO collapse to stableMin == stableMax (there's only one real
    // stable cluster; the outlier(s) are correctly discarded regardless of which
    // side they're on) — and that collapse is exactly the intended, tested
    // behavior. Nothing about the collapsed (min, max) values alone can tell
    // "one genuine cluster, N isolated outliers" apart from "two genuine but
    // only-once-sampled extremes either side of a genuine cluster" — they are the
    // same shape. Un-collapsing one necessarily un-collapses (and breaks) the
    // other: a real per-side fix would need information neighbor-validation
    // doesn't have (e.g. session duration or an independent glissando detector).
    //
    // Conclusion: requiring >=2 corroborating frames (~320 ms) at a pitch before
    // it can register as a detected extreme is a deliberate, consistent trade-off,
    // not a bug — it just means a genuinely-sung note held for under two frames at
    // the very top or bottom of a glissando will never become the reported
    // extreme. Documenting current (collapsed) behavior here rather than "fixing" it.

    @Test
    fun `isolated outliers at both ends collapse the range to the only corroborated pitch`() {
        val pitches = listOf(100f, 300f, 300f, 900f)
        val (min, max) = FachClassifier.estimateDetectedExtremes(pitches)
        assertEquals("Only 300 Hz has a same-side neighbor, so it is both min and max", 300f, min, 0.01f)
        assertEquals("Only 300 Hz has a same-side neighbor, so it is both min and max", 300f, max, 0.01f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2b. estimateDetectedExtremes — total bypass, not collapse (documented, not
    //     fixed — second, separate audit pass, 2026-09-01)
    // ─────────────────────────────────────────────────────────────────────────
    //
    // Distinct from the collapse case above: there, one real cluster exists and
    // absorbs the fallback, so isolated outliers are correctly rejected. Here,
    // NOTHING has a same-side neighbor anywhere in the list — every pairwise gap
    // exceeds 2 semitones — so firstOrNull{hasNeighbor}/lastOrNull{hasNeighbor}
    // both return null and the function falls back to the raw, completely
    // unvalidated min/max. This is the opposite of the function's stated purpose
    // ("prevents a single stray high-confidence frame from claiming the floor or
    // ceiling") — any single stray frame at either extreme becomes the reported
    // extreme with zero corroboration, exactly when the session gives the LEAST
    // reason to trust it.
    //
    // Not fixed: the one architecturally sound fix (treat temporal adjacency —
    // frames close together in TIME — as a second corroboration path alongside
    // value-adjacency, so a genuine fast glissando can be told apart from a
    // single spurious frame) is a rewrite of comparable scope and risk to
    // estimatePassaggio's reversal/median fix. This shape is also rare in
    // practice: it requires a session where the singer never holds a single note
    // anywhere, directly contradicting the Guide's own "hold each note for at
    // least 2-3 seconds" instruction, and YIN's confidence tends to drop during
    // genuinely fast pitch movement anyway. See KNOWN_ISSUES.md.

    @Test
    fun `when nothing in the session has any neighbor, validation is completely bypassed`() {
        val pitches = listOf(100f, 300f, 500f, 900f)
        val (min, max) = FachClassifier.estimateDetectedExtremes(pitches)
        assertEquals(
            "No sample has a same-side neighbor, so validation is bypassed entirely " +
                    "and the raw minimum is returned unvalidated",
            100f, min, 0.01f
        )
        assertEquals(
            "No sample has a same-side neighbor, so validation is bypassed entirely " +
                    "and the raw maximum is returned unvalidated",
            900f, max, 0.01f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. estimatePassaggio — Hz-space variance bias + step-transition bias
    //    (FIXED — regression guard; was KNOWN_ISSUES.md #1, hardened further
    //    after this test also caught a step-transition edge winning outright)
    // ─────────────────────────────────────────────────────────────────────────
    //
    // A baritone's true passaggio sits around D3–E3 (~185–220 Hz). Give the
    // algorithm a real break there (an oscillation of a fixed *semitone* width)
    // plus a same-semitone-width wobble an octave higher (e.g. a vibrato flourish
    // on a passing high note, not a register break) with an unrelated stable
    // block in between. Two bugs, both now fixed in estimatePassaggio:
    //   1. Hz-space variance biased toward the higher-register wobble (~4x more
    //      Hz-variance for the same relative size).
    //   2. Even after fixing (1), plain semitone-space variance still let the
    //      *edge* between the stable middle block and the wobble outscore both
    //      genuine oscillations outright, because variance can't tell a wobble
    //      from a step. Fixed by scoring on median move size (robust to a single
    //      contaminating jump) weighted by direction-reversal count, not variance.

    @Test
    fun `equal-semitone-width wobble in a higher register does not outscore the real passaggio`() {
        val stableBelow = List(15) { 165f }                         // E3, stable
        // Real break: oscillates roughly a whole tone around ~207 Hz (Ab3/G#3)
        val realBreak = List(15) { i -> if (i % 2 == 0) 196f else 220f }
        val stableMiddle = List(15) { 262f }                        // C4, stable
        // Same *relative* wobble one octave higher: ~392–440 Hz — not a real break,
        // just a held trill/vibrato on a passing note.
        val higherWobble = List(15) { i -> if (i % 2 == 0) 392f else 440f }
        val stableAbove = List(15) { 523f }

        val pitches = stableBelow + realBreak + stableMiddle + higherWobble + stableAbove
        val passaggio = FachClassifier.estimatePassaggio(pitches)

        assertTrue(
            "estimatePassaggio picked $passaggio Hz — expected it near the real break " +
                    "(~196-220 Hz), not the higher-register wobble (~392-440 Hz) or the " +
                    "stable-block transition edge between them.",
            passaggio in 150f..260f
        )
    }

    /**
     * Same shape as above but reversed: the spurious wobble comes FIRST and the real
     * break comes LAST, with the stable blocks re-ordered around them. Guards against
     * a fix that only works because the real break happened to be scanned first.
     */
    @Test
    fun `real passaggio wins regardless of where it falls in the session`() {
        val higherWobble = List(15) { i -> if (i % 2 == 0) 392f else 440f }
        val stableMiddle = List(15) { 262f }
        val realBreak = List(15) { i -> if (i % 2 == 0) 196f else 220f }
        val stableBelow = List(15) { 165f }
        val stableAbove = List(15) { 523f }

        val pitches = stableAbove + stableMiddle + higherWobble + stableBelow + realBreak
        val passaggio = FachClassifier.estimatePassaggio(pitches)

        assertTrue(
            "estimatePassaggio picked $passaggio Hz — expected the real break (~196-220 Hz) " +
                    "to win regardless of session order, got a value outside that range",
            passaggio in 150f..260f
        )
    }

    /**
     * A much larger unrelated jump (220 Hz -> 1046 Hz, 24 semitones) than the original
     * case, next to a noisy but genuine soprano-register break (~659-698 Hz). Confirms
     * the median+reversal defense isn't curve-fit to the original example's magnitudes.
     */
    @Test
    fun `real passaggio wins even against a much larger unrelated jump`() {
        val rng = java.util.Random(7L)
        fun jitter() = (rng.nextFloat() - 0.5f) * 2f // +/- 1 Hz
        val sopranoBreak = List(15) { i -> if (i % 2 == 0) 659f + jitter() else 698f + jitter() }

        val pitches = List(15) { 220f } + List(15) { 1046f } + sopranoBreak + List(15) { 110f }
        val passaggio = FachClassifier.estimatePassaggio(pitches)

        assertTrue(
            "estimatePassaggio picked $passaggio Hz — expected the genuine soprano break " +
                    "(~659-698 Hz) to win over the much larger unrelated 220->1046 Hz jump",
            passaggio in 600f..750f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3b. estimatePassaggio — ordinary vibrato defeats the reversal+median fix
    //     (NOT fixed — documented as [Inherent] in KNOWN_ISSUES.md #8; this test
    //     is the permanent record of the finding, previously proven only via a
    //     throwaway Python simulation during the second, separate audit pass)
    // ─────────────────────────────────────────────────────────────────────────
    //
    // The fix above rewards a window for having many direction reversals — but
    // ordinary vocal vibrato IS a small, fast, regularly-alternating pitch
    // oscillation, exactly the shape the algorithm is built to reward. A held
    // note decorated with completely ordinary ±1-semitone vibrato, placed
    // anywhere else in the session, can outscore a genuine register break
    // whose own magnitude is comparable. Verified this is not fixable with any
    // pitch-only signal available today (see KNOWN_ISSUES.md #8 for the three
    // rejected mitigations and why); the mitigation shipped instead was
    // product-side (Guide copy asking the user to sing straight tone), not
    // algorithmic. This test documents the actual, current, accepted behavior —
    // like the estimateDetectedExtremes collapse/bypass tests elsewhere in this
    // file, it is expected to keep passing, not to be "fixed" into failing.

    @Test
    fun `ordinary vibrato on an unrelated note outscores a genuine register break`() {
        val realBreak = List(15) { i -> if (i % 2 == 0) 196f else 220f }   // ~2-semitone break
        // 350 Hz held note decorated with ordinary +/-1-semitone vibrato — not a
        // register break, just a normally-sung sustained note with healthy vibrato.
        val vibratoNote = List(15) { i ->
            val semitoneOffset = if (i % 2 == 0) 1.0 else -1.0
            350f * 2.0.pow(semitoneOffset / 12.0).toFloat()
        }
        val stableBelow = List(15) { 165f }
        val stableMiddle = List(15) { 262f }
        val stableAbove = List(15) { 523f }

        val pitches = stableBelow + realBreak + stableMiddle + vibratoNote + stableAbove
        val passaggio = FachClassifier.estimatePassaggio(pitches)

        assertTrue(
            "Documents KNOWN_ISSUES.md #8: ordinary vibrato (~350 Hz) is expected to win " +
                    "over the genuine register break (~196-220 Hz) here — got $passaggio Hz. " +
                    "If this now fails, the algorithm changed; update KNOWN_ISSUES.md #8 rather " +
                    "than just loosening this assertion.",
            passaggio in 330f..370f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. classify() — degenerate all-zero profile must not crash
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `classify does not crash on a fully degenerate zero-valued profile`() {
        val profile = VoiceProfile(
            detectedMinHz = 0f,
            detectedMaxHz = 0f,
            comfortableLowHz = 0f,
            comfortableHighHz = 0f,
            estimatedPassaggioHz = 0f,
            sampleCount = 0,
            durationSeconds = 0f
        )
        val results = FachClassifier.classify(profile)
        assertTrue("classify() must still return one entry per Fach", results.size == ALL_FACH.size)
        assertTrue("every score must be non-negative", results.all { it.score >= 0 })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. YIN — harmonic-rich tone (a real voice is not a pure sine)
    // ─────────────────────────────────────────────────────────────────────────
    //
    // Every existing YinPitchDetectorTest case uses a pure sine. A sung vowel is
    // never a pure sine — it is a fundamental plus strong harmonics. A detector
    // that only works on pure sines is not actually validated against the input
    // it will really receive. Octave errors (locking onto the 2nd harmonic, or a
    // sub-harmonic) are YIN's best-known failure mode on harmonic-rich material.

    private val SR = 44100f

    private fun harmonicTone(f0: Float, nSamples: Int = 4096): FloatArray = FloatArray(nSamples) { i ->
        val t = i / SR
        (0.5f * sin(2.0 * PI * f0 * t).toFloat() +
                0.35f * sin(2.0 * PI * 2 * f0 * t).toFloat() +
                0.2f * sin(2.0 * PI * 3 * f0 * t).toFloat() +
                0.1f * sin(2.0 * PI * 4 * f0 * t).toFloat())
    }

    @Test
    fun `harmonic-rich tone at typical tenor pitch is not detected an octave off`() {
        val f0 = 220f // A3 — realistic sung fundamental, not falsetto-pure
        val (pitch, confidence) = YinPitchDetector.detect(harmonicTone(f0), SR, 0.15)

        assertTrue("pitch must be positive (got $pitch)", pitch > 0f)
        val ratio = pitch / f0
        assertTrue(
            "Detected $pitch Hz for a $f0 Hz fundamental (ratio ${"%.3f".format(ratio)}) — " +
                    "this looks like an octave error (locked onto a harmonic/sub-harmonic), " +
                    "confidence=$confidence",
            ratio in 0.9f..1.1f
        )
    }

    @Test
    fun `harmonic-rich tone at typical soprano pitch is not detected an octave off`() {
        val f0 = 660f // E5 — realistic soprano tessitura pitch
        val (pitch, confidence) = YinPitchDetector.detect(harmonicTone(f0), SR, 0.15)

        assertTrue("pitch must be positive (got $pitch)", pitch > 0f)
        val ratio = pitch / f0
        assertTrue(
            "Detected $pitch Hz for a $f0 Hz fundamental (ratio ${"%.3f".format(ratio)}) — " +
                    "this looks like an octave error, confidence=$confidence",
            ratio in 0.9f..1.1f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Minimum sample gate boundary — one frame short of the classification gate
    // ─────────────────────────────────────────────────────────────────────────
    //
    // VoiceAnalyzer.stop() gates at `pitchSamples.size < 20`. Confirm the pure
    // classifier functions behave sanely exactly at the boundary the gate is
    // meant to protect (19 vs 20 samples), since a future refactor could easily
    // move the gate check without re-verifying this edge.

    @Test
    fun `19 samples (one below the acceptance gate) still produces a usable profile if called directly`() {
        // The pure functions have no gate of their own — only VoiceAnalyzer.stop() enforces
        // the 20-frame minimum. Calling them directly with 19 samples must not crash;
        // it should just be statistically weak, which is exactly why the gate exists.
        val pitches = List(19) { i -> 200f + i * 5f }
        val (min, max) = FachClassifier.estimateDetectedExtremes(pitches)
        val (low, high) = FachClassifier.estimateComfortableRange(pitches)
        assertTrue("min <= max", min <= max)
        assertTrue("low <= high", low <= high)
    }
}
