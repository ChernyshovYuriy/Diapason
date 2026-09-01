package com.yuriy.diapason.comparison

import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.VoiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ComparisonResult] and [HzDelta].
 *
 * All tests are pure JVM — no Android context required.
 */
class ComparisonResultTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Build a [VoiceProfile] with explicit fields for testing.
     * [sampleCount] defaults to [FachClassifier.PASSAGGIO_MIN_SAMPLES] so passaggio is
     * included by default.
     */
    private fun profile(
        detectedMin: Float = 200f,
        detectedMax: Float = 800f,
        comfortableLow: Float = 250f,
        comfortableHigh: Float = 700f,
        passaggio: Float = 400f,
        sampleCount: Int = FachClassifier.PASSAGGIO_MIN_SAMPLES,
    ) = VoiceProfile(
        detectedMinHz = detectedMin,
        detectedMaxHz = detectedMax,
        comfortableLowHz = comfortableLow,
        comfortableHighHz = comfortableHigh,
        estimatedPassaggioHz = passaggio,
        sampleCount = sampleCount,
        durationSeconds = 30f,
    )

    private fun compute(before: VoiceProfile, after: VoiceProfile) =
        ComparisonResult.compute(
            before = before,
            beforeTopMatch = null,
            after = after,
            afterTopMatch = null,
        )

    // ── HzDelta tests ─────────────────────────────────────────────────────────

    @Test
    fun `deltaHz is positive when after is higher`() {
        val d = HzDelta(beforeHz = 300f, afterHz = 340f)
        assertTrue(d.deltaHz > 0)
        assertEquals(40f, d.deltaHz, 0.01f)
    }

    @Test
    fun `deltaHz is negative when after is lower`() {
        val d = HzDelta(beforeHz = 300f, afterHz = 260f)
        assertTrue(d.deltaHz < 0)
        assertEquals(-40f, d.deltaHz, 0.01f)
    }

    @Test
    fun `isMeaningful is false for sub-semitone changes`() {
        // One semitone ≈ 5.9% ratio. Below that should be false.
        val d = HzDelta(beforeHz = 400f, afterHz = 404f)  // ~1% change
        assertFalse(d.isMeaningful)
    }

    @Test
    fun `isMeaningful is true for changes of one semitone or more`() {
        // One semitone up from 400 Hz ≈ 423.7 Hz
        val d = HzDelta(beforeHz = 400f, afterHz = 425f)
        assertTrue(d.isMeaningful)
    }

    @Test
    fun `isMeaningful is true for clearly larger changes`() {
        val d = HzDelta(beforeHz = 200f, afterHz = 260f)  // 30% change
        assertTrue(d.isMeaningful)
    }

    @Test
    fun `isMeaningful is false when beforeHz is zero`() {
        val d = HzDelta(beforeHz = 0f, afterHz = 300f)
        assertFalse(d.isMeaningful)
    }

    @Test
    fun `isMeaningful is false when beforeHz is negative`() {
        val d = HzDelta(beforeHz = -50f, afterHz = 300f)
        assertFalse(d.isMeaningful)
    }

    // ── Asymmetry fix: a flat percentage is not a symmetric proxy for a semitone ──
    //
    // Found during a second, separate adversarial audit pass. A semitone is a
    // fixed ratio (2^(1/12) ≈ 1.0595), not a fixed percentage, so the old flat
    // +/-5.9% threshold corresponded to different true semitone distances
    // depending on direction. Verified numerically before writing this test: a
    // +5.925% rise is only ~0.997 true semitones (just under a full semitone) —
    // the old formula would have called it "meaningful" anyway, since 5.925% is
    // itself above the flat 5.9% cutoff.

    @Test
    fun `isMeaningful correctly rejects a rise that clears the old flat percentage but not a true semitone`() {
        // 400 -> 423.7 Hz is a +5.925% change (clears the old ">= 5.9%" threshold)
        // but only ~0.997 true semitones (does not clear ">= 1 semitone").
        val d = HzDelta(beforeHz = 400f, afterHz = 423.7f)
        assertFalse(
            "A +5.925% rise is under 1 true semitone (~0.997) and must not be meaningful, " +
                    "even though it clears the old flat 5.9% threshold",
            d.isMeaningful
        )
    }

    // ── ComparisonResult.compute – delta correctness ───────────────────────────

    @Test
    fun `comfortableLow delta reflects before and after values`() {
        val before = profile(comfortableLow = 260f)
        val after = profile(comfortableLow = 240f)
        val result = compute(before, after)

        assertEquals(260f, result.comfortableLow.beforeHz, 0.01f)
        assertEquals(240f, result.comfortableLow.afterHz, 0.01f)
        assertEquals(-20f, result.comfortableLow.deltaHz, 0.01f)
    }

    @Test
    fun `comfortableHigh delta reflects before and after values`() {
        val before = profile(comfortableHigh = 680f)
        val after = profile(comfortableHigh = 750f)
        val result = compute(before, after)

        assertEquals(680f, result.comfortableHigh.beforeHz, 0.01f)
        assertEquals(750f, result.comfortableHigh.afterHz, 0.01f)
        assertEquals(70f, result.comfortableHigh.deltaHz, 0.01f)
    }

    @Test
    fun `detectedMin delta reflects before and after values`() {
        val before = profile(detectedMin = 190f)
        val after = profile(detectedMin = 175f)
        val result = compute(before, after)

        assertEquals(190f, result.detectedMin.beforeHz, 0.01f)
        assertEquals(175f, result.detectedMin.afterHz, 0.01f)
    }

    @Test
    fun `detectedMax delta reflects before and after values`() {
        val before = profile(detectedMax = 800f)
        val after = profile(detectedMax = 860f)
        val result = compute(before, after)

        assertEquals(800f, result.detectedMax.beforeHz, 0.01f)
        assertEquals(860f, result.detectedMax.afterHz, 0.01f)
    }

    // ── Passaggio presence ────────────────────────────────────────────────────

    @Test
    fun `passaggio is present when both sessions have PASSAGGIO_MIN_SAMPLES or more`() {
        val before = profile(passaggio = 380f, sampleCount = FachClassifier.PASSAGGIO_MIN_SAMPLES)
        val after = profile(passaggio = 400f, sampleCount = FachClassifier.PASSAGGIO_MIN_SAMPLES + 15)
        val result = compute(before, after)

        assertNotNull(result.passaggio)
        assertEquals(380f, result.passaggio!!.beforeHz, 0.01f)
        assertEquals(400f, result.passaggio!!.afterHz, 0.01f)
    }

    @Test
    fun `passaggio is null when before session has fewer than PASSAGGIO_MIN_SAMPLES`() {
        val before = profile(sampleCount = FachClassifier.PASSAGGIO_MIN_SAMPLES - 1)
        val after = profile(sampleCount = 50)
        val result = compute(before, after)

        assertNull(result.passaggio)
    }

    @Test
    fun `passaggio is null when after session has fewer than PASSAGGIO_MIN_SAMPLES`() {
        val before = profile(sampleCount = 50)
        val after = profile(sampleCount = 15)
        val result = compute(before, after)

        assertNull(result.passaggio)
    }

    @Test
    fun `passaggio is null when both sessions are below PASSAGGIO_MIN_SAMPLES`() {
        val before = profile(sampleCount = 20)
        val after = profile(sampleCount = 22)
        val result = compute(before, after)

        assertNull(result.passaggio)
    }

    // ── Comfortable range widening flags ──────────────────────────────────────

    @Test
    fun `comfortableRangeWidened is true when high boundary rises meaningfully`() {
        val before = profile(comfortableLow = 260f, comfortableHigh = 680f)
        val after = profile(comfortableLow = 260f, comfortableHigh = 750f)
        val result = compute(before, after)

        assertTrue(result.comfortableRangeWidened)
    }

    @Test
    fun `comfortableRangeWidened is true when low boundary drops meaningfully`() {
        val before = profile(comfortableLow = 260f, comfortableHigh = 680f)
        val after = profile(comfortableLow = 230f, comfortableHigh = 680f)
        val result = compute(before, after)

        assertTrue(result.comfortableRangeWidened)
    }

    @Test
    fun `comfortableRangeWidened is false for sub-semitone changes`() {
        val before = profile(comfortableLow = 260f, comfortableHigh = 680f)
        // Both boundaries move by less than one semitone (~1-2 Hz)
        val after = profile(comfortableLow = 261f, comfortableHigh = 681f)
        val result = compute(before, after)

        assertFalse(result.comfortableRangeWidened)
    }

    @Test
    fun `comfortableRangeWidened is false when high boundary drops meaningfully`() {
        val before = profile(comfortableLow = 260f, comfortableHigh = 680f)
        val after  = profile(comfortableLow = 260f, comfortableHigh = 620f)  // high drops ~1.5 semitones
        val result = compute(before, after)

        assertFalse(result.comfortableRangeWidened)
    }

    // ── Detected range widening flags ─────────────────────────────────────────

    @Test
    fun `detectedRangeWidened is true when ceiling rises meaningfully`() {
        val before = profile(detectedMin = 180f, detectedMax = 820f)
        val after = profile(detectedMin = 180f, detectedMax = 900f)
        val result = compute(before, after)

        assertTrue(result.detectedRangeWidened)
    }

    @Test
    fun `detectedRangeWidened is false when no meaningful change`() {
        val before = profile(detectedMin = 200f, detectedMax = 800f)
        val after = profile(detectedMin = 201f, detectedMax = 801f)
        val result = compute(before, after)

        assertFalse(result.detectedRangeWidened)
    }

    @Test
    fun `detectedRangeWidened is false when ceiling drops meaningfully`() {
        val before = profile(detectedMin = 180f, detectedMax = 820f)
        val after  = profile(detectedMin = 180f, detectedMax = 750f)  // ceiling drops ~1.5 semitones
        val result = compute(before, after)

        assertFalse(result.detectedRangeWidened)
    }

    // ── compute assigns all four range fields to the correct HzDelta slots ────

    @Test
    fun `compute assigns all four range fields without swapping`() {
        // Use obviously distinct values so any field swap produces a readable failure.
        val before = profile(detectedMin = 80f, detectedMax = 1200f, comfortableLow = 150f, comfortableHigh = 900f)
        val after  = profile(detectedMin = 70f, detectedMax = 1300f, comfortableLow = 130f, comfortableHigh = 950f)
        val result = compute(before, after)

        assertEquals(80f,   result.detectedMin.beforeHz,    0.01f)
        assertEquals(1200f, result.detectedMax.beforeHz,    0.01f)
        assertEquals(150f,  result.comfortableLow.beforeHz,  0.01f)
        assertEquals(900f,  result.comfortableHigh.beforeHz, 0.01f)

        assertEquals(70f,   result.detectedMin.afterHz,     0.01f)
        assertEquals(1300f, result.detectedMax.afterHz,     0.01f)
        assertEquals(130f,  result.comfortableLow.afterHz,   0.01f)
        assertEquals(950f,  result.comfortableHigh.afterHz,  0.01f)
    }

    // ── compute returns correct profile references ─────────────────────────────

    @Test
    fun `compute stores before and after profiles by reference`() {
        val before = profile(detectedMin = 150f)
        val after = profile(detectedMin = 160f)
        val result = compute(before, after)

        assertEquals(before, result.before)
        assertEquals(after, result.after)
    }

    @Test
    fun `compute with null matches stores null top matches`() {
        val result = ComparisonResult.compute(
            before = profile(),
            beforeTopMatch = null,
            after = profile(),
            afterTopMatch = null,
        )
        assertNull(result.beforeTopMatch)
        assertNull(result.afterTopMatch)
    }

    // ── Symmetric identity: same session produces zero deltas ─────────────────

    @Test
    fun `same profile before and after produces zero deltas`() {
        val p = profile(
            detectedMin = 200f, detectedMax = 800f,
            comfortableLow = 250f, comfortableHigh = 700f,
            passaggio = 420f, sampleCount = 40,
        )
        val result = compute(p, p)

        assertEquals(0f, result.comfortableLow.deltaHz, 0.01f)
        assertEquals(0f, result.comfortableHigh.deltaHz, 0.01f)
        assertEquals(0f, result.detectedMin.deltaHz, 0.01f)
        assertEquals(0f, result.detectedMax.deltaHz, 0.01f)
        assertFalse(result.comfortableRangeWidened)
        assertFalse(result.detectedRangeWidened)
        assertNotNull(result.passaggio)
        assertEquals(0f, result.passaggio!!.deltaHz, 0.01f)
        assertFalse(result.passaggio!!.isMeaningful)
    }
}
