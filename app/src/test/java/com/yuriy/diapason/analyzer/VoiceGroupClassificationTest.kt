package com.yuriy.diapason.analyzer

import com.yuriy.diapason.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Voice-group (male/female) classification and the family-first [ResultSummary].
 *
 * Background: in production, 28% of consecutive takes by the same install crossed
 * between the male and female halves of the table — always between range-overlapping
 * pairs (contralto ↔ lyric tenor was the most common). Pitch can't separate those, so
 * the user states the group once and [FachClassifier.classify] ranks only that half.
 */
class VoiceGroupClassificationTest {

    private val female = setOf(
        R.string.fach_category_soprano,
        R.string.fach_category_mezzo_soprano,
        R.string.fach_category_contralto,
    )

    private fun fachByRange(minHz: Float, maxHz: Float): FachDefinition =
        ALL_FACH.first { it.rangeMinHz == minHz && it.rangeMaxHz == maxHz }

    private val contralto get() = fachByRange(165f, 698f)
    private val lyricTenor get() = fachByRange(130f, 523f)
    private val countertenor get() = ALL_FACH.first { it.categoryRes == R.string.fach_category_countertenor }

    private fun perfectProfileFor(fach: FachDefinition) = VoiceProfile(
        detectedMinHz = fach.rangeMinHz,
        detectedMaxHz = fach.rangeMaxHz,
        comfortableLowHz = fach.tessituraMinHz,
        comfortableHighHz = fach.tessituraMaxHz,
        estimatedPassaggioHz = fach.passaggioHz,
        sampleCount = 60,
        durationSeconds = 30f,
    )

    private fun match(fach: FachDefinition, score: Int) =
        FachMatch(fach = fach, score = score, scoreBreakdown = emptyList())

    // ── FachData: every Fach belongs to the right half ───────────────────────

    @Test
    fun `soprano, mezzo and contralto Fach are FEMALE, every other Fach is MALE`() {
        ALL_FACH.forEach { fach ->
            val expected = if (fach.categoryRes in female) VoiceGroup.FEMALE else VoiceGroup.MALE
            assertEquals("Fach with category ${fach.categoryRes}", expected, fach.voiceGroup)
        }
    }

    @Test
    fun `countertenor is a male Fach despite its female-like range`() {
        assertEquals(VoiceGroup.MALE, countertenor.voiceGroup)
    }

    @Test
    fun `the table splits into 8 female and 11 male Fach`() {
        assertEquals(8, ALL_FACH.count { it.voiceGroup == VoiceGroup.FEMALE })
        assertEquals(11, ALL_FACH.count { it.voiceGroup == VoiceGroup.MALE })
    }

    // ── classify(profile, group) ──────────────────────────────────────────────

    @Test
    fun `classify with a group ranks only that group's Fach`() {
        val profile = perfectProfileFor(contralto)
        VoiceGroup.entries.forEach { group ->
            val matches = FachClassifier.classify(profile, group)
            assertEquals(ALL_FACH.count { it.voiceGroup == group }, matches.size)
            assertTrue(matches.all { it.fach.voiceGroup == group })
        }
    }

    @Test
    fun `classify without a group still ranks all 19 Fach`() {
        assertEquals(ALL_FACH.size, FachClassifier.classify(perfectProfileFor(contralto)).size)
        assertEquals(ALL_FACH.size, FachClassifier.classify(perfectProfileFor(contralto), null).size)
    }

    @Test
    fun `a contralto profile can no longer be ranked lyric tenor when the singer is female`() {
        // The #2 most frequent take-to-take flip in production: contralto <-> lyric tenor.
        val tenorProfile = perfectProfileFor(lyricTenor)
        val top = FachClassifier.classify(tenorProfile, VoiceGroup.FEMALE).first()
        assertEquals(VoiceGroup.FEMALE, top.fach.voiceGroup)
    }

    @Test
    fun `every Fach's own perfect profile shares the top score within its own group`() {
        // Not necessarily 14: Contrabass Oktavist's floor sits below MIN_PITCH_HZ, so its
        // own floor deliberately scores "inconclusive" (+2) — KNOWN_ISSUES.md #7.
        ALL_FACH.forEach { fach ->
            val matches = FachClassifier.classify(perfectProfileFor(fach), fach.voiceGroup)
            val own = matches.first { it.fach == fach }
            assertEquals("Fach ${fach.nameRes} must rank top", matches.first().score, own.score)
        }
    }

    @Test
    fun `filtering by group never changes an individual Fach's score`() {
        val profile = perfectProfileFor(contralto)
        val all = FachClassifier.classify(profile).associate { it.fach to it.score }
        VoiceGroup.entries.forEach { group ->
            FachClassifier.classify(profile, group).forEach { m ->
                assertEquals(all.getValue(m.fach), m.score)
            }
        }
    }

    // ── summarize ─────────────────────────────────────────────────────────────

    @Test
    fun `summarize of an empty list is null`() {
        assertNull(FachClassifier.summarize(emptyList()))
    }

    @Test
    fun `summarize leads with the top match's family`() {
        val summary = FachClassifier.summarize(listOf(match(lyricTenor, 12), match(contralto, 8)))!!
        assertEquals(lyricTenor.categoryRes, summary.familyRes)
        assertEquals(lyricTenor, summary.leaning.fach)
    }

    @Test
    fun `a runner-up within one point is reported as close`() {
        val summary = FachClassifier.summarize(listOf(match(lyricTenor, 12), match(contralto, 11)))!!
        assertNotNull(summary.closeRunnerUp)
        assertEquals(contralto, summary.closeRunnerUp!!.fach)
    }

    @Test
    fun `a tied runner-up is reported as close`() {
        val summary = FachClassifier.summarize(listOf(match(lyricTenor, 12), match(contralto, 12)))!!
        assertEquals(contralto, summary.closeRunnerUp?.fach)
    }

    @Test
    fun `a runner-up two points behind is not reported`() {
        val summary = FachClassifier.summarize(listOf(match(lyricTenor, 12), match(contralto, 10)))!!
        assertNull(summary.closeRunnerUp)
    }

    @Test
    fun `a single match has no runner-up`() {
        assertNull(FachClassifier.summarize(listOf(match(lyricTenor, 12)))!!.closeRunnerUp)
    }
}
