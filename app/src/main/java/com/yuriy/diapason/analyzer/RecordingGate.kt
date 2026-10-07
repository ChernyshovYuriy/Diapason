package com.yuriy.diapason.analyzer

import kotlin.math.ceil

/**
 * Progress toward the [MIN_ACCEPTED_SAMPLES] gate while recording, shared by the Analyze
 * and warm-up recording screens.
 *
 * Why it exists: in production 24% of attempts ended "insufficient", 40% of those with
 * 20–39 samples — users pressed Stop without knowing they were seconds short. So the
 * screens show progress, and a Stop pressed below the gate first says how much longer to
 * sing instead of discarding the take.
 */
object RecordingGate {

    const val MIN_SAMPLES = MIN_ACCEPTED_SAMPLES

    /** Below this many samples the session's own rate is too noisy to estimate from. */
    private const val MIN_SAMPLES_FOR_ESTIMATE = 3

    /** Longest estimate shown; beyond it the number would mean little. */
    private const val MAX_ESTIMATE_SECONDS = 60

    fun isEnough(sampleCount: Int): Boolean = sampleCount >= MIN_SAMPLES

    /** 0..1 progress toward the gate. */
    fun progress(sampleCount: Int): Float = (sampleCount.toFloat() / MIN_SAMPLES).coerceIn(0f, 1f)

    /**
     * Seconds of singing still needed at this session's own accepted-sample rate, rounded
     * up and capped at [MAX_ESTIMATE_SECONDS]; 0 once the gate is met; null when there's
     * too little data to estimate a rate (e.g. nothing detected yet).
     */
    fun secondsRemaining(sampleCount: Int, elapsedSeconds: Float): Int? {
        if (isEnough(sampleCount)) return 0
        if (sampleCount < MIN_SAMPLES_FOR_ESTIMATE || elapsedSeconds <= 0f) return null
        val samplesPerSecond = sampleCount / elapsedSeconds
        val seconds = ceil((MIN_SAMPLES - sampleCount) / samplesPerSecond).toInt()
        return seconds.coerceIn(1, MAX_ESTIMATE_SECONDS)
    }
}
