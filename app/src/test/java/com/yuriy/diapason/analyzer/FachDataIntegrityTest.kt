package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Data-integrity tests for [ALL_FACH].
 *
 * These tests have nothing to do with the classifier algorithm.  Their sole
 * purpose is to catch copy-paste mistakes or accidental edits in the fach
 * definition table — the kind of bug that is invisible at compile time but
 * silently corrupts every classification result at runtime.
 *
 * Rules verified:
 *
 *  D1.  Exactly 19 fach entries exist.
 *  D2.  rangeMinHz < rangeMaxHz  (range is ordered low → high).
 *  D3.  tessituraMinHz < tessituraMaxHz  (tessitura is ordered low → high).
 *  D4.  Tessitura is fully contained within the range:
 *         tessituraMinHz >= rangeMinHz  AND  tessituraMaxHz <= rangeMaxHz.
 *  D5.  passaggioHz falls within the full range:
 *         passaggioHz >= rangeMinHz  AND  passaggioHz <= rangeMaxHz.
 *  D6.  passaggioHz falls within the tessitura:
 *         passaggioHz >= tessituraMinHz  AND  passaggioHz <= tessituraMaxHz.
 *       Musicologically the passaggio (register break) sits in the middle of
 *       the voice's comfortable zone, so this is a meaningful domain constraint.
 *  D7.  All Hz values are strictly positive.
 *  D8.  No two fach definitions share an identical (rangeMinHz, rangeMaxHz) pair.
 *       These pairs are used as stable identifiers in the classifier tests.
 *  D9.  No two fach definitions share an identical nameRes resource ID.
 *       Duplicate IDs indicate a copy-paste error in the fach table.
 *  D10. All @StringRes and @ArrayRes fields are positive (non-zero, non-negative).
 *       A zero or negative resource ID would cause a runtime crash when the UI
 *       tries to look up the string.
 */
class FachDataIntegrityTest {

    /**
     * Human-readable label for assertion messages.
     *
     * [FachDefinition.nameRes] is an integer resource ID — printing it gives an
     * opaque number like "2131820583". The Hz range is equally unique per fach
     * (guaranteed by D8) and immediately tells you which voice type failed.
     */
    private fun FachDefinition.label() = "[${rangeMinHz}–${rangeMaxHz} Hz]"

    // ── D1. Exactly 19 fachs ──────────────────────────────────────────────────

    @Test
    fun `ALL_FACH contains exactly 19 entries`() {
        assertEquals(
            "ALL_FACH must contain exactly 19 voice-type definitions",
            19, ALL_FACH.size
        )
    }

    // ── D2. Range direction ───────────────────────────────────────────────────

    @Test
    fun `rangeMinHz is less than rangeMaxHz for every fach`() {
        ALL_FACH.forEach { fach ->
            assertTrue(
                "${fach.label()}: rangeMinHz (${fach.rangeMinHz}) must be < rangeMaxHz (${fach.rangeMaxHz})",
                fach.rangeMinHz < fach.rangeMaxHz
            )
        }
    }

    // ── D3. Tessitura direction ───────────────────────────────────────────────

    @Test
    fun `tessituraMinHz is less than tessituraMaxHz for every fach`() {
        ALL_FACH.forEach { fach ->
            assertTrue(
                "${fach.label()}: tessituraMinHz (${fach.tessituraMinHz}) " +
                        "must be < tessituraMaxHz (${fach.tessituraMaxHz})",
                fach.tessituraMinHz < fach.tessituraMaxHz
            )
        }
    }

    // ── D4. Tessitura contained within range ──────────────────────────────────

    @Test
    fun `tessituraMinHz is greater than or equal to rangeMinHz for every fach`() {
        ALL_FACH.forEach { fach ->
            assertTrue(
                "${fach.label()}: tessituraMinHz (${fach.tessituraMinHz}) " +
                        "must be >= rangeMinHz (${fach.rangeMinHz})",
                fach.tessituraMinHz >= fach.rangeMinHz
            )
        }
    }

