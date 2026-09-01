package com.yuriy.diapason.ui.screens.analyze

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Tests for [formatHz] — pulled out of the [AnalyzeScreen] Composable and made
 * `internal` specifically so it can be tested directly, without Compose UI test infra.
 */
class AnalyzeScreenTest {

    @Test
    fun `formatHz renders one decimal place`() {
        assertEquals("440.0", formatHz(440f))
        assertEquals("82.5", formatHz(82.5f))
    }

    // ── Locale safety (BUG-05, KNOWN_ISSUES.md) ───────────────────────────────
    //
    // This is a live pitch readout, not localized prose — the device's default
    // locale must not change its digit glyphs (e.g. Persian renders Eastern
    // Arabic-Indic digits and a different decimal mark for an unqualified
    // .format() call).

    @Test
    fun `formatHz renders Western digits regardless of default locale`() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fa"))
        try {
            assertEquals("440.0", formatHz(440f))
        } finally {
            Locale.setDefault(original)
        }
    }
}
