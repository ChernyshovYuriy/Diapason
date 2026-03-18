package com.yuriy.diapason.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted representation of one completed voice-analysis session.
 *
 * Only sessions where [VoiceAnalyzer.stop] returns a non-null [VoiceProfile] are
 * ever saved, so every row in this table represents a valid, complete analysis.
 *
 * All Hz columns are NOT NULL because domain validation already happened before
 * the repository is called.
 *
 * [topFachKey] stores the non-translatable English name of the best Fach match
 * (e.g. "Lyric Soprano"). Using the English string — rather than a row index —
 * makes the value human-readable in a DB inspector, version-stable, and
 * independent of the ordering of [ALL_FACH].  It is nullable because a future
 * code path might legitimately produce a profile without a Fach result.
 */
@Entity(tableName = "sessions")
data class SessionEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Epoch millis — primary sort key for history list. */
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,

    @ColumnInfo(name = "duration_s")
    val durationSeconds: Float,

    // ── Absolute range (neighbour-validated) ──────────────────────────────

    @ColumnInfo(name = "detected_min_hz")
    val detectedMinHz: Float,

    @ColumnInfo(name = "detected_max_hz")
    val detectedMaxHz: Float,

    // ── Comfortable range (P20–P80 tessitura) ─────────────────────────────

    @ColumnInfo(name = "comfortable_low_hz")
    val comfortableLowHz: Float,

    @ColumnInfo(name = "comfortable_high_hz")
    val comfortableHighHz: Float,

    // ── Register break ────────────────────────────────────────────────────

    @ColumnInfo(name = "passaggio_hz")
    val passaggioHz: Float,

    // ── Quality metrics ───────────────────────────────────────────────────

    @ColumnInfo(name = "sample_count")
    val sampleCount: Int,

    // ── Classification result ─────────────────────────────────────────────

    /** Non-translatable English Fach name. Null if classification produced no result. */
    @ColumnInfo(name = "top_fach_key")
    val topFachKey: String?,

    @ColumnInfo(name = "top_fach_score")
    val topFachScore: Int?,

    @ColumnInfo(name = "top_fach_max_score")
    val topFachMaxScore: Int?,

    /**
     * Reserved for future use: marks sessions saved with marginal quality
     * (e.g. very short duration or low sample count) so the UI can warn the user.
     */
    @ColumnInfo(name = "is_partial", defaultValue = "0")
    val isPartial: Boolean = false,
)
