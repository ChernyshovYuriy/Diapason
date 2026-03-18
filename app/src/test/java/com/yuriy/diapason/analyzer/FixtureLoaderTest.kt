package com.yuriy.diapason.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [FixtureLoader] and the [FixtureData.toPitchSamples] bridge.
 *
 * Coverage:
 *  L1.  Well-formed fixture loads from the test classpath correctly.
 *  L2.  All string fields are extracted without leading/trailing whitespace.
 *  L3.  Frame array is counted and ordered correctly.
 *  L4.  Null assertion fields round-trip as Kotlin null.
 *  L5.  Missing optional assertion fields use documented defaults.
 *  L6.  Empty frames array produces an empty list without crashing.
 *  L7.  Extra unknown fields in the JSON are silently ignored.
 *  L8.  Requesting a non-existent fixture throws with a clear message.
 *  L9.  [FixtureData.toPitchSamples] produces one [PitchSample] per frame,
 *       preserving hz and confidence values exactly.
 *  L10. Frames with hz=0 / confidence=0 survive toPitchSamples unmodified
 *       (filtering is SessionReplay's responsibility, not the loader's).
 *  L11. [FixtureAssertHelper.noteNameToMidi] returns correct MIDI values for
 *       representative note names.
 *  L12. [FixtureAssertHelper.semitoneDiff] measures semitone distances correctly.
 *  L13. [FixtureAssertHelper.assertWithinSemitones] passes and fails correctly.
 */
class FixtureLoaderTest {

    // ── L1. Load a real fixture from the classpath ────────────────────────────

    @Test
    fun `load retrieves a known fixture from the test classpath`() {
        val fixture = FixtureLoader.load("lyric_tenor_warmup")
        assertEquals("lyric_tenor_warmup", fixture.id)
        assertTrue("fixture should have at least 50 frames", fixture.frames.size >= 50)
        assertNotNull("assertions block must not be null", fixture.assertions)
    }

    // ── L2. String field extraction ───────────────────────────────────────────

    @Test
    fun `parse extracts id description voiceType source and capturedAt correctly`() {
        val json = """
            {
              "id": "test_fixture",
              "description": "A test description",
              "voiceType": "Test Voice",
              "source": "synthetic",
              "capturedAt": "synthetic",
              "frames": [],
              "assertions": {}
            }
        """.trimIndent()

        val f = FixtureLoader.parse(json)
        assertEquals("test_fixture", f.id)
        assertEquals("A test description", f.description)
        assertEquals("Test Voice", f.voiceType)
        assertEquals("synthetic", f.source)
        assertEquals("synthetic", f.capturedAt)
    }

    @Test
    fun `parse handles logcat_export source value`() {
        val json = minimalFixtureJson(source = "logcat_export", capturedAt = "2025-03-01")
        val f = FixtureLoader.parse(json)
        assertEquals("logcat_export", f.source)
        assertEquals("2025-03-01", f.capturedAt)
    }

    // ── L3. Frame array parsing ───────────────────────────────────────────────

    @Test
    fun `parse counts frames correctly for a small inline array`() {
        val json = """
            {
              "id": "x", "description": "", "voiceType": "", "source": "synthetic",
              "capturedAt": "synthetic",
              "frames": [
                {"hz": 440.0, "confidence": 0.92},
                {"hz": 330.0, "confidence": 0.88},
                {"hz": 261.6, "confidence": 0.95}
              ],
              "assertions": {}
            }
        """.trimIndent()

        val f = FixtureLoader.parse(json)
        assertEquals(3, f.frames.size)
    }

    @Test
    fun `parse preserves frame order and hz values`() {
        val json = """
            {
              "id": "x", "description": "", "voiceType": "", "source": "synthetic",
              "capturedAt": "synthetic",
              "frames": [
                {"hz": 196.0, "confidence": 0.91},
                {"hz": 220.0, "confidence": 0.93},
                {"hz": 246.9, "confidence": 0.90}
              ],
              "assertions": {}
            }
        """.trimIndent()

        val f = FixtureLoader.parse(json)
        assertEquals(196.0f, f.frames[0].hz, 0.01f)
        assertEquals(220.0f, f.frames[1].hz, 0.01f)
        assertEquals(246.9f, f.frames[2].hz, 0.01f)
        assertEquals(0.91f, f.frames[0].confidence, 0.001f)
        assertEquals(0.93f, f.frames[1].confidence, 0.001f)
        assertEquals(0.90f, f.frames[2].confidence, 0.001f)
    }

    // ── L4. Null assertion fields ─────────────────────────────────────────────

    @Test
    fun `parse treats JSON null for passaggioNote as Kotlin null`() {
        val json = minimalFixtureJson(
            assertions = """
            "passaggioNote": null,
            "detectedMinNote": "G3"
        """.trimIndent()
        )

        val f = FixtureLoader.parse(json)
        assertNull("passaggioNote should be null", f.assertions.passaggioNote)
        assertEquals("G3", f.assertions.detectedMinNote)
    }

