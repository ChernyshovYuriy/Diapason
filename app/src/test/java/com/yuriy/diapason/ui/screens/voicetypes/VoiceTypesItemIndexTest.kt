package com.yuriy.diapason.ui.screens.voicetypes

import com.yuriy.diapason.analyzer.ALL_FACH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [voiceTypesItemIndex] — where "Learn about…" scrolls to on the Voice Types list. */
class VoiceTypesItemIndexTest {

    @Test
    fun `every Fach has its own list position`() {
        val indices = ALL_FACH.map { voiceTypesItemIndex(it.nameRes) }
        assertEquals("all 19 Fach must be found", ALL_FACH.size, indices.filterNotNull().size)
        assertEquals("positions must be distinct", ALL_FACH.size, indices.toSet().size)
    }

    @Test
    fun `the first card sits after the title and the first category header`() {
        assertEquals(2, ALL_FACH.mapNotNull { voiceTypesItemIndex(it.nameRes) }.min())
    }

    @Test
    fun `consecutive Fach in one category are adjacent, categories are separated by spacer and header`() {
        val sorted = ALL_FACH.mapNotNull { voiceTypesItemIndex(it.nameRes) }.sorted()
        val gaps = sorted.zipWithNext { a, b -> b - a }.toSet()
        // 1 within a category; 3 across a boundary (spacer + next header + card).
        assertEquals(setOf(1, 3), gaps)
    }

    @Test
    fun `an unknown resource has no position`() {
        assertNull(voiceTypesItemIndex(-1))
    }
}
