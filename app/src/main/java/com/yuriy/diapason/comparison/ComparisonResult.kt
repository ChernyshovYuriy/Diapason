package com.yuriy.diapason.comparison

import com.yuriy.diapason.analyzer.FachMatch
import com.yuriy.diapason.analyzer.VoiceProfile

/**
 * A delta value between two voice sessions.
 *
 * [deltaHz] is positive if the "after" value is higher than "before".
 * [expanded] is true if a range boundary moved in the direction that widens range
 * (low boundary went down OR high boundary went up).
 */
data class HzDelta(
    val beforeHz: Float,
    val afterHz: Float,
) {
    val deltaHz: Float get() = afterHz - beforeHz

    /** True if the absolute difference is large enough to be meaningful (>= 1 semitone ≈ 6%). */
    val isMeaningful: Boolean get() = kotlin.math.abs(deltaHz / beforeHz) >= 0.059f
}

/**
 * Pure comparison between a "before warm-up" and "after warm-up" [VoiceProfile].
 *
 * All fields are computed at construction time. No Room/database annotations.
 * Both profiles are retained so the UI can show raw values alongside deltas.
 *
 * Conservative wording note: callers must not imply that warm-up *always* improves
 * range or that changes are physiological — just present as two session measurements.
 */
data class ComparisonResult(
    val before: VoiceProfile,
    val after: VoiceProfile,

    val beforeTopMatch: FachMatch?,
    val afterTopMatch: FachMatch?,

    /** Lower bound of comfortable range (P20). */
    val comfortableLow: HzDelta,

    /** Upper bound of comfortable range (P80). */
    val comfortableHigh: HzDelta,

    /** Absolute lowest detected pitch. */
    val detectedMin: HzDelta,

    /** Absolute highest detected pitch. */
    val detectedMax: HzDelta,

    /** Passaggio — only meaningful if both sessions have sufficient samples. */
    val passaggio: HzDelta?,
) {
    /**
     * True if the comfortable range widened (low went down OR high went up by a meaningful amount).
     * Not exposed as a "improvement" claim — just a factual observation.
     */
    val comfortableRangeWidened: Boolean
        get() = (comfortableLow.deltaHz < -comfortableLow.beforeHz * 0.059f) ||
                (comfortableHigh.deltaHz > comfortableHigh.beforeHz * 0.059f)

    /**
     * True if the detected range widened in either direction by a meaningful amount.
     */
    val detectedRangeWidened: Boolean
        get() = (detectedMin.deltaHz < -detectedMin.beforeHz * 0.059f) ||
                (detectedMax.deltaHz > detectedMax.beforeHz * 0.059f)

    companion object {
        /**
         * Compute a [ComparisonResult] from two completed sessions.
         * Passaggio delta is omitted when either session has too few samples to produce
         * a reliable estimate (fewer than 30 samples).
         */
        fun compute(
            before: VoiceProfile,
            beforeTopMatch: FachMatch?,
            after: VoiceProfile,
            afterTopMatch: FachMatch?,
        ): ComparisonResult {
            val passagio = if (before.sampleCount >= 30 && after.sampleCount >= 30) {
                HzDelta(before.estimatedPassaggioHz, after.estimatedPassaggioHz)
            } else null

            return ComparisonResult(
                before = before,
                after = after,
                beforeTopMatch = beforeTopMatch,
                afterTopMatch = afterTopMatch,
                comfortableLow = HzDelta(before.comfortableLowHz, after.comfortableLowHz),
                comfortableHigh = HzDelta(before.comfortableHighHz, after.comfortableHighHz),
                detectedMin = HzDelta(before.detectedMinHz, after.detectedMinHz),
                detectedMax = HzDelta(before.detectedMaxHz, after.detectedMaxHz),
                passaggio = passagio,
            )
        }
    }
}