    @Test
    fun `parse treats all null assertion note fields as Kotlin null`() {
        val json = minimalFixtureJson(
            assertions = """
            "detectedMinNote": null,
            "detectedMaxNote": null,
            "comfortableLowNote": null,
            "comfortableHighNote": null,
            "passaggioNote": null
        """.trimIndent()
        )

        val f = FixtureLoader.parse(json)
        assertNull(f.assertions.detectedMinNote)
        assertNull(f.assertions.detectedMaxNote)
        assertNull(f.assertions.comfortableLowNote)
        assertNull(f.assertions.comfortableHighNote)
        assertNull(f.assertions.passaggioNote)
    }

    // ── L5. Default assertion values ──────────────────────────────────────────

    @Test
    fun `parse uses default values when optional assertion fields are absent`() {
        val json = minimalFixtureJson(assertions = "")  // empty assertions object
        val f = FixtureLoader.parse(json)

        // Documented defaults from FixtureAssertions
        assertEquals("default minAcceptedFrames", 20, f.assertions.minAcceptedFrames)
        assertEquals("default semitoneTol", 2, f.assertions.semitoneTol)
        assertEquals("default passaggioTol", 3, f.assertions.passaggioTol)
        assertNull("absent string → null", f.assertions.detectedMinNote)
        assertNull("absent string → null", f.assertions.passaggioNote)
    }

    @Test
    fun `parse reads explicit assertion integer fields correctly`() {
        val json = minimalFixtureJson(
            assertions = """
            "minAcceptedFrames": 42,
            "semitoneTol": 3,
            "passaggioTol": 5
        """.trimIndent()
        )

        val f = FixtureLoader.parse(json)
        assertEquals(42, f.assertions.minAcceptedFrames)
        assertEquals(3, f.assertions.semitoneTol)
        assertEquals(5, f.assertions.passaggioTol)
    }

    // ── L6. Empty frames array ────────────────────────────────────────────────

    @Test
    fun `parse handles empty frames array without crashing`() {
        val json = minimalFixtureJson()   // default has frames: []
        val f = FixtureLoader.parse(json)
        assertTrue("empty frames should produce an empty list", f.frames.isEmpty())
    }

    // ── L7. Unknown fields are ignored ───────────────────────────────────────

    @Test
    fun `parse ignores unknown top-level fields without crashing`() {
        val json = """
            {
              "id": "test", "description": "", "voiceType": "", "source": "synthetic",
              "capturedAt": "synthetic", "frames": [],
              "unknownField": "some_value",
              "anotherUnknown": 42,
              "assertions": { "unknownAssertion": "ignored" }
            }
        """.trimIndent()

        val f = FixtureLoader.parse(json)
        assertEquals("test", f.id)  // known field unaffected
    }

    // ── L8. Non-existent fixture throws clearly ───────────────────────────────

