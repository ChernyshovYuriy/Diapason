package com.yuriy.diapason.data

import com.yuriy.diapason.analyzer.TimedProfile
import com.yuriy.diapason.analyzer.VoiceGroupChoice
import com.yuriy.diapason.analyzer.VoiceProfile
import com.yuriy.diapason.analyzer.VoiceProfileAggregator
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
    // An unrecognised stored name (none exist today) is treated like a pre-v2 row.
    voiceGroupChoice = voiceGroup?.let { stored -> VoiceGroupChoice.entries.firstOrNull { it.name == stored } },
    recordingKind = recordingKind?.let { stored -> RecordingKind.entries.firstOrNull { it.name == stored } },
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
    voiceGroup = voiceGroupChoice?.name,
    recordingKind = recordingKind?.name,
)

/** The stored profile with its recording time, as [VoiceProfileAggregator] consumes it. */
fun SessionRecord.toTimedProfile() = TimedProfile(
    timestampMs = timestampMs,
    profile = VoiceProfile(
        detectedMinHz = detectedMinHz,
        detectedMaxHz = detectedMaxHz,
        comfortableLowHz = comfortableLowHz,
        comfortableHighHz = comfortableHighHz,
        estimatedPassaggioHz = passaggioHz,
        sampleCount = sampleCount,
        durationSeconds = durationSeconds,
    ),
)

/**
 * The sessions the combined voice profile may draw on for the current [choice]: only those
 * recorded with that same choice. Sessions saved before the choice was stored (null) are
 * never included — they might be a different singer or the other voice group.
 */
fun List<SessionRecord>.recordedWith(choice: VoiceGroupChoice): List<SessionRecord> =
    filter { it.voiceGroupChoice == choice }

/** How a session was recorded; rows from before that was stored were free recordings. */
val SessionRecord.kind: RecordingKind get() = recordingKind ?: RecordingKind.FREE

/**
 * The sessions comparable with the most recent one: the same kind of recording. A singer who
 * takes the range test is compared only with their range tests — free singing measures
 * whatever happened to be sung, so mixing the two would move the numbers for no vocal reason.
 */
fun List<SessionRecord>.comparableWithLatest(): List<SessionRecord> {
    val latest = maxByOrNull { it.timestampMs } ?: return emptyList()
    return filter { it.kind == latest.kind }
}
