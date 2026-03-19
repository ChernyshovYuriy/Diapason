package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Stress tests for [FachClassifier.estimateDetectedExtremes].
 *
 * The function was rewritten from O(n²) to O(log n) per candidate.  These tests
 * verify two things:
 *
 *  1. CORRECTNESS — the new implementation produces identical results to the
 *     reference O(n²) implementation across a wide range of synthetic inputs.
 *     The reference is kept inline so it is readable and obviously correct
 *     even if the production code changes again in the future.
 *
 *  2. PERFORMANCE — on a worst-case 3 600-sample session the new implementation
 *     finishes in well under 50 ms.  The old O(n²) implementation took ~300 ms
 *     on the same input on a typical test machine.
 *
 * Correctness vectors exercised:
 *  - All identical values (every element is its own duplicate neighbour)
 *  - Dense cluster: all values within one semitone
 *  - One isolated outlier at each extreme
 *  - Two isolated outliers (both rejected)
 *  - Realistic ascending scale session
 *  - Realistic session with genuine low and high clusters
 *  - Very sparse (4-element) boundary case
 *  - Boundary: pitches.size < 4 falls back to raw min/max
 */
class EstimateDetectedExtremesStressTest {

    // ── Reference implementation (original O(n²) algorithm) ──────────────────

    private fun referenceDetectedExtremes(pitches: List<Float>): Pair<Float, Float> {
        if (pitches.size < 4) {
            return Pair(pitches.minOrNull() ?: 0f, pitches.maxOrNull() ?: 0f)
        }
        val sorted = pitches.sorted()
        val twoSemitones = 1.1225f
        fun hasNeighbor(candidate: Float): Boolean =
            sorted.count { other ->
                val ratio = if (other >= candidate) other / candidate else candidate / other
                ratio <= twoSemitones
            } >= 2

        val stableMin = sorted.firstOrNull { hasNeighbor(it) } ?: sorted.first()
        val stableMax = sorted.lastOrNull { hasNeighbor(it) } ?: sorted.last()
        return Pair(stableMin, stableMax)
    }

    // ── Tolerance ─────────────────────────────────────────────────────────────

    private val eps = 0.001f  // float precision tolerance

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Asserts that production and reference produce the same result for [pitches]. */
    private fun assertSameAsReference(label: String, pitches: List<Float>) {
        val (prodMin, prodMax) = FachClassifier.estimateDetectedExtremes(pitches)
        val (refMin, refMax) = referenceDetectedExtremes(pitches)
        assertEquals("$label: min mismatch (prod=$prodMin ref=$refMin)", refMin, prodMin, eps)
        assertEquals("$label: max mismatch (prod=$prodMax ref=$refMax)", refMax, prodMax, eps)
    }

    // ── Correctness tests ─────────────────────────────────────────────────────

    @Test
    fun `all identical values — every element has a duplicate neighbour`() {
        val pitches = List(50) { 440f }
        assertSameAsReference("all identical", pitches)
        val (min, max) = FachClassifier.estimateDetectedExtremes(pitches)
        assertEquals(440f, min, eps)
        assertEquals(440f, max, eps)
    }

    @Test
    fun `dense cluster within one semitone — all elements qualify`() {
        // Values from 440 Hz to 466 Hz, ratio = 466/440 ≈ 1.059 < 1.1225
        val pitches = (0..30).map { i -> 440f + i.toFloat() }
        assertSameAsReference("dense cluster", pitches)
    }

    @Test
    fun `one isolated high outlier is rejected by both implementations`() {
        val pitches = List(30) { 330f } + listOf(1760f)  // A6 — no neighbour
        assertSameAsReference("isolated high outlier", pitches)
        val (_, max) = FachClassifier.estimateDetectedExtremes(pitches)
        assertTrue("Isolated high outlier should not become detected max", max < 400f)
    }

    @Test
    fun `one isolated low outlier is rejected by both implementations`() {
        val pitches = listOf(65f) + List(30) { 440f }    // C2 — no neighbour
        assertSameAsReference("isolated low outlier", pitches)
        val (min, _) = FachClassifier.estimateDetectedExtremes(pitches)
        assertTrue("Isolated low outlier should not become detected min", min > 400f)
    }

    @Test
    fun `two isolated outliers at both extremes — both rejected`() {
        val pitches = listOf(55f) + List(30) { 330f } + listOf(2000f)
        assertSameAsReference("two isolated outliers", pitches)
        val (min, max) = FachClassifier.estimateDetectedExtremes(pitches)
        assertTrue("Low outlier (55 Hz) should be rejected, got $min", min > 200f)
        assertTrue("High outlier (2000 Hz) should be rejected, got $max", max < 500f)
    }

    @Test
    fun `two adjacent high samples qualify as the detected max`() {
        // 880 Hz and 932 Hz: ratio = 932/880 ≈ 1.059 — within 2 semitones
        val pitches = List(30) { 330f } + listOf(880f, 932f)
        assertSameAsReference("adjacent high pair", pitches)
        val (_, max) = FachClassifier.estimateDetectedExtremes(pitches)
        assertTrue("Adjacent high pair should qualify as detected max", max > 900f)
    }

