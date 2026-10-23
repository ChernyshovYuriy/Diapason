package com.yuriy.diapason.analyzer

/**
 * The guided range test: the same four short exercises every session, each measuring what a
 * voice teacher uses it for.
 *
 * Why it exists: free singing made results incomparable between sessions. Replaying 2.7.0
 * data, even five-session medians moved 2–3 semitones — people simply sang something
 * different each time — and the voice-type reading changed ~40% of the time
 * (KNOWN_ISSUES #17). A teacher never judges range from whatever a student happens to sing;
 * they run a fixed exercise so this week's numbers can be compared with last week's.
 *
 *  1. A comfortable sustained note, near speaking pitch.
 *  2. A simple song in an easy key — where the voice lives: the comfortable range.
 *  3. A slow slide up to the highest *easy* note.
 *  4. A slow slide down to the lowest *clear* note.
 *
 * The passaggio comes from the two slides only: a slow siren is where a voice flips or
 * wobbles at its break, while a melody's constant ups and downs would swamp the
 * estimator's reversal score ([FachClassifier.estimatePassaggio]).
 */
object RangeTest {

    enum class Step(val seconds: Int) {
        ComfortableNote(8),
        Song(20),
        SlideUp(10),
        SlideDown(10),
    }

    val STEPS: List<Step> = Step.entries

    val TOTAL_SECONDS: Int = STEPS.sumOf { it.seconds }

    /** How often a running test checks which step it's in; well under a second so the countdown reads true. */
    const val TICK_MS = 250L

    /** Fewer than this many samples from the comfortable-range steps falls back to all samples. */
    internal const val MIN_COMFORTABLE_SAMPLES = 20

    fun stepStartSeconds(step: Step): Int = STEPS.takeWhile { it != step }.sumOf { it.seconds }

    fun stepEndSeconds(step: Step): Int = stepStartSeconds(step) + step.seconds

    /** The step running at [elapsedSeconds]; the last one once the test is over. */
    fun stepAt(elapsedSeconds: Float): Step =
        STEPS.firstOrNull { elapsedSeconds < stepEndSeconds(it) } ?: STEPS.last()

    /** Whole seconds left in the step running at [elapsedSeconds]. */
    fun secondsLeftInStep(elapsedSeconds: Float): Int =
        (stepEndSeconds(stepAt(elapsedSeconds)) - elapsedSeconds).toInt().coerceAtLeast(0)

    /**
     * Builds the profile from a test's accepted [pitches], each recorded [timesMs] after the
     * start: extremes from everything, the comfortable range from the note and the song,
     * the passaggio from the slides. Each falls back to all samples when its own steps
     * produced too few — someone who stopped early still gets a profile.
     */
    fun profileOf(pitches: List<Float>, timesMs: List<Long>, durationSeconds: Float): VoiceProfile {
        require(pitches.size == timesMs.size) { "one time per pitch" }
        fun samplesOf(vararg steps: Step): List<Float> = pitches.filterIndexed { i, _ ->
            stepAt(timesMs[i] / 1000f) in steps
        }

        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(pitches)
        val comfortableSamples = samplesOf(Step.ComfortableNote, Step.Song)
            .takeIf { it.size >= MIN_COMFORTABLE_SAMPLES } ?: pitches
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(comfortableSamples)
        val slideSamples = samplesOf(Step.SlideUp, Step.SlideDown)
            .takeIf { it.size >= FachClassifier.PASSAGGIO_MIN_SAMPLES } ?: pitches
        val passaggio = FachClassifier.estimatePassaggio(slideSamples)

        return VoiceProfile(
            detectedMinHz = detectedMin,
            detectedMaxHz = detectedMax,
            comfortableLowHz = comfortableLow,
            comfortableHighHz = comfortableHigh,
            estimatedPassaggioHz = passaggio,
            sampleCount = pitches.size,
            durationSeconds = durationSeconds,
        )
    }
}
