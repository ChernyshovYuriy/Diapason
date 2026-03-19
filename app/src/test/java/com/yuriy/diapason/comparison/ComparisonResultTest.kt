package com.yuriy.diapason.comparison

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
     * [sampleCount] defaults to 30 so passaggio is included by default.
     */
    private fun profile(
        detectedMin: Float = 200f,
        detectedMax: Float = 800f,
        comfortableLow: Float = 250f,
        comfortableHigh: Float = 700f,
        passaggio: Float = 400f,
        sampleCount: Int = 30,
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
    fun `passaggio is present when both sessions have 30 or more samples`() {
        val before = profile(passaggio = 380f, sampleCount = 30)
        val after = profile(passaggio = 400f, sampleCount = 45)
        val result = compute(before, after)

        assertNotNull(result.passaggio)
        assertEquals(380f, result.passaggio!!.beforeHz, 0.01f)
        assertEquals(400f, result.passaggio!!.afterHz, 0.01f)
    }

    @Test
    fun `passaggio is null when before session has fewer than 30 samples`() {
        val before = profile(sampleCount = 29)
        val after = profile(sampleCount = 50)
        val result = compute(before, after)

        assertNull(result.passaggio)
    }

    @Test
    fun `passaggio is null when after session has fewer than 30 samples`() {
        val before = profile(sampleCount = 50)
        val after = profile(sampleCount = 15)
        val result = compute(before, after)

        assertNull(result.passaggio)
    }

    @Test
    fun `passaggio is null when both sessions are below 30 samples`() {
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

    // ── Invariant: comfortable range must stay within detected extremes ────────

    @Test
    fun `comfortable range never exceeds detected extremes in before profile`() {
        val before = profile(
            detectedMin = 200f, detectedMax = 800f,
            comfortableLow = 250f, comfortableHigh = 700f,
        )
        assertTrue(before.comfortableLowHz >= before.detectedMinHz)
        assertTrue(before.comfortableHighHz <= before.detectedMaxHz)
    }

    @Test
    fun `comfortable range never exceeds detected extremes in after profile`() {
        val after = profile(
            detectedMin = 185f, detectedMax = 860f,
            comfortableLow = 240f, comfortableHigh = 780f,
        )
        assertTrue(after.comfortableLowHz >= after.detectedMinHz)
        assertTrue(after.comfortableHighHz <= after.detectedMaxHz)
    }

    /**
     * Construct arbitrary before/after pairs and verify the invariant holds.
     * This is a property-like sanity check using a small hand-written table.
     */
    @Test
    fun `comfortable range invariant holds across multiple profile pairs`() {
        data class Case(
            val detMin: Float, val detMax: Float,
            val comfLow: Float, val comfHigh: Float,
        )
        listOf(
            Case(100f, 1000f, 200f, 800f),
            Case(65f, 880f, 130f, 700f),
            Case(260f, 1047f, 349f, 880f),
        ).forEach { c ->
            val p = profile(
                detectedMin = c.detMin, detectedMax = c.detMax,
                comfortableLow = c.comfLow, comfortableHigh = c.comfHigh,
            )
            assertTrue(
                "comfortable low ${p.comfortableLowHz} < detected min ${p.detectedMinHz}",
                p.comfortableLowHz >= p.detectedMinHz
            )
            assertTrue(
                "comfortable high ${p.comfortableHighHz} > detected max ${p.detectedMaxHz}",
                p.comfortableHighHz <= p.detectedMaxHz
            )
        }
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
