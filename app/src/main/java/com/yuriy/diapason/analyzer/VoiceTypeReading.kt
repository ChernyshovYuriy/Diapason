package com.yuriy.diapason.analyzer

/**
 * What a recording can honestly say about voice type — the way a voice teacher would put it
 * after hearing a range, not a verdict.
 *
 * A phone recording measures pitch: where the voice sits comfortably, how far it reaches,
 * where it shifts register. Fach also rests on timbre, weight and how the voice carries,
 * which pitch can't hear. Replaying real 2.7.0 sessions (`CombinedProfileReplayTest`) showed
 * what that means in practice: within a voice group, two independent sets of takes from the
 * same singer named the same top Fach only ~24% of the time, and 81% of results had the
 * runner-up within one point. So the reading names the voice family (or the two it sits
 * between) and the Fach types whose ranges fit, instead of crowning one Fach.
 */
data class VoiceTypeReading(
    /**
     * The family the range points to ([FachDefinition.categoryRes]); or two, higher first,
     * when the range sits between them.
     */
    val familyRes: List<Int>,
    /** Fach types whose ranges fit about as well as the best one, best first. */
    val fachTypes: List<FachDefinition>,
) {
    val isBetween: Boolean get() = familyRes.size > 1
}

object VoiceTypeReader {

    /**
     * A family is named alone only when its best Fach beats every other family's best by
     * this many points; otherwise the reading is "between" the top two.
     */
    const val FAMILY_MARGIN = 2

    /** Fach types within this many points of the best one count as fitting the range. */
    const val COMPATIBLE_POINTS = 1

    /** At most this many Fach types are listed; more would stop meaning anything. */
    const val MAX_FACH_TYPES = 3

    /** Reads a ranked [FachClassifier.classify] result. Null for an empty list. */
    fun read(matches: List<FachMatch>, familyMargin: Int = FAMILY_MARGIN): VoiceTypeReading? {
        val top = matches.firstOrNull() ?: return null
        // matches is sorted best first, so each family's first entry is its best.
        val families = matches.distinctBy { it.fach.categoryRes }
        val runnerUpFamily = families.getOrNull(1)
        val familyRes = if (runnerUpFamily == null || top.score - runnerUpFamily.score >= familyMargin) {
            listOf(top.fach.categoryRes)
        } else {
            listOf(top, runnerUpFamily)
                .sortedBy { tableOrder(it.fach) }
                .map { it.fach.categoryRes }
        }
        val fachTypes = matches
            .filter { it.score >= top.score - COMPATIBLE_POINTS }
            .take(MAX_FACH_TYPES)
            .map { it.fach }
        return VoiceTypeReading(familyRes, fachTypes)
    }

    /** [ALL_FACH] runs from the highest voice to the lowest within each group. */
    private fun tableOrder(fach: FachDefinition): Int = ALL_FACH.indexOf(fach)
}
