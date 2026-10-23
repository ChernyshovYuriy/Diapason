package com.yuriy.diapason.analyzer

import com.yuriy.diapason.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFamilyRangesTest {

    @Test
    fun `each group lists its own families, highest first`() {
        assertEquals(
            listOf(R.string.fach_category_soprano, R.string.fach_category_mezzo_soprano, R.string.fach_category_contralto),
            VoiceFamilyRanges.of(VoiceGroup.FEMALE).map { it.categoryRes },
        )
        assertEquals(
            listOf(
                R.string.fach_category_countertenor, R.string.fach_category_tenor,
                R.string.fach_category_baritone, R.string.fach_category_bass,
            ),
            VoiceFamilyRanges.of(VoiceGroup.MALE).map { it.categoryRes },
        )
        assertEquals(7, VoiceFamilyRanges.of(null).size)
    }

    @Test
    fun `a family spans every one of its Fach types`() {
        VoiceFamilyRanges.of(null).forEach { family ->
            ALL_FACH.filter { it.categoryRes == family.categoryRes }.forEach { fach ->
                assertTrue(family.rangeMinHz <= fach.rangeMinHz && family.rangeMaxHz >= fach.rangeMaxHz)
                assertTrue(family.tessituraMinHz <= fach.tessituraMinHz && family.tessituraMaxHz >= fach.tessituraMaxHz)
            }
        }
    }

    @Test
    fun `the comfortable span sits inside the full range`() {
        VoiceFamilyRanges.of(null).forEach {
            assertTrue(it.rangeMinHz <= it.tessituraMinHz && it.tessituraMaxHz <= it.rangeMaxHz)
        }
    }
}
