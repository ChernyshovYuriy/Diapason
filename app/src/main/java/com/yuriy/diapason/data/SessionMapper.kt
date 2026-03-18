package com.yuriy.diapason.data

import com.yuriy.diapason.data.db.SessionEntity

/**
 * Maps [SessionEntity] → [SessionRecord].
 *
 * Internal visibility keeps the mapping inside the `data` package and prevents
 * the rest of the app from coupling to Room types.
 */
internal fun SessionEntity.toDomain() = SessionRecord(
    id = id,
    timestampMs = timestampMs,
    durationSeconds = durationSeconds,
    detectedMinHz = detectedMinHz,
    detectedMaxHz = detectedMaxHz,
    comfortableLowHz = comfortableLowHz,
    comfortableHighHz = comfortableHighHz,
    passaggioHz = passaggioHz,
    sampleCount = sampleCount,
    topFachKey = topFachKey,
    topFachScore = topFachScore,
    topFachMaxScore = topFachMaxScore,
    isPartial = isPartial,
)

/** Maps [SessionRecord] → [SessionEntity]. */
internal fun SessionRecord.toEntity() = SessionEntity(
    id = id,
    timestampMs = timestampMs,
    durationSeconds = durationSeconds,
    detectedMinHz = detectedMinHz,
    detectedMaxHz = detectedMaxHz,
    comfortableLowHz = comfortableLowHz,
    comfortableHighHz = comfortableHighHz,
    passaggioHz = passaggioHz,
    sampleCount = sampleCount,
    topFachKey = topFachKey,
    topFachScore = topFachScore,
    topFachMaxScore = topFachMaxScore,
    isPartial = isPartial,
)
