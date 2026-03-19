package com.yuriy.diapason.analyzer

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// JSON fixture data model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One voiced frame as exported from a development session.
 *
 * Only the two fields meaningful for regression are stored; raw PCM audio is
 * never persisted.  [hz] is the YIN-estimated fundamental frequency;
 * [confidence] is the normalized YIN confidence in [0, 1].
 */
data class FixtureFrame(
    val hz: Float,
    val confidence: Float
)

/**
 * Conservative, behavior-focused assertions bundled with a fixture.
 *
 * Every field is optional so a fixture can assert only what it was designed to
 * demonstrate — a fixture about passaggio detection need not assert exact
 * boundary notes.
 *
 * Note names follow standard scientific pitch notation (e.g. "C4", "A#3").
 * Semitone tolerances are applied during assertion to avoid fragile exact-frame
 * comparisons.
 *
 * @param detectedMinNote    Expected detected floor note; asserted ±[semitoneTol] semitones.
 * @param detectedMaxNote    Expected detected ceiling note; asserted ±[semitoneTol] semitones.
 * @param comfortableLowNote Expected comfortable-range low; asserted ±[semitoneTol] semitones.
 * @param comfortableHighNote Expected comfortable-range high; asserted ±[semitoneTol] semitones.
 * @param passaggioNote      Expected passaggio; asserted ±[passaggioTol] semitones (wider
 *                           because passaggio is inherently approximate).
 * @param minAcceptedFrames  Lower bound on the number of frames that survive confidence +
 *                           range filtering.  Catches silent or near-silent fixtures.
 * @param semitoneTol        Tolerance for range/comfortable assertions (default 2 semitones).
 * @param passaggioTol       Tolerance for passaggio assertion (default 3 semitones).
 */
data class FixtureAssertions(
    val detectedMinNote: String? = null,
    val detectedMaxNote: String? = null,
    val comfortableLowNote: String? = null,
    val comfortableHighNote: String? = null,
    val passaggioNote: String? = null,
    val minAcceptedFrames: Int = 20,
    val semitoneTol: Int = 2,
    val passaggioTol: Int = 3
)

/**
 * Full fixture loaded from a JSON file.
 *
 * @param id          Machine-readable identifier, matches the filename without extension.
 * @param description Human-readable summary of the vocal scenario.
 * @param voiceType   Informational label (not used in assertions); e.g. "Lyric Tenor".
 * @param source      Either "synthetic" (built from a DSL) or "logcat_export" (from a real
 *                    session; see CAPTURING.md).
 * @param capturedAt  ISO-8601 date; "synthetic" for generated fixtures.
 * @param frames      Ordered sequence of voiced frames from the session.
 * @param assertions  Behavioral expectations for the regression tests.
 */
data class FixtureData(
    val id: String,
    val description: String,
    val voiceType: String,
    val source: String,
    val capturedAt: String,
    val frames: List<FixtureFrame>,
    val assertions: FixtureAssertions
)

// ─────────────────────────────────────────────────────────────────────────────
// JSON loader — minimal purpose-built parser, no external dependencies
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Loads [FixtureData] from JSON files on the test classpath.
 *
 * The parser handles only the subset of JSON used by the fixture schema and is
 * intentionally not a general-purpose JSON library.  Fixture files must comply
 * with the schema documented in CAPTURING.md.
 *
 * Usage:
 * ```kotlin
 * val fixture = FixtureLoader.load("lyric_tenor_warmup")
 * val pitches  = SessionReplay.acceptedPitches(fixture.toPitchSamples())
 * ```
 */
object FixtureLoader {

    /**
     * Loads the fixture named [name] (without ".json") from
     * `src/test/resources/fixtures/<name>.json` on the test classpath.
     */
    fun load(name: String): FixtureData {
        val path = "/fixtures/$name.json"
        val stream = FixtureLoader::class.java.getResourceAsStream(path)
            ?: error("Fixture not found on test classpath: $path")
        return parse(stream.bufferedReader().readText())
    }

    /** Parses a fixture JSON string.  Exposed for testing the loader itself. */
    internal fun parse(json: String): FixtureData {
        val id = extractString(json, "id") ?: ""
        val description = extractString(json, "description") ?: ""
        val voiceType = extractString(json, "voiceType") ?: ""
        val source = extractString(json, "source") ?: "synthetic"
        val capturedAt = extractString(json, "capturedAt") ?: "synthetic"

        val frames = parseFrames(json)
        val assertions = parseAssertions(extractObject(json, "assertions") ?: "{}")

        return FixtureData(
            id = id,
            description = description,
            voiceType = voiceType,
            source = source,
            capturedAt = capturedAt,
            frames = frames,
            assertions = assertions
        )
    }

    // ── Frame array parser ────────────────────────────────────────────────────

    private fun parseFrames(json: String): List<FixtureFrame> {
        val arrayText = extractArray(json, "frames") ?: return emptyList()
        val result = mutableListOf<FixtureFrame>()
        // Split into individual {…} objects.  A frame object is always flat (no nesting).
        var depth = 0
        var objStart = -1
        for (i in arrayText.indices) {
            when (arrayText[i]) {
                '{' -> {
                    if (depth++ == 0) objStart = i
                }

                '}' -> {
                    if (--depth == 0 && objStart >= 0) {
                        val obj = arrayText.substring(objStart, i + 1)
                        parseFrame(obj)?.let { result += it }
                        objStart = -1
                    }
                }
            }
        }
        return result
    }

    private fun parseFrame(obj: String): FixtureFrame? {
        val hz = extractFloat(obj, "hz") ?: return null
        val conf = extractFloat(obj, "confidence") ?: return null
        return FixtureFrame(hz = hz, confidence = conf)
    }

