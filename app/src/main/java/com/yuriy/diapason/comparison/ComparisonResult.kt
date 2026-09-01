package com.yuriy.diapason.comparison

import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.FachMatch
import com.yuriy.diapason.analyzer.VoiceProfile
import kotlin.math.abs
import kotlin.math.ln

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

    /**
     * True if the change is at least one semitone, computed as a true log-ratio
     * distance (`12 * log2(afterHz / beforeHz)`), not a flat percentage.
     *
     * A flat percentage is not a symmetric proxy for "one semitone": a semitone is
     * a fixed *ratio* (2^(1/12) ≈ 1.0595), so the same +/-5.9% used before this fix
     * corresponded to different true semitone distances depending on direction
     * (-5.9% ≈ -1.05 semitones, +5.9% ≈ +0.99 semitones — just under a full
     * semitone) — found during a second, separate adversarial audit pass. Using
     * the actual semitone formula, already the standard elsewhere in this app
     * (`FachClassifier`'s semitone-space calculations), removes the asymmetry
     * entirely rather than picking a better-tuned percentage.
     */
    val isMeaningful: Boolean
        get() {
            if (beforeHz <= 0f || afterHz <= 0f) return false
            val semitones = 12.0 * ln(afterHz / beforeHz.toDouble()) / ln(2.0)
            return abs(semitones) >= 1.0
        }
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
     *
     * Reuses [HzDelta.isMeaningful] rather than re-deriving the same threshold — this used
     * to duplicate the formula independently (with its own copy of the old flat-percentage
     * threshold, and without [HzDelta.isMeaningful]'s zero/negative-Hz guard), which is
     * exactly the kind of hand-mirrored constant that can silently drift out of sync.
     */
    val comfortableRangeWidened: Boolean
        get() = (comfortableLow.deltaHz < 0f && comfortableLow.isMeaningful) ||
                (comfortableHigh.deltaHz > 0f && comfortableHigh.isMeaningful)

    /**
     * True if the detected range widened in either direction by a meaningful amount.
     * See [comfortableRangeWidened] for why this reuses [HzDelta.isMeaningful].
     */
    val detectedRangeWidened: Boolean
        get() = (detectedMin.deltaHz < 0f && detectedMin.isMeaningful) ||
                (detectedMax.deltaHz > 0f && detectedMax.isMeaningful)

    companion object {
        /**
         * Compute a [ComparisonResult] from two completed sessions.
         *
         * Passaggio delta is omitted when either session has fewer than
         * [FachClassifier.PASSAGGIO_MIN_SAMPLES] — below that, [FachClassifier.estimatePassaggio]
         * itself falls back to a plain average rather than the windowed algorithm, so the two
         * sides wouldn't be comparable numbers. In current production builds
         * `VoiceAnalyzer.MIN_ACCEPTED_SAMPLES` (40) is stricter than this threshold (30), so no
         * real session can ever fail this check today — but the two constants answer different
         * questions and have already diverged once, so this stays tied to the one that actually
         * governs whether the passaggio estimate itself is meaningful, not to whatever the outer
         * sample gate happens to be.
         */
        fun compute(
            before: VoiceProfile,
            beforeTopMatch: FachMatch?,
            after: VoiceProfile,
            afterTopMatch: FachMatch?,
        ): ComparisonResult {
            val passagio = if (
                before.sampleCount >= FachClassifier.PASSAGGIO_MIN_SAMPLES &&
                after.sampleCount >= FachClassifier.PASSAGGIO_MIN_SAMPLES
            ) {
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
