package com.yuriy.diapason.analyzer

/**
 * Flags a take whose pitches fit the other half of the Fach table clearly better than the
 * half the singer chose, so the app can ask before ranking it within the wrong half.
 *
 * Why it exists: replaying 2.7.0 sessions (`CombinedProfileReplayTest`) found 14% of
 * Male and 21.5% of Female takes fitting the other group by this margin — shared phones
 * with the switch left on the previous singer, falsetto, children. Ranked within the
 * chosen half, they landed on its edge type every time: countertenor won 29% of male
 * results (comfortable F#4–F#5) and contralto 24% of female results (G3–E4, a tenor's range).
 *
 * The margin keeps ordinary overlap quiet: the halves share an octave or more, so a
 * one-point cross-group lead is a near-tie, not a mismatch.
 */
object VoiceGroupCheck {

    /** Points (of 14) by which the other group's best Fach must beat the chosen group's. */
    const val MARGIN = 3

    /**
     * The choice the take fits clearly better than [choice], or null when it fits [choice]
     * (or [choice] is "Not sure", which already ranks all 19 Fach).
     */
    fun betterFittingChoice(profile: VoiceProfile, choice: VoiceGroupChoice): VoiceGroupChoice? {
        val group = choice.group ?: return null
        val other = if (group == VoiceGroup.MALE) VoiceGroup.FEMALE else VoiceGroup.MALE
        val own = FachClassifier.classify(profile, group).firstOrNull()?.score ?: return null
        val best = FachClassifier.classify(profile, other).firstOrNull()?.score ?: return null
        return if (best - own >= MARGIN) VoiceGroupChoice.entries.first { it.group == other } else null
    }
}
