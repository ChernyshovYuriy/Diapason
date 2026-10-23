package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [VoiceGroupCheck] — the profiles are the per-group medians from replaying 2.7.0 sessions
 * (`CombinedProfileReplayTest`): the takes that won countertenor under Male and contralto
 * under Female, and typical takes that fit the group they were recorded with.
 */
class VoiceGroupCheckTest {

    private fun profile(min: Float, max: Float, low: Float, high: Float, passaggio: Float) = VoiceProfile(
        detectedMinHz = min,
        detectedMaxHz = max,
        comfortableLowHz = low,
        comfortableHighHz = high,
        estimatedPassaggioHz = passaggio,
        sampleCount = 120,
        durationSeconds = 40f,
    )

    /** "Male" takes that won countertenor: A3–A#5, comfortable F#4–F#5, passaggio D5. */
    private val femaleRangeTake = profile(220f, 932f, 370f, 740f, 587f)

    /** "Female" takes that won contralto: C3–B4, comfortable G3–E4, passaggio A#3. */
    private val tenorRangeTake = profile(131f, 494f, 196f, 330f, 233f)

    /** Lyric baritone: A#2–F4, comfortable D3–C4, passaggio G3. */
    private val baritoneTake = profile(117f, 349f, 147f, 262f, 196f)

    /** Lyric soprano: B3–C5, comfortable D4–G#4, passaggio E4. */
    private val sopranoTake = profile(247f, 523f, 294f, 415f, 330f)

    @Test
    fun `a soprano-range take recorded as Male suggests Female`() {
        assertEquals(VoiceGroupChoice.FEMALE, VoiceGroupCheck.betterFittingChoice(femaleRangeTake, VoiceGroupChoice.MALE))
    }

    @Test
    fun `a tenor-range take recorded as Female suggests Male`() {
        assertEquals(VoiceGroupChoice.MALE, VoiceGroupCheck.betterFittingChoice(tenorRangeTake, VoiceGroupChoice.FEMALE))
    }

    @Test
    fun `takes that fit their own group raise nothing`() {
        assertNull(VoiceGroupCheck.betterFittingChoice(baritoneTake, VoiceGroupChoice.MALE))
        assertNull(VoiceGroupCheck.betterFittingChoice(sopranoTake, VoiceGroupChoice.FEMALE))
    }

    @Test
    fun `Not sure is never checked — it already ranks all 19 Fach`() {
        listOf(femaleRangeTake, tenorRangeTake, baritoneTake, sopranoTake).forEach {
            assertNull(VoiceGroupCheck.betterFittingChoice(it, VoiceGroupChoice.UNSURE))
        }
    }

    @Test
    fun `a suggestion always means the other group leads by at least the margin`() {
        listOf(femaleRangeTake, tenorRangeTake, baritoneTake, sopranoTake).forEach { take ->
            listOf(VoiceGroupChoice.MALE, VoiceGroupChoice.FEMALE).forEach { choice ->
                val own = FachClassifier.classify(take, choice.group).first().score
                val otherGroup = if (choice.group == VoiceGroup.MALE) VoiceGroup.FEMALE else VoiceGroup.MALE
                val other = FachClassifier.classify(take, otherGroup).first().score
                val suggested = VoiceGroupCheck.betterFittingChoice(take, choice)
                assertEquals(
                    "own $own vs other $other for $choice",
                    other - own >= VoiceGroupCheck.MARGIN,
                    suggested != null,
                )
            }
        }
    }
}
