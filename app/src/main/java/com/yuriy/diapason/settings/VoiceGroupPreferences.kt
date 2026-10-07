package com.yuriy.diapason.settings

import android.content.Context
import com.yuriy.diapason.analyzer.VoiceGroupChoice

/**
 * The last Male · Female · Not sure choice. It pre-selects the switch shown above Start on
 * every recording — a default, not a hidden global setting, so a teacher or a shared phone
 * sees and changes it per singer. Null until the first-run prompt has been answered.
 */
class VoiceGroupPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var choice: VoiceGroupChoice?
        get() = prefs.getString(KEY_VOICE_GROUP, null)
            ?.let { stored -> VoiceGroupChoice.entries.firstOrNull { it.name == stored } }
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_VOICE_GROUP) else editor.putString(KEY_VOICE_GROUP, value.name)
            editor.apply()
        }

    companion object {
        private const val PREFS_NAME = "diapason_voice_group"
        // Stored values are VoiceGroupChoice names ("MALE" / "FEMALE" / "UNSURE").
        private const val KEY_VOICE_GROUP = "voice_group"
    }
}
