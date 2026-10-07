package com.yuriy.diapason.ui.screens.analyze

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RecordingGateProgressTest {

    @Test
    fun `progress label shows samples out of the minimum`() {
        assertEquals("32 / 40", formatGateProgress(32))
    }

    @Test
    fun `progress label never shows more than the minimum`() {
        assertEquals("40 / 40", formatGateProgress(55))
    }

    @Test
    fun `progress label keeps Latin digits under a Persian default locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR"))
            assertEquals("32 / 40", formatGateProgress(32))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