    // ── Assertions object parser ──────────────────────────────────────────────

    private fun parseAssertions(obj: String) = FixtureAssertions(
        detectedMinNote = extractString(obj, "detectedMinNote"),
        detectedMaxNote = extractString(obj, "detectedMaxNote"),
        comfortableLowNote = extractString(obj, "comfortableLowNote"),
        comfortableHighNote = extractString(obj, "comfortableHighNote"),
        passaggioNote = extractString(obj, "passaggioNote"),
        minAcceptedFrames = extractInt(obj, "minAcceptedFrames") ?: 20,
        semitoneTol = extractInt(obj, "semitoneTol") ?: 2,
        passaggioTol = extractInt(obj, "passaggioTol") ?: 3
    )

    // ── Primitive extractors ──────────────────────────────────────────────────

    private fun extractString(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*"([^"]*?)"""").find(json)?.groupValues?.get(1)

    private fun extractFloat(json: String, key: String): Float? =
        Regex(""""$key"\s*:\s*([0-9]+(?:\.[0-9]*)?)""").find(json)?.groupValues?.get(1)
            ?.toFloatOrNull()

    private fun extractInt(json: String, key: String): Int? =
        Regex(""""$key"\s*:\s*([0-9]+)""").find(json)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Extracts the text content of a JSON array value for [key].
     * Returns the raw text including brackets.
     */
    private fun extractArray(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val arrStart = json.indexOf('[', keyIdx)
        if (arrStart < 0) return null
        var depth = 0
        for (i in arrStart until json.length) {
            when (json[i]) {
                '[' -> depth++
                ']' -> {
                    if (--depth == 0) return json.substring(arrStart, i + 1)
                }
            }
        }
        return null
    }

    /**
     * Extracts the text content of a JSON object value for [key].
     * Returns the raw text including braces.
     */
    private fun extractObject(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val objStart = json.indexOf('{', keyIdx)
        if (objStart < 0) return null
        var depth = 0
        for (i in objStart until json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> {
                    if (--depth == 0) return json.substring(objStart, i + 1)
                }
            }
        }
        return null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bridge: FixtureData → PitchSample list (feeds into SessionReplay)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converts the fixture's [FixtureFrame] list into [PitchSample]s so it can be
 * passed directly to [SessionReplay.acceptedPitches] or [SessionReplay.buildProfile].
 *
 * Frame index is derived from list position.  All frames are flagged as voiced —
 * unvoiced frames are represented in the fixture by a confidence below
 * [SessionReplay.MIN_CONFIDENCE] or an out-of-range [hz], mirroring real logcat
 * exports where silence frames simply have low YIN confidence.
 */
fun FixtureData.toPitchSamples(): List<PitchSample> =
    frames.mapIndexed { idx, frame ->
        PitchSample(
            frameIndex = idx,
            hz = frame.hz,
            confidence = frame.confidence,
            isVoiced = true   // session files contain only voiced detections
        )
    }

// ─────────────────────────────────────────────────────────────────────────────
// Assertion helper — semitone-tolerant note comparison
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Assertion helpers used by [FixtureRegressionTest].
 *
 * All pitch comparisons are semitone-based to decouple tests from float precision
 * and the exact frame boundary where a singer crossed a note.
 */
object FixtureAssertHelper {

    /**
     * Converts a scientific pitch note name (e.g. "A4", "C#3", "D#5") to its
     * MIDI number.  Returns -1 for unrecognised names rather than throwing.
     */
    fun noteNameToMidi(note: String): Int {
        val m = Regex("""([A-G]#?)(-?\d+)""").find(note) ?: return -1
        val noteStep = mapOf(
            "C" to 0, "C#" to 1, "D" to 2, "D#" to 3, "E" to 4, "F" to 5,
            "F#" to 6, "G" to 7, "G#" to 8, "A" to 9, "A#" to 10, "B" to 11
        )
        val step = noteStep[m.groupValues[1]] ?: return -1
        val octave = m.groupValues[2].toIntOrNull() ?: return -1
        return (octave + 1) * 12 + step
    }

    /** Converts [hz] to its MIDI pitch number using the standard 440-Hz tuning. */
    fun hzToMidi(hz: Float): Int =
        ((12.0 * ln(hz / 440.0) / ln(2.0)) + 69).roundToInt()

    /**
     * Semitone distance between [hz] and the named [expectedNote].
     * Returns [Int.MAX_VALUE] when either argument is invalid.
     */
    fun semitoneDiff(hz: Float, expectedNote: String): Int {
        if (hz <= 0f) return Int.MAX_VALUE
        val actualMidi = hzToMidi(hz)
        val expectedMidi = noteNameToMidi(expectedNote)
        if (expectedMidi < 0) return Int.MAX_VALUE
        return abs(actualMidi - expectedMidi)
    }

    /**
     * Asserts that [actualHz] is within [toleranceSemitones] semitones of [expectedNote].
     *
     * Produces a descriptive failure message that shows both the actual note name and
     * the expected name, so test failures are immediately legible without an IDE.
     */
    fun assertWithinSemitones(
        label: String,
        actualHz: Float,
        expectedNote: String,
        toleranceSemitones: Int
    ) {
        val diff = semitoneDiff(actualHz, expectedNote)
        val actualNote = FachClassifier.hzToNoteName(actualHz)
        assert(diff <= toleranceSemitones) {
            "$label: expected $expectedNote ±${toleranceSemitones}st " +
                    "but got $actualNote (${"%.1f".format(actualHz)} Hz) — $diff semitones away"
        }
    }
}