    @Test
    fun `tessituraMaxHz is less than or equal to rangeMaxHz for every fach`() {
        ALL_FACH.forEach { fach ->
            assertTrue(
                "${fach.label()}: tessituraMaxHz (${fach.tessituraMaxHz}) " +
                        "must be <= rangeMaxHz (${fach.rangeMaxHz})",
                fach.tessituraMaxHz <= fach.rangeMaxHz
            )
        }
    }

    // ── D5. Passaggio within full range ───────────────────────────────────────

    @Test
    fun `passaggioHz is within rangeMinHz and rangeMaxHz for every fach`() {
        ALL_FACH.forEach { fach ->
            assertTrue(
                "${fach.label()}: passaggioHz (${fach.passaggioHz}) " +
                        "must be >= rangeMinHz (${fach.rangeMinHz})",
                fach.passaggioHz >= fach.rangeMinHz
            )
            assertTrue(
                "${fach.label()}: passaggioHz (${fach.passaggioHz}) " +
                        "must be <= rangeMaxHz (${fach.rangeMaxHz})",
                fach.passaggioHz <= fach.rangeMaxHz
            )
        }
    }

    // ── D6. Passaggio within tessitura ────────────────────────────────────────

    @Test
    fun `passaggioHz is within tessituraMinHz and tessituraMaxHz for every fach`() {
        ALL_FACH.forEach { fach ->
            assertTrue(
                "${fach.label()}: passaggioHz (${fach.passaggioHz}) " +
                        "must be >= tessituraMinHz (${fach.tessituraMinHz}). " +
                        "The register break should sit inside the comfortable zone.",
                fach.passaggioHz >= fach.tessituraMinHz
            )
            assertTrue(
                "${fach.label()}: passaggioHz (${fach.passaggioHz}) " +
                        "must be <= tessituraMaxHz (${fach.tessituraMaxHz}). " +
                        "The register break should sit inside the comfortable zone.",
                fach.passaggioHz <= fach.tessituraMaxHz
            )
        }
    }

    // ── D7. All Hz values are strictly positive ───────────────────────────────

    @Test
    fun `all Hz values are strictly positive for every fach`() {
        ALL_FACH.forEach { fach ->
            listOf(
                "rangeMinHz" to fach.rangeMinHz,
                "rangeMaxHz" to fach.rangeMaxHz,
                "tessituraMinHz" to fach.tessituraMinHz,
                "tessituraMaxHz" to fach.tessituraMaxHz,
                "passaggioHz" to fach.passaggioHz,
            ).forEach { (label, value) ->
                assertTrue(
                    "${fach.label()}: $label ($value) must be > 0",
                    value > 0f
                )
            }
        }
    }

    // ── D8. Unique (rangeMin, rangeMax) pairs ─────────────────────────────────

    @Test
    fun `no two fachs share the same rangeMinHz and rangeMaxHz pair`() {
        val pairs = ALL_FACH.map { it.rangeMinHz to it.rangeMaxHz }
        val duplicates = pairs.groupBy { it }.filter { (_, v) -> v.size > 1 }.keys
        assertTrue(
            "Duplicate (rangeMinHz, rangeMaxHz) pairs found — each fach must have a unique range: $duplicates",
            duplicates.isEmpty()
        )
    }

    // ── D9. Unique nameRes IDs ────────────────────────────────────────────────

    @Test
    fun `no two fachs share the same nameRes resource ID`() {
        val ids = ALL_FACH.map { it.nameRes }
        val duplicates = ids.groupBy { it }.filter { (_, v) -> v.size > 1 }.keys
        assertTrue(
            "Duplicate nameRes IDs found — copy-paste error in FachData.kt: $duplicates",
            duplicates.isEmpty()
        )
    }

    // ── D10. All resource IDs are positive ────────────────────────────────────

    @Test
    fun `all StringRes and ArrayRes fields are positive for every fach`() {
        ALL_FACH.forEach { fach ->
            listOf(
                "nameRes" to fach.nameRes,
                "categoryRes" to fach.categoryRes,
                "famousRolesRes" to fach.famousRolesRes,
                "exampleSingersRes" to fach.exampleSingersRes,
            ).forEach { (label, resId) ->
                assertTrue(
                    "${fach.label()}: $label ($resId) must be a positive resource ID. " +
                            "Zero or negative means the annotation is missing or wrong.",
                    resId > 0
                )
            }
        }
    }
}