    @Test
    fun `load throws an informative error for a non-existent fixture`() {
        try {
            FixtureLoader.load("this_fixture_does_not_exist")
            fail("Expected an exception for a missing fixture")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Error message should mention the missing path, got: ${e.message}",
                e.message?.contains("this_fixture_does_not_exist") == true
            )
        }
    }

    // ── L9/L10. toPitchSamples bridge ─────────────────────────────────────────

    @Test
    fun `toPitchSamples produces one PitchSample per frame in order`() {
        val fixture = FixtureData(
            id = "x", description = "", voiceType = "", source = "synthetic",
            capturedAt = "synthetic",
            frames = listOf(
                FixtureFrame(hz = 440.0f, confidence = 0.92f),
                FixtureFrame(hz = 330.0f, confidence = 0.88f)
            ),
            assertions = FixtureAssertions()
        )

        val samples = fixture.toPitchSamples()

        assertEquals(2, samples.size)
        assertEquals(440.0f, samples[0].hz, 0.001f)
        assertEquals(0.92f, samples[0].confidence, 0.001f)
        assertEquals(0, samples[0].frameIndex)
        assertEquals(330.0f, samples[1].hz, 0.001f)
        assertEquals(1, samples[1].frameIndex)
    }

    @Test
    fun `toPitchSamples marks all frames as voiced regardless of hz or confidence`() {
        // Filtering is SessionReplay's job; the loader must not pre-filter.
        val fixture = FixtureData(
            id = "x", description = "", voiceType = "", source = "synthetic",
            capturedAt = "synthetic",
            frames = listOf(
                FixtureFrame(hz = 0f, confidence = 0.0f),   // would fail filter
                FixtureFrame(hz = 50f, confidence = 0.5f),   // below MIN_PITCH_HZ
                FixtureFrame(hz = 440f, confidence = 0.95f)   // clean voiced frame
            ),
            assertions = FixtureAssertions()
        )

        val samples = fixture.toPitchSamples()

        assertEquals(3, samples.size)
        samples.forEach { s ->
            assertTrue(
                "toPitchSamples must set isVoiced=true; found false at index ${s.frameIndex}",
                s.isVoiced
            )
        }
    }

    @Test
    fun `toPitchSamples on empty frames produces empty list`() {
        val fixture = FixtureData(
            id = "x", description = "", voiceType = "", source = "synthetic",
            capturedAt = "synthetic", frames = emptyList(), assertions = FixtureAssertions()
        )
        assertTrue(fixture.toPitchSamples().isEmpty())
    }

    // ── L11. FixtureAssertHelper.noteNameToMidi ────────────────────────────────

    @Test
    fun `noteNameToMidi returns correct MIDI values for standard note names`() {
        val helper = FixtureAssertHelper
        assertEquals("C4 is MIDI 60", 60, helper.noteNameToMidi("C4"))
        assertEquals("A4 is MIDI 69", 69, helper.noteNameToMidi("A4"))
        assertEquals("A3 is MIDI 57", 57, helper.noteNameToMidi("A3"))
        assertEquals("C#4 is MIDI 61", 61, helper.noteNameToMidi("C#4"))
        assertEquals("G2 is MIDI 43", 43, helper.noteNameToMidi("G2"))
        assertEquals("E2 is MIDI 40", 40, helper.noteNameToMidi("E2"))
        assertEquals("C5 is MIDI 72", 72, helper.noteNameToMidi("C5"))
    }

    @Test
    fun `noteNameToMidi returns -1 for malformed input`() {
        val helper = FixtureAssertHelper
        assertEquals(-1, helper.noteNameToMidi(""))
        assertEquals(-1, helper.noteNameToMidi("X4"))
        assertEquals(-1, helper.noteNameToMidi("440Hz"))
    }

    // ── L12. FixtureAssertHelper.semitoneDiff ─────────────────────────────────

    @Test
    fun `semitoneDiff returns 0 for exact match`() {
        // A4 = 440 Hz = MIDI 69; hzToMidi(440) should be 69
        assertEquals(0, FixtureAssertHelper.semitoneDiff(440f, "A4"))
    }

    @Test
    fun `semitoneDiff returns 12 for notes one octave apart`() {
        // A4 (440 Hz) vs A3 (220 Hz) — 12 semitones
        assertEquals(12, FixtureAssertHelper.semitoneDiff(440f, "A3"))
        assertEquals(12, FixtureAssertHelper.semitoneDiff(220f, "A4"))
    }

    @Test
    fun `semitoneDiff returns 1 for adjacent semitones`() {
        // A4 (440 Hz) vs G#4 / A#4
        assertEquals(1, FixtureAssertHelper.semitoneDiff(440f, "G#4"))
        assertEquals(1, FixtureAssertHelper.semitoneDiff(440f, "A#4"))
    }

    @Test
    fun `semitoneDiff returns MAX_VALUE for invalid inputs`() {
        assertEquals(Int.MAX_VALUE, FixtureAssertHelper.semitoneDiff(0f, "A4"))
        assertEquals(Int.MAX_VALUE, FixtureAssertHelper.semitoneDiff(-1f, "A4"))
        assertEquals(Int.MAX_VALUE, FixtureAssertHelper.semitoneDiff(440f, ""))
    }

    // ── L13. FixtureAssertHelper.assertWithinSemitones ────────────────────────

    @Test
    fun `assertWithinSemitones passes when actual is within tolerance`() {
        // A4 (440 Hz) asserted against "A4" at 0 semitones tolerance — exact match
        FixtureAssertHelper.assertWithinSemitones(
            label = "test",
            actualHz = 440f,
            expectedNote = "A4",
            toleranceSemitones = 0
        )

        // G3 (196 Hz) asserted against "A3" (220 Hz) — 2 semitones apart, tol=2
        FixtureAssertHelper.assertWithinSemitones(
            label = "test",
            actualHz = 196f,
            expectedNote = "A3",
            toleranceSemitones = 2
        )
    }

    @Test
    fun `assertWithinSemitones fails when actual is outside tolerance`() {
        try {
            FixtureAssertHelper.assertWithinSemitones(
                label = "test",
                actualHz = 440f,    // A4
                expectedNote = "C4",    // 9 semitones below A4
                toleranceSemitones = 2
            )
            fail("Expected AssertionError for 9-semitone difference with tolerance 2")
        } catch (e: AssertionError) {
            assertTrue(
                "Error message should mention the semitone distance, got: ${e.message}",
                e.message?.contains("9") == true || e.message?.contains("semitone") == true
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal valid fixture JSON string with optional overrides.
     * Used to keep individual tests focused on one aspect at a time.
     */
    private fun minimalFixtureJson(
        id: String = "test",
        source: String = "synthetic",
        capturedAt: String = "synthetic",
        frames: String = "",            // comma-separated frame objects, or empty
        assertions: String = ""         // comma-separated assertion key-value pairs, or empty
    ): String {
        val framesArray = if (frames.isBlank()) "[]" else "[$frames]"
        val assertObject = if (assertions.isBlank()) "{}" else "{$assertions}"
        return """
            {
              "id": "$id",
              "description": "minimal test fixture",
              "voiceType": "Test",
              "source": "$source",
              "capturedAt": "$capturedAt",
              "frames": $framesArray,
              "assertions": $assertObject
            }
        """.trimIndent()
    }
}
