package com.yuriy.diapason.analyzer

/** A saved session's profile with the time it was recorded. */
data class TimedProfile(val timestampMs: Long, val profile: VoiceProfile)

/**
 * The user's voice profile combined across recent sessions, classified once.
 *
 * [consistentCount] is how many of the [sessionCount] sessions, classified on their own,
 * land in the same family as the combined result — shown as "consistent in k of n".
 */
data class CombinedVoiceProfile(
    val profile: VoiceProfile,
    val matches: List<FachMatch>,
    val summary: ResultSummary,
    val sessionCount: Int,
    val consistentCount: Int,
)

/**
 * Combines recent sessions into one profile so the headline result doesn't change with
 * every take. In production a re-running user got a different Fach on 82% of
 * consecutive takes; a per-dimension median over several takes damps the single
 * unusual one (a take that skipped the top, or wandered into falsetto) instead of
 * letting it decide the answer.
 *
 * Window chosen by the author (2026-10-07): the last [MAX_SESSIONS] sessions within
 * [WINDOW_DAYS] days — no other window, and older sessions never count, so a voice that
 * has genuinely changed isn't held back by old recordings.
 */
object VoiceProfileAggregator {

    const val MAX_SESSIONS = 5
    const val WINDOW_DAYS = 30L

    /** One session is just the current result; combining needs at least two. */
    const val MIN_SESSIONS = 2

    private const val WINDOW_MS = WINDOW_DAYS * 24 * 60 * 60 * 1000

    /** The newest [MAX_SESSIONS] sessions recorded within [WINDOW_DAYS] before [nowMs]. */
    fun selectRecent(sessions: List<TimedProfile>, nowMs: Long): List<VoiceProfile> =
        sessions
            .filter { it.timestampMs >= nowMs - WINDOW_MS }
            .sortedByDescending { it.timestampMs }
            .take(MAX_SESSIONS)
            .map { it.profile }

    /**
     * Per-dimension median of [profiles]. Sample counts and durations are summed, since
     * the combined profile rests on all of them. Requires a non-empty list.
     */
    fun medianProfile(profiles: List<VoiceProfile>): VoiceProfile {
        require(profiles.isNotEmpty()) { "medianProfile needs at least one profile" }
        fun median(select: (VoiceProfile) -> Float): Float {
            val sorted = profiles.map(select).sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
        }
        return VoiceProfile(
            detectedMinHz = median { it.detectedMinHz },
            detectedMaxHz = median { it.detectedMaxHz },
            comfortableLowHz = median { it.comfortableLowHz },
            comfortableHighHz = median { it.comfortableHighHz },
            estimatedPassaggioHz = median { it.estimatedPassaggioHz },
            sampleCount = profiles.sumOf { it.sampleCount },
            durationSeconds = profiles.sumOf { it.durationSeconds.toDouble() }.toFloat(),
        )
    }

    /**
     * Combines the recent sessions in [sessions] and classifies the result within
     * [group]. Returns null when fewer than [MIN_SESSIONS] sessions fall in the window.
     */
    fun combine(sessions: List<TimedProfile>, nowMs: Long, group: VoiceGroup?): CombinedVoiceProfile? {
        val recent = selectRecent(sessions, nowMs)
        if (recent.size < MIN_SESSIONS) return null
        val combined = medianProfile(recent)
        val matches = FachClassifier.classify(combined, group)
        val summary = FachClassifier.summarize(matches) ?: return null
        val consistent = recent.count { session ->
            FachClassifier.classify(session, group).firstOrNull()?.fach?.categoryRes == summary.familyRes
        }
        return CombinedVoiceProfile(
            profile = combined,
            matches = matches,
            summary = summary,
            sessionCount = recent.size,
            consistentCount = consistent,
        )
    }
}
