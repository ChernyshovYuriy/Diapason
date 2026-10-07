package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoiceProfileAggregator]: the last 5 sessions within 30 days, combined by
 * per-dimension median (window chosen by the author, 2026-10-07).
 */
class VoiceProfileAggregatorTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun profile(
        min: Float = 100f, max: Float = 400f, low: Float = 130f, high: Float = 330f,
        pass: Float = 300f, samples: Int = 40, duration: Float = 20f,
    ) = VoiceProfile(min, max, low, high, pass, samples, duration)

    private fun at(daysAgo: Long, p: VoiceProfile = profile()) = TimedProfile(now - daysAgo * day, p)

    private fun fachByRange(minHz: Float, maxHz: Float): FachDefinition =
        ALL_FACH.first { it.rangeMinHz == minHz && it.rangeMaxHz == maxHz }

    private fun perfectProfileFor(fach: FachDefinition) = VoiceProfile(
        fach.rangeMinHz, fach.rangeMaxHz, fach.tessituraMinHz, fach.tessituraMaxHz,
        fach.passaggioHz, 60, 30f,
    )

    // ── selectRecent ──────────────────────────────────────────────────────────

    @Test
    fun `selectRecent keeps at most the newest five sessions`() {
        val sessions = (0L..7L).map { at(it, profile(samples = 100 + it.toInt())) }
        val selected = VoiceProfileAggregator.selectRecent(sessions.shuffled(), now)
        assertEquals(5, selected.size)
        assertEquals(listOf(100, 101, 102, 103, 104), selected.map { it.sampleCount })
    }

    @Test
    fun `selectRecent drops sessions older than 30 days even when fewer than five remain`() {
        val sessions = listOf(at(1), at(29), at(30), at(31), at(90))
        val selected = VoiceProfileAggregator.selectRecent(sessions, now)
        assertEquals("1, 29 and exactly-30 days ago are in; 31 and 90 are out", 3, selected.size)
    }

    // ── medianProfile ─────────────────────────────────────────────────────────

    @Test
    fun `medianProfile takes the middle value of each dimension independently`() {
        val combined = VoiceProfileAggregator.medianProfile(
            listOf(
                profile(min = 90f, max = 500f, pass = 310f),
                profile(min = 100f, max = 400f, pass = 290f),
                profile(min = 300f, max = 410f, pass = 300f),   // one unusual take
            )
        )
        assertEquals(100f, combined.detectedMinHz, 0f)
        assertEquals(410f, combined.detectedMaxHz, 0f)
        assertEquals(300f, combined.estimatedPassaggioHz, 0f)
    }

    @Test
    fun `medianProfile averages the two middle values for an even count`() {
        val combined = VoiceProfileAggregator.medianProfile(
            listOf(profile(min = 100f), profile(min = 110f), profile(min = 120f), profile(min = 200f))
        )
        assertEquals(115f, combined.detectedMinHz, 0.001f)
    }

    @Test
    fun `medianProfile sums sample counts and durations`() {
        val combined = VoiceProfileAggregator.medianProfile(
            listOf(profile(samples = 40, duration = 20f), profile(samples = 60, duration = 25f))
        )
        assertEquals(100, combined.sampleCount)
        assertEquals(45f, combined.durationSeconds, 0.001f)
    }

    // ── combine ───────────────────────────────────────────────────────────────

    @Test
    fun `combine needs at least two recent sessions`() {
        assertNull(VoiceProfileAggregator.combine(emptyList(), now, null))
        assertNull(VoiceProfileAggregator.combine(listOf(at(0)), now, null))
        assertNull(
            "one recent plus one outside the window is still only one",
            VoiceProfileAggregator.combine(listOf(at(0), at(45)), now, null)
        )
        assertNotNull(VoiceProfileAggregator.combine(listOf(at(0), at(2)), now, null))
    }

    @Test
    fun `combine damps a single unusual take`() {
        val lyricBaritone = fachByRange(110f, 392f)
        val usual = perfectProfileFor(lyricBaritone)
        val unusual = perfectProfileFor(fachByRange(65f, 294f))   // Basso profundo
        val sessions = listOf(at(0, unusual), at(1, usual), at(2, usual), at(3, usual))

        val combined = VoiceProfileAggregator.combine(sessions, now, VoiceGroup.MALE)!!

        assertEquals(lyricBaritone.categoryRes, combined.summary.familyRes)
        assertEquals(4, combined.sessionCount)
        assertEquals("the profundo take lands in another family", 3, combined.consistentCount)
    }

    @Test
    fun `combine classifies within the given group`() {
        val sessions = listOf(at(0), at(1), at(2))
        val female = VoiceProfileAggregator.combine(sessions, now, VoiceGroup.FEMALE)!!
        assertTrue(female.matches.all { it.fach.voiceGroup == VoiceGroup.FEMALE })
    }
}
