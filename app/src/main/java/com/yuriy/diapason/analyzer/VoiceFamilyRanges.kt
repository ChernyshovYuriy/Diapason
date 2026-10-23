package com.yuriy.diapason.analyzer

/**
 * One voice family's span across all its Fach types — what a singer's measured range is
 * drawn against on the Results screen, the way a textbook charts voice ranges.
 */
data class FamilyRange(
    val categoryRes: Int,
    val rangeMinHz: Float,
    val rangeMaxHz: Float,
    val tessituraMinHz: Float,
    val tessituraMaxHz: Float,
)

object VoiceFamilyRanges {

    /**
     * The families of [group] (all seven for null), highest first, each spanning the full
     * range and tessitura of its Fach types in [ALL_FACH].
     */
    fun of(group: VoiceGroup?): List<FamilyRange> =
        ALL_FACH
            .filter { group == null || it.voiceGroup == group }
            .groupBy { it.categoryRes } // keeps ALL_FACH's high-to-low order
            .map { (categoryRes, fach) ->
                FamilyRange(
                    categoryRes = categoryRes,
                    rangeMinHz = fach.minOf { it.rangeMinHz },
                    rangeMaxHz = fach.maxOf { it.rangeMaxHz },
                    tessituraMinHz = fach.minOf { it.tessituraMinHz },
                    tessituraMaxHz = fach.maxOf { it.tessituraMaxHz },
                )
            }
}
