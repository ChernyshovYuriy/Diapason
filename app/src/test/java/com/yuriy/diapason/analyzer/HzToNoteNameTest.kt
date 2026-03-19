package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive tests for [FachClassifier.hzToNoteName].
 *
 * This function drives every note name shown to the user — wrong output means the
 * app shows "A#4" when the user sang "B4", or "C5" when they reached "B4".
 * Even a small rounding error in the MIDI math would silently mislabel an entire
 * octave of notes.
 *
 * The implementation uses truncating integer conversion (`toInt()`), which floors
 * positive MIDI values.  All expected values below were independently derived
 * from the equal-temperament formula `f = 440 × 2^((midi−69)/12)`.
 *
 * Tests:
 *  N1.  Reference note A4 = 440 Hz → "A4"
 *  N2.  Middle C (C4 = 261.63 Hz) → "C4"
 *  N3.  All 12 chromatic pitch classes are spelled correctly (one octave, C4–B4)
 *  N4.  Octave numbers increment correctly across multiple octaves
 *  N5.  Bass register notes (C2–B2) map to octave number "2"
 *  N6.  Deep bass notes used by Oktavist (sub-C2) stay within legal output format
 *  N7.  Soprano ceiling notes (C6 and above) are labelled correctly
 *  N8.  Colouratura ceiling E6 = 1319 Hz → "E6"
 *  N9.  Zero and negative Hz return the dash placeholder "—"
 *  N10. The function is strictly deterministic — same input always gives same output
 *  N11. Note names never contain digits in the pitch-class portion
 *  N12. Every returned string that is not "—" ends with an integer (the octave)
 */
class HzToNoteNameTest {

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun note(hz: Float) = FachClassifier.hzToNoteName(hz)

    // ── N1. Reference A4 ──────────────────────────────────────────────────────

    @Test
    fun `A4 at 440 Hz returns A4`() {
        assertEquals("A4", note(440.0f))
    }

    // ── N2. Middle C ──────────────────────────────────────────────────────────

    @Test
    fun `C4 at 261_63 Hz returns C4`() {
        // C4 (middle C) = 440 × 2^(-9/12) ≈ 261.626 Hz, MIDI 60
        assertEquals("C4", note(261.63f))
    }

    // ── N3. All 12 chromatic pitch classes in one octave ──────────────────────

    /**
     * Equal-temperament frequencies for C4 through B4.
     * Each was independently verified against the standard MIDI note table.
     */
    @Test
    fun `all 12 chromatic pitch classes in octave 4 map to correct note names`() {
        // (frequency in Hz, expected note name)
        val cases = listOf(
            261.63f to "C4",   // MIDI 60
            277.18f to "C#4",  // MIDI 61
            293.66f to "D4",   // MIDI 62
            311.13f to "D#4",  // MIDI 63
            329.63f to "E4",   // MIDI 64
            349.23f to "F4",   // MIDI 65
            369.99f to "F#4",  // MIDI 66
            392.00f to "G4",   // MIDI 67
            415.30f to "G#4",  // MIDI 68
            440.00f to "A4",   // MIDI 69
            466.16f to "A#4",  // MIDI 70
            493.88f to "B4",   // MIDI 71
        )
        for ((hz, expected) in cases) {
            assertEquals("Note at %.2f Hz".format(hz), expected, note(hz))
        }
    }

    // ── N4. Octave numbers increment correctly ────────────────────────────────

    @Test
    fun `C notes across multiple octaves carry the correct octave number`() {
        // C notes: MIDI 36=C2, 48=C3, 60=C4, 72=C5, 84=C6
        // Frequencies: 65.41, 130.81, 261.63, 523.25, 1046.50
        assertEquals("C2", note(65.41f))
        assertEquals("C3", note(130.81f))
        assertEquals("C4", note(261.63f))
        assertEquals("C5", note(523.25f))
        assertEquals("C6", note(1046.50f))
    }

    @Test
    fun `A notes span from A2 to A5 with correct octave numbers`() {
        // A2=110, A3=220, A4=440, A5=880 Hz
        assertEquals("A2", note(110.00f))
        assertEquals("A3", note(220.00f))
        assertEquals("A4", note(440.00f))
        assertEquals("A5", note(880.00f))
    }

    // ── N5. Bass register (C2–B2) ─────────────────────────────────────────────

    @Test
    fun `bass register C2 through B2 all carry octave number 2`() {
        // Representative bass notes
        val cases = listOf(
            65.41f  to "C2",   // MIDI 36 — low bass floor
            73.42f  to "D2",   // MIDI 38
            82.41f  to "E2",   // MIDI 40
            98.00f  to "G2",   // MIDI 43
            110.00f to "A2",   // MIDI 45
            123.47f to "B2",   // MIDI 47
        )
        for ((hz, expected) in cases) {
            assertEquals("%.2f Hz should be %s".format(hz, expected), expected, note(hz))
        }
    }

    // ── N6. Sub-C2 deep bass (Oktavist range) ─────────────────────────────────

    @Test
    fun `oktavist range notes below C2 are labelled without crashing`() {
        // 43 Hz = E1 (approximately), 55 Hz = A1
        val result43 = note(43.0f)
        val result55 = note(55.0f)

        // Neither should be the fallback dash
        assertFalse("43 Hz should not return '—'", result43 == "—")
        assertFalse("55 Hz should not return '—'", result55 == "—")

        // Both should end with the octave number
        assertTrue("43 Hz result '$result43' should end with a digit", result43.last().isDigit())
        assertTrue("55 Hz result '$result55' should end with a digit", result55.last().isDigit())

        // 43 Hz ≈ E1, 55 Hz ≈ A1
        assertTrue(
            "43 Hz ($result43) should contain octave 1",
            result43.endsWith("1")
        )
        assertTrue(
            "55 Hz ($result55) should be A1",
            result55 == "A1"
        )
    }

