package com.yuriy.diapason.reminder

import android.content.Context

/**
 * Persists the user's choice about the "re-test in 30 days" reminder.
 *
 * - [optedIn] flips true the first time the user accepts and never flips back to false
 *   automatically; the user can opt out from the Results card.
 * - [scheduledAtMs] is the absolute wall-clock time the next reminder should fire. We
 *   store it so the Results card can show "Reminder set for <date>" without having to
 *   query WorkManager.
 */
class ReminderPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var optedIn: Boolean
        get() = prefs.getBoolean(KEY_OPTED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_OPTED_IN, value).apply()

    /** Epoch millis when the next reminder fires, or 0 if none scheduled. */
    var scheduledAtMs: Long
        get() = prefs.getLong(KEY_SCHEDULED_AT_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_SCHEDULED_AT_MS, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "diapason_reminder"
        private const val KEY_OPTED_IN = "opted_in"
        private const val KEY_SCHEDULED_AT_MS = "scheduled_at_ms"
    }
}
