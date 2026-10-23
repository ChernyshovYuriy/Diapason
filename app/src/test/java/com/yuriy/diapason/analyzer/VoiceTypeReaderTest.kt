package com.yuriy.diapason.analyzer

import com.yuriy.diapason.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [VoiceTypeReader] — a family when the range is clear, "between" two when it isn't. */
class VoiceTypeReaderTest {

    private fun fach(nameRes: Int) = ALL_FACH.first { it.nameRes == nameRes }
    private val lyricSoprano get() = fach(R.string.fach_name_lyric_soprano)
    private val spintoSoprano get() = fach(R.string.fach_name_spinto_soprano)
    private val dramaticSoprano get() = fach(R.string.fach_name_dramatic_soprano)
    private val lyricMezzo get() = fach(R.string.fach_name_lyric_mezzo_soprano)
    private val contralto get() = fach(R.string.fach_name_contralto)

    private fun match(fach: FachDefinition, score: Int) = FachMatch(fach = fach, score = score, scoreBreakdown = emptyList())

    @Test
    fun `an empty ranking has no reading`() {
        assertNull(VoiceTypeReader.read(emptyList()))
    }

    @Test
    fun `a family that leads every other by the margin is named alone`() {
        val reading = VoiceTypeReader.read(
            listOf(match(lyricSoprano, 12), match(spintoSoprano, 11), match(lyricMezzo, 12 - VoiceTypeReader.FAMILY_MARGIN)),
        )!!
        assertEquals(listOf(R.string.fach_category_soprano), reading.familyRes)
        assertFalse(reading.isBetween)
    }

    @Test
    fun `a closer second family makes the reading 'between', higher voice first`() {
        // Mezzo leads, but soprano is within the margin: listed soprano → mezzo, as a teacher says it.
        val reading = VoiceTypeReader.read(
            listOf(match(lyricMezzo, 12), match(lyricSoprano, 12 - VoiceTypeReader.FAMILY_MARGIN + 1), match(contralto, 6)),
        )!!
        assertEquals(listOf(R.string.fach_category_soprano, R.string.fach_category_mezzo_soprano), reading.familyRes)
        assertTrue(reading.isBetween)
    }

    @Test
    fun `Fach types within a point of the best are listed, best first, at most three`() {
        val reading = VoiceTypeReader.read(
            listOf(
                match(lyricSoprano, 12), match(spintoSoprano, 11), match(dramaticSoprano, 11),
                match(lyricMezzo, 11), match(contralto, 10),
            ),
        )!!
        assertEquals(listOf(lyricSoprano, spintoSoprano, dramaticSoprano), reading.fachTypes)
    }

    @Test
    fun `a Fach two points behind is not listed`() {
        val reading = VoiceTypeReader.read(listOf(match(lyricSoprano, 12), match(spintoSoprano, 10)))!!
        assertEquals(listOf(lyricSoprano), reading.fachTypes)
    }

    @Test
    fun `a real classification always lists its best Fach first`() {
        val profile = VoiceProfile(247f, 523f, 294f, 415f, 330f, 120, 40f)
        val matches = FachClassifier.classify(profile, VoiceGroup.FEMALE)
        val reading = VoiceTypeReader.read(matches)!!
        assertEquals(matches.first().fach, reading.fachTypes.first())
        assertTrue(matches.first().fach.categoryRes in reading.familyRes)
    }
}