    // ── N7. Soprano ceiling (C6 region) ──────────────────────────────────────

    @Test
    fun `soprano ceiling notes in octave 6 carry correct octave number`() {
        assertEquals("C6",  note(1046.50f))  // MIDI 84
        assertEquals("D6",  note(1174.66f))  // MIDI 86
        assertEquals("E6",  note(1318.51f))  // MIDI 88
    }

    // ── N8. Colouratura ceiling E6 ────────────────────────────────────────────

    @Test
    fun `colouratura ceiling E6 at 1319 Hz returns E6`() {
        // Fixture constant Fixtures.E6 = 1319f is used in analyzer tests;
        // this confirms the human-readable label matches.
        assertEquals("E6", note(1319.0f))
    }

    @Test
    fun `coloratura soprano range max 2093 Hz maps to a C-note in octave 7`() {
        // Coloratura max rangeMaxHz = 2093f
        // 2093 ≈ 2093.00 Hz; C7 = 2093.00 Hz (MIDI 96)
        assertEquals("C7", note(2093.0f))
    }

    // ── N9. Edge values return the placeholder ─────────────────────────────────

    @Test
    fun `zero Hz returns the dash placeholder`() {
        assertEquals("—", note(0f))
    }

    @Test
    fun `negative Hz returns the dash placeholder`() {
        assertEquals("—", note(-1f))
        assertEquals("—", note(-440f))
    }

    // ── N10. Determinism ──────────────────────────────────────────────────────

    @Test
    fun `same frequency always returns the same note name across repeated calls`() {
        val frequencies = listOf(65f, 130f, 196f, 261.63f, 329.63f, 440f, 523.25f, 880f, 1047f)
        for (hz in frequencies) {
            val first = note(hz)
            val second = note(hz)
            val third = note(hz)
            assertEquals("hzToNoteName($hz) must be deterministic: call 1 vs 2", first, second)
            assertEquals("hzToNoteName($hz) must be deterministic: call 1 vs 3", first, third)
        }
    }

    // ── N11. Pitch class portion never contains digits ────────────────────────

    @Test
    fun `pitch class portion of the note name never contains a digit`() {
        val testFreqs = listOf(
            65.41f, 110f, 130.81f, 196f, 261.63f, 329.63f, 392f, 440f, 523.25f, 698f, 880f, 1047f
        )
        for (hz in testFreqs) {
            val name = note(hz)
            if (name == "—") continue
            // The note name ends with the octave digit(s); strip trailing digits → pitch class
            val pitchClass = name.trimEnd { it.isDigit() || it == '-' }
            assertTrue(
                "Pitch class '$pitchClass' of '$name' (${hz} Hz) must not be empty",
                pitchClass.isNotEmpty()
            )
            assertFalse(
                "Pitch class '$pitchClass' of '$name' (${hz} Hz) must not contain a digit",
                pitchClass.any { it.isDigit() }
            )
        }
    }

    // ── N12. Every non-placeholder result ends with an octave digit ───────────

    @Test
    fun `every non-placeholder note name ends with a digit`() {
        val testFreqs = listOf(
            43f, 65f, 82f, 110f, 130f, 165f, 196f, 220f, 247f, 262f, 330f,
            392f, 440f, 494f, 523f, 659f, 784f, 880f, 988f, 1047f, 1319f, 2093f
        )
        for (hz in testFreqs) {
            val name = note(hz)
            assertTrue(
                "Note name '$name' for ${hz} Hz must end with a digit (octave number)",
                name.last().isDigit()
            )
        }
    }

    // ── Passaggio notes from ALL_FACH ─────────────────────────────────────────

    /**
     * Every passaggio Hz value defined in [ALL_FACH] must produce a sensible,
     * non-placeholder note name.  A regression here would mean the voice-type
     * comparison screen shows "—" instead of a real note name.
     */
    @Test
    fun `every passaggio frequency in ALL_FACH maps to a non-placeholder note name`() {
        ALL_FACH.forEach { fach ->
            val name = note(fach.passaggioHz)
            assertFalse(
                "passaggioHz=${fach.passaggioHz} should produce a real note name, not '—'",
                name == "—"
            )
            assertTrue(
                "Note name '$name' for passaggio ${fach.passaggioHz} Hz must end with a digit",
                name.last().isDigit()
            )
        }
    }

    /**
     * Every range boundary Hz value defined in [ALL_FACH] must produce a real note name.
     */
    @Test
    fun `every range boundary frequency in ALL_FACH maps to a non-placeholder note name`() {
        ALL_FACH.forEach { fach ->
            listOf(
                fach.rangeMinHz to "rangeMinHz",
                fach.rangeMaxHz to "rangeMaxHz",
                fach.tessituraMinHz to "tessituraMinHz",
                fach.tessituraMaxHz to "tessituraMaxHz",
            ).forEach { (hz, label) ->
                val name = note(hz)
                assertFalse(
                    "$label=${hz} Hz for fach range ${fach.rangeMinHz}–${fach.rangeMaxHz} " +
                            "should not be '—'",
                    name == "—"
                )
            }
        }
    }
}
