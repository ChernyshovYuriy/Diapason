package com.yuriy.diapason.ui.screens.comparison

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Tests for [formatTime] — pulled out of the [WarmUpTimerScreen] Composable and made
 * `internal` specifically so it can be tested directly, without Compose UI test infra.
 */
class WarmUpTimerScreenTest {

    @Test
    fun `formatTime renders minutes and zero-padded seconds`() {
        assertEquals("5:00", formatTime(300))
        assertEquals("0:05", formatTime(5))
        assertEquals("1:09", formatTime(69))
        assertEquals("0:00", formatTime(0))
    }

    // ── Locale safety (BUG-05, KNOWN_ISSUES.md) ───────────────────────────────
    //
    // The countdown timer's digits must not change glyph on the device's default
    // locale (e.g. Persian renders Eastern Arabic-Indic digits and a different
    // decimal/colon-adjacent formatting for an unqualified .format() call).

    @Test
    fun `formatTime renders Western digits regardless of default locale`() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fa"))
        try {
            assertEquals("5:00", formatTime(300))
        } finally {
            Locale.setDefault(original)
        }
    }
}