    @Test
    fun `ascending scale session — each note has an adjacent neighbour`() {
        // Chromatic scale G3 to G4 in 50-cent steps, 3 samples per note
        val notes = listOf(
            196f, 208f, 220f, 233f, 247f, 261f, 277f, 294f, 311f, 330f,
            349f, 370f, 392f
        )
        val pitches = notes.flatMap { listOf(it, it, it) }
        assertSameAsReference("ascending scale", pitches)
    }

    @Test
    fun `typical vocal session with genuine floor and ceiling clusters`() {
        // C4 cluster at the bottom, B5 cluster at the top, C5 bulk in the middle
        val pitches = List(3) { 261f } + List(3) { 277f } +  // C4–C#4
                List(20) { 523f } +                      // C5 middle
                List(3) { 932f } + List(3) { 988f }    // A#5–B5
        assertSameAsReference("genuine floor and ceiling", pitches)
        val (min, max) = FachClassifier.estimateDetectedExtremes(pitches)
        assertTrue("Floor cluster (C4) should be detected min", min < 280f)
        assertTrue("Ceiling cluster (B5) should be detected max", max > 900f)
    }

    @Test
    fun `sparse boundary case with exactly 4 elements`() {
        val pitches = listOf(200f, 220f, 440f, 460f)
        assertSameAsReference("sparse 4-element", pitches)
    }

    @Test
    fun `below-threshold boundary case falls back to raw min and max`() {
        // size < 4 → raw fallback; both implementations must agree
        for (size in 0..3) {
            val pitches = List(size) { i -> (100f + i * 100f) }
            assertSameAsReference("size=$size fallback", pitches)
        }
    }

    // ── Exhaustive random correctness sweep ───────────────────────────────────

    /**
     * Runs the production and reference implementations on 500 randomly-shaped
     * pitch lists and asserts they agree on every input.  This catches any
     * edge case in the binary-search logic that the hand-crafted vectors above
     * might have missed.
     */
    @Test
    fun `production matches reference on 500 randomly shaped pitch lists`() {
        val rng = java.util.Random(0x1A2B3C4DL)

        repeat(500) { trial ->
            val size = 4 + rng.nextInt(200)   // 4..203 samples
            val base = 100f + rng.nextFloat() * 800f
            val pitches = List(size) {
                // Mostly within a 2-octave band, occasional outlier
                if (rng.nextFloat() < 0.05f)
                    base * (0.1f + rng.nextFloat() * 5f)  // outlier
                else
                    base * (0.8f + rng.nextFloat() * 0.4f)  // normal
            }

            assertSameAsReference("trial=$trial size=$size", pitches)
        }
    }

    // ── Performance test ──────────────────────────────────────────────────────

    /**
     * Simulates a 10-minute recording session: ~3 600 samples.
     *
     * The old O(n²) implementation performed ~26 M float comparisons for this
     * input.  The new O(log n) implementation performs ~240.  This test enforces
     * that the call completes within a generous 50 ms budget, which is still
     * fast enough to feel instant from the user's perspective.
     *
     * If this test fails it means [estimateDetectedExtremes] has regressed to a
     * slower algorithm and the main thread stall will be noticeable at stop time.
     */
    @Test
    fun `3600 sample session completes in under 50ms`() {
        // Realistic: notes drawn from a 2-octave range with a few outliers
        val rng = java.util.Random(42L)
        val pitches = List(3600) {
            if (rng.nextFloat() < 0.02f) 1800f  // occasional stray high
            else 200f + rng.nextFloat() * 600f
        }

        val elapsed = measureTimeMillis {
            repeat(10) { FachClassifier.estimateDetectedExtremes(pitches) }
        } / 10.0

        assertTrue(
            "estimateDetectedExtremes on 3 600 samples took ${elapsed.toLong()} ms (limit: 50 ms). " +
                    "The algorithm may have regressed to O(n²). Check hasNeighbor in FachClassifier.",
            elapsed < 50.0
        )
    }

    /**
     * Worst case for binary search: every sample is a different power of two,
     * so no candidate has any neighbour — stableMin and stableMax both fall
     * back to sorted.first() and sorted.last().  Both implementations must
     * agree and neither should be slow.
     */
    @Test
    fun `all-isolated worst case produces same result as reference`() {
        // Every sample is more than 2 semitones from its neighbours
        val pitches = List(200) { i -> 100f * Math.pow(1.2, i.toDouble()).toFloat() }

        val elapsed = measureTimeMillis {
            repeat(50) { FachClassifier.estimateDetectedExtremes(pitches) }
        }

        assertSameAsReference("all-isolated worst case", pitches)
        assertTrue(
            "All-isolated 200-sample case: 50 calls took ${elapsed} ms (limit: 200 ms)",
            elapsed < 200L
        )
    }
}
