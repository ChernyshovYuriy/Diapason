package com.yuriy.diapason.comparison

/**
 * The guided warm-up: five exercises, each with its own length, run back to back.
 *
 * Replaces a silent 5-minute timer that 81 of 96 users skipped after ~28 seconds in
 * production. Two minutes of named, timed steps is short enough to finish and still a
 * real warm-up sequence — breath (lip trills), resonance (humming), agility (scales),
 * register transitions (slides through the passaggio), then the sustained notes the
 * analyzer listens for.
 */
object WarmUpPlan {

    /** Seconds per step, in order: lip trills, humming, five-note scales, octave slides, sustained notes. */
    val STEP_SECONDS: List<Int> = listOf(30, 20, 30, 25, 15)

    val TOTAL_SECONDS: Int = STEP_SECONDS.sum()

    // No personal passaggio hint on the slides step: the only estimate available here comes
    // from the baseline, which is a scale — and a scale gives no reliable passaggio
    // (app/src/test/CAPTURING.md, KNOWN_ISSUES.md). A confidently wrong note would mislead.

    /** When step [index] starts, in seconds from the beginning of the warm-up. */
    fun stepStartSeconds(index: Int): Int = STEP_SECONDS.take(index).sum()

    fun stepEndSeconds(index: Int): Int = stepStartSeconds(index) + STEP_SECONDS[index]

    /** The step running at [elapsedSeconds]; the last step once the warm-up is over. */
    fun stepIndexAt(elapsedSeconds: Int): Int {
        var end = 0
        STEP_SECONDS.forEachIndexed { index, seconds ->
            end += seconds
            if (elapsedSeconds < end) return index
        }
        return STEP_SECONDS.lastIndex
    }

    /** Seconds left in the step running at [elapsedSeconds]. */
    fun secondsLeftInStep(elapsedSeconds: Int): Int =
        (stepEndSeconds(stepIndexAt(elapsedSeconds)) - elapsedSeconds).coerceAtLeast(0)
}
