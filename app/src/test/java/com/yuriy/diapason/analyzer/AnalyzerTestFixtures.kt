package com.yuriy.diapason.analyzer

// ─────────────────────────────────────────────────────────────────────────────
// Fixture model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One pitch-analysis frame as the analyzer would produce it.
 *
 * [frameIndex]  Sequential frame number — lets tests describe time ordering without
 *               committing to a fixed sample rate.
 * [hz]          Detected fundamental frequency in Hertz. Use 0f or a negative value
 *               to represent an unvoiced / silence frame.
 * [confidence]  YIN confidence in [0, 1]. Frames below MIN_CONFIDENCE are discarded
 *               by [SessionReplay] just as [VoiceAnalyzer] would discard them.
 * [isVoiced]    Optional explicit voiced flag. When false the frame is always dropped,
 *               regardless of confidence — useful for modelling deliberate silence gaps.
 */
data class PitchSample(
    val frameIndex: Int,
    val hz: Float,
    val confidence: Float = 1.0f,
    val isVoiced: Boolean = true
)

// ─────────────────────────────────────────────────────────────────────────────
// Session replay helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Applies the same acceptance rules as [VoiceAnalyzer] to a list of [PitchSample]s
 * and returns the surviving pitch values — a plain [List<Float>] that can be fed
 * directly into [FachClassifier] functions.
 *
 * Acceptance criteria (mirrors VoiceAnalyzer constants):
 *   - isVoiced == true
 *   - hz in [MIN_PITCH_HZ, MAX_PITCH_HZ]
 *   - confidence >= MIN_CONFIDENCE
 */
object SessionReplay {

    const val MIN_PITCH_HZ = 60f
    const val MAX_PITCH_HZ = 2200f
    const val MIN_CONFIDENCE = 0.80f

    /**
     * Filter [samples] with the same rules [VoiceAnalyzer] uses and return the
     * accepted pitch values in frame order.
     */
    fun acceptedPitches(samples: List<PitchSample>): List<Float> =
        samples
            .filter { s ->
                s.isVoiced &&
                        s.hz in MIN_PITCH_HZ..MAX_PITCH_HZ &&
                        s.confidence >= MIN_CONFIDENCE
            }
            .map { it.hz }

