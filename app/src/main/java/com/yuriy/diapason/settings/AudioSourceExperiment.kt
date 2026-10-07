package com.yuriy.diapason.settings

import android.content.Context
import android.media.MediaRecorder
import kotlin.random.Random

/**
 * A/B test of the microphone audio source, assigned once per install and kept.
 *
 * Hypothesis (from the 2026-10 BigQuery review): `MIC` applies OEM noise suppression and
 * automatic gain control that treat a held sung note as background noise, which would
 * explain why completion ranged from 47% (Infinix) to 78% (Honor) for the same task.
 * `VOICE_RECOGNITION` disables that processing on most devices. Compare completion and
 * insufficient rates by the `audio_source` user property, split by device brand, and
 * keep the winner. `UNPROCESSED` is a possible follow-up arm; two arms keep each one
 * large enough at the current install rate.
 */
class AudioSourceExperiment(context: Context, private val random: Random = Random.Default) {

    enum class Arm(val analyticsValue: String, val audioSource: Int) {
        MIC("mic", MediaRecorder.AudioSource.MIC),
        VOICE_RECOGNITION("voice_recognition", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** This install's arm, assigned 50/50 on first use and stable afterwards. */
    val arm: Arm
        get() {
            prefs.getString(KEY_ARM, null)
                ?.let { stored -> Arm.entries.firstOrNull { it.name == stored } }
                ?.let { return it }
            val assigned = if (random.nextBoolean()) Arm.VOICE_RECOGNITION else Arm.MIC
            prefs.edit().putString(KEY_ARM, assigned.name).apply()
            return assigned
        }

    companion object {
        /** `audio_source` value when the arm's source failed to initialise and MIC was used. */
        const val FALLBACK_ANALYTICS_VALUE = "mic_fallback"

        private const val PREFS_NAME = "diapason_experiments"
        private const val KEY_ARM = "audio_source_arm"
    }
}
