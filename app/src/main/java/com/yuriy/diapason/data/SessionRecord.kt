package com.yuriy.diapason.data

/**
 * Domain-layer representation of a completed, persisted voice-analysis session.
 *
 * This class is intentionally free of Room annotations so that higher layers
 * (ViewModels, UI) never import Room directly. All mapping to/from
 * [com.yuriy.diapason.data.db.SessionEntity] lives in [SessionMapper].
 */
data class SessionRecord(
    val id: String,
    val timestampMs: Long,
    val durationSeconds: Float,

    /** Absolute lowest pitch detected (neighbour-validated). */
    val detectedMinHz: Float,

    /** Absolute highest pitch detected (neighbour-validated). */
    val detectedMaxHz: Float,

    /** Lower bound of comfortable range (P20 of accepted samples). */
    val comfortableLowHz: Float,

    /** Upper bound of comfortable range (P80 of accepted samples). */
    val comfortableHighHz: Float,

    /** Estimated register-break frequency. */
    val passaggioHz: Float,

    val sampleCount: Int,

    /**
     * Non-translatable English name of the best Fach match, e.g. "Lyric Soprano".
     * Null when the classifier produced no result (shouldn't happen in normal flow
     * but kept nullable for robustness).
     */
    val topFachKey: String?,
    val topFachScore: Int?,
    val topFachMaxScore: Int?,

    /** True if the session was saved despite marginal quality. */
    val isPartial: Boolean,
)