    /**
     * Convenience: build a [VoiceProfile] from a fixture session using the same
     * logic [VoiceAnalyzer.stop] uses. Returns null when too few samples survive
     * (mirrors the 20-sample guard in production).
     */
    fun buildProfile(samples: List<PitchSample>, durationSeconds: Float = 0f): VoiceProfile? {
        val pitches = acceptedPitches(samples)
        if (pitches.size < 20) return null

        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(pitches)
        val passaggio = FachClassifier.estimatePassaggio(pitches)

        return VoiceProfile(
            detectedMinHz = detectedMin,
            detectedMaxHz = detectedMax,
            comfortableLowHz = comfortableLow,
            comfortableHighHz = comfortableHigh,
            estimatedPassaggioHz = passaggio,
            sampleCount = pitches.size,
            durationSeconds = durationSeconds
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session builder DSL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fluent builder for constructing [PitchSample] sequences.
 *
 * Usage:
 * ```
 * val session = buildSession {
 *     sustainedNote(hz = 262f, frames = 20)          // C4 for 20 frames
 *     stepUp(from = 262f, to = 392f, steps = 8)      // ascending walk
 *     silenceGap(frames = 10)                         // silence
 *     noisy(centerHz = 330f, varianceHz = 40f, frames = 15)  // unstable zone
 * }
 * ```
 */
class SessionBuilder {

    private val samples = mutableListOf<PitchSample>()
    private var nextFrame = 0

    // ── Core primitive ────────────────────────────────────────────────────────

    fun frame(hz: Float, confidence: Float = 1.0f, isVoiced: Boolean = true): SessionBuilder {
        samples += PitchSample(
            frameIndex = nextFrame++, hz = hz,
            confidence = confidence, isVoiced = isVoiced
        )
        return this
    }

    // ── Convenience builders ──────────────────────────────────────────────────

    /** [frames] identical voiced frames at [hz] with [confidence]. */
    fun sustainedNote(hz: Float, frames: Int, confidence: Float = 1.0f): SessionBuilder {
        repeat(frames) { frame(hz, confidence) }
        return this
    }

    /**
     * Chromatic-style walk from [from] to [to] (inclusive endpoints), split into
     * [steps] equal intervals.  Each step emits [framesPerStep] frames.
     */
    fun stepUp(
        from: Float,
        to: Float,
        steps: Int = 8,
        framesPerStep: Int = 3,
        confidence: Float = 1.0f
    ): SessionBuilder {
        val interval = (to - from) / steps
        for (i in 0..steps) {
            val hz = from + interval * i
            repeat(framesPerStep) { frame(hz, confidence) }
        }
        return this
    }

    /** Descending walk — same as [stepUp] but reversed. */
    fun stepDown(
        from: Float,
        to: Float,
        steps: Int = 8,
        framesPerStep: Int = 3,
        confidence: Float = 1.0f
    ): SessionBuilder = stepUp(
        from = from, to = to, steps = steps,
        framesPerStep = framesPerStep, confidence = confidence
    )

    /**
     * Unvoiced silence gap.  These frames are always rejected by [SessionReplay].
     * Use to simulate pauses between phrases.
     */
    fun silenceGap(frames: Int): SessionBuilder {
        repeat(frames) { frame(hz = 0f, confidence = 0f, isVoiced = false) }
        return this
    }

    /**
     * Rapid alternation between [centerHz] ± [varianceHz] — models the area near
     * the passaggio where the voice is most unstable.
     *
     * Produces an alternating low/high pattern rather than random noise so the
     * tests remain deterministic.
     */
    fun noisyGlide(
        centerHz: Float, varianceHz: Float, frames: Int,
        confidence: Float = 1.0f
    ): SessionBuilder {
        for (i in 0 until frames) {
            val hz = if (i % 2 == 0) centerHz - varianceHz else centerHz + varianceHz
            frame(hz, confidence)
        }
        return this
    }

    /**
     * A single isolated sample at [hz] with [confidence], meant to model a stray
     * falsetto squeak or microphone artefact with no neighbours.
     */
    fun isolatedSpike(hz: Float, confidence: Float = 1.0f): SessionBuilder =
        frame(hz, confidence)

    /**
     * Simulate a region where YIN confidence degrades — [frames] frames at [hz] with
     * confidence linearly decreasing from [startConfidence] to [endConfidence].
     */
    fun fadingConfidence(
        hz: Float,
        frames: Int,
        startConfidence: Float = 1.0f,
        endConfidence: Float = 0.0f
    ): SessionBuilder {
        for (i in 0 until frames) {
            val conf = startConfidence + (endConfidence - startConfidence) * i / frames
            frame(hz, conf)
        }
        return this
    }

    fun build(): List<PitchSample> = samples.toList()
}

/** Entry point for the DSL. */
fun buildSession(block: SessionBuilder.() -> Unit): List<PitchSample> =
    SessionBuilder().apply(block).build()

// ─────────────────────────────────────────────────────────────────────────────
// Named fixture sessions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pre-built sessions representing realistic vocal scenarios.
 * These are the canonical fixtures shared across test classes.
 *
 * Naming convention: [VoiceType]_[Scenario].  All frequencies are real
 * note values to make debugging intuitive.
 */
object Fixtures {

    // ── Frequency landmarks (standard equal-temperament Hz, rounded) ──────────

    // Bass / baritone territory
    const val E2 = 82f   // low bass floor
    const val G2 = 98f
    const val C3 = 130f
    const val E3 = 165f
    const val G3 = 196f  // comfortable baritone low

    // Tenor / baritone passaggio area
    const val C4 = 262f  // middle C
    const val D4 = 294f
    const val E4 = 330f
    const val F4 = 349f
    const val G4 = 392f
    const val A4 = 440f  // concert pitch
    const val B4 = 494f

    // Soprano / mezzo territory
    const val C5 = 523f
    const val D5 = 587f
    const val E5 = 659f
    const val G5 = 784f
    const val A5 = 880f
    const val B5 = 988f

    // High soprano / coloratura
    const val C6 = 1047f
    const val E6 = 1319f

    // ── Scenario 1: Stable ascending warm-up (baritone-ish range) ─────────────

    /**
     * Singer starts low (G3), steps up chromatically to G4, then back down.
     * Steady confidence throughout. No artefacts.
     * Expected: detected min ≈ G3, detected max ≈ G4;
     *           comfortable range sits in the middle of the arc.
     */
    val ASCENDING_WARMUP: List<PitchSample> = buildSession {
        sustainedNote(G3, 5)          // anchor low
        stepUp(G3, G4, steps = 12, framesPerStep = 4)
        sustainedNote(G4, 5)          // anchor high
    }

    // ── Scenario 2: Stable descending warm-up ────────────────────────────────

    /**
     * Same range as ASCENDING_WARMUP but top-down.
     * Expected: identical extremes and comfortable range regardless of direction.
     */
    val DESCENDING_WARMUP: List<PitchSample> = buildSession {
        sustainedNote(G4, 5)
        stepDown(G4, G3, steps = 12, framesPerStep = 4)
        sustainedNote(G3, 5)
    }

    // ── Scenario 3: Brief high spike, stable core ─────────────────────────────

    /**
     * A lyric soprano session: mostly E4–C5, then one isolated A5 frame (falsetto squeak).
     * Expected: detected max = A5 only if A5 has a neighbor; comfortable high ≈ C5.
     * Since A5 is isolated (no neighbor), neighbor-validation should exclude it from
     * detected max, leaving it at C5.
     */
    val HIGH_SPIKE_OVER_STABLE_CORE: List<PitchSample> = buildSession {
        sustainedNote(E4, 20)          // bulk of session
        sustainedNote(C5, 20)
        isolatedSpike(A5)              // lone high spike — no neighbor
    }

    // ── Scenario 4: Brief low spike, stable core ─────────────────────────────

    /**
     * A soprano session: mostly C5–E5, then one isolated E2 frame (microphone thump).
     * Expected: detected min = E2 only if E2 has a neighbor; comfortable low ≈ C5.
     * Since E2 is isolated, it should be excluded from detected min.
     */
    val LOW_SPIKE_OVER_STABLE_CORE: List<PitchSample> = buildSession {
        isolatedSpike(E2)              // lone low spike — no neighbor
        sustainedNote(C5, 20)
        sustainedNote(E5, 20)
    }

    // ── Scenario 5: Noisy glide through passaggio ─────────────────────────────

    /**
     * A tenor's passaggio is around D4–F4. This session models an exercise where
     * the singer glides through that break, causing pitch instability there.
     * Expected: passaggio estimate lands near the noisy center (≈ E4).
     */
    val NOISY_GLIDE_THROUGH_PASSAGGIO: List<PitchSample> = buildSession {
        sustainedNote(C4, 15)          // stable chest voice below passaggio
        noisyGlide(centerHz = E4, varianceHz = 40f, frames = 30)  // the break
        sustainedNote(G4, 15)          // stable head voice above passaggio
    }

    // ── Scenario 6: Stable mid-range, unstable edges ─────────────────────────

    /**
     * Mostly stable A4 singing, but brief wobbles at both the low extreme (G3)
     * and high extreme (E5) where the singer is straining.
     * Expected: comfortable range ≈ A4 (narrow); detected range spans G3–E5.
     */
    val STABLE_MID_UNSTABLE_EDGES: List<PitchSample> = buildSession {
        noisyGlide(centerHz = G3, varianceHz = 20f, frames = 6)   // wobbly low
        sustainedNote(A4, 40)                                       // dominant mid
        noisyGlide(centerHz = E5, varianceHz = 20f, frames = 6)   // wobbly high
    }

    // ── Scenario 7: Too few valid samples ────────────────────────────────────

    /**
     * Only 10 frames of voiced singing — below the 20-sample minimum.
     * Expected: [SessionReplay.buildProfile] returns null (safe failure).
     */
    val TOO_FEW_VALID_SAMPLES: List<PitchSample> = buildSession {
        sustainedNote(A4, 10)
    }

    // ── Scenario 8: Long silence gaps between short phrases ──────────────────

    /**
     * Three short sung phrases, each separated by a long silence gap.
     * Total voiced frames = 30 (just above the minimum).
     * Expected: profile builds successfully; extremes are from the sung pitches only.
     */
    val LONG_SILENCE_GAPS: List<PitchSample> = buildSession {
        sustainedNote(E4, 10)
        silenceGap(30)
        sustainedNote(A4, 10)
        silenceGap(30)
        sustainedNote(C5, 10)
    }

    // ── Scenario 9: Repeated boundary notes ──────────────────────────────────

    /**
     * Singer returns to the same floor (E3) and ceiling (B4) note many times —
     * confirming them as genuine boundaries, not accidents.
     * Expected: E3 and B4 both pass neighbor validation; detected range = E3–B4.
     */
    val REPEATED_STABLE_BOUNDARIES: List<PitchSample> = buildSession {
        // Three visits to the floor
        sustainedNote(E3, 5); sustainedNote(A4, 10); sustainedNote(E3, 5)
        sustainedNote(G4, 10); sustainedNote(E3, 5)
        // Three visits to the ceiling
        sustainedNote(A4, 10); sustainedNote(B4, 5); sustainedNote(E4, 10)
        sustainedNote(B4, 5); sustainedNote(A4, 10); sustainedNote(B4, 5)
    }

    // ── Scenario 10: Confidence drops near extremes ───────────────────────────

    /**
     * The singer's YIN confidence is already below the acceptance threshold at
     * both the low extreme (E3) and the high extreme (E5) — common at the edges
     * of one's range on a phone microphone where YIN struggles with extreme pitches.
     *
     * The fades start at 0.79f (just below MIN_CONFIDENCE = 0.80) and continue
     * to 0.0f, so EVERY E3 and E5 frame is rejected by [SessionReplay].
     *
     * Expected: only the confident G3–B4 core survives acceptance filtering;
     *   - detected min  ≈ G3 (196 Hz), not E3 (165 Hz)
     *   - detected max  ≈ B4 (494 Hz), not E5 (659 Hz)
     *   - comfortable range stays within G3–B4
     */
    val CONFIDENCE_DROPS_NEAR_EXTREMES: List<PitchSample> = buildSession {
        // Low extreme — all frames start below threshold; none are accepted
        fadingConfidence(hz = E3, frames = 10, startConfidence = 0.79f, endConfidence = 0.0f)
        // Confident core — accepted normally
        sustainedNote(G3, 10, confidence = 1.0f)
        sustainedNote(A4, 20, confidence = 1.0f)
        sustainedNote(B4, 10, confidence = 1.0f)
        // High extreme — all frames start below threshold; none are accepted
        fadingConfidence(hz = E5, frames = 10, startConfidence = 0.79f, endConfidence = 0.0f)
    }

    // ── Scenario 10b: Confidence fades from high to zero at extremes ──────────

    /**
     * A more nuanced version: confidence STARTS at full (1.0) and decays to 0
     * at both extremes.  The first few frames at each extreme ARE accepted
     * (confidence ≥ 0.80), but they are few enough to remain isolated outliers
     * that neighbor-validation may accept or reject depending on how many survive.
     *
     * This fixture is intentionally kept separate from CONFIDENCE_DROPS_NEAR_EXTREMES
     * so tests can be precise about which behaviour they target.
     *
     * With 10 frames decaying from 1.0 → 0.0 over 10 steps:
     *   frame 0: conf=1.0  (accepted)
     *   frame 1: conf=0.9  (accepted)
     *   frame 2: conf=0.8  (accepted — exactly at threshold)
     *   frames 3–9: conf < 0.8 (rejected)
     *
     * So 3 frames at E3 and 3 frames at E5 survive filtering.
     * With 3 neighbors each, both E3 and E5 DO pass neighbor-validation and
     * become the detected extremes — which is the correct conservative behavior.
     */
    val CONFIDENCE_FADES_FROM_HIGH: List<PitchSample> = buildSession {
        fadingConfidence(hz = E3, frames = 10, startConfidence = 1.0f, endConfidence = 0.0f)
        sustainedNote(G3, 10, confidence = 1.0f)
        sustainedNote(A4, 20, confidence = 1.0f)
        sustainedNote(B4, 10, confidence = 1.0f)
        fadingConfidence(hz = E5, frames = 10, startConfidence = 1.0f, endConfidence = 0.0f)
    }

    /**
     * Singer repeatedly returns to B4 — a high note for a mezzo.
     * Expected: comfortable high expands toward B4 because the singer truly
     * spends significant time there.
     */
    val REPEATED_STABLE_TOP_NOTE: List<PitchSample> = buildSession {
        sustainedNote(E4, 5)
        // Keeps returning to B4 — many samples accumulate there
        repeat(6) {
            sustainedNote(A4, 3)
            sustainedNote(B4, 5)   // 30 frames total at B4
        }
        sustainedNote(A4, 5)
    }
}
