package com.yuriy.diapason.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yuriy.diapason.logging.AppLogger
import java.util.concurrent.TimeUnit

/**
 * Schedules a single one-shot reminder via WorkManager.
 *
 * 7-day cadence: singers commonly think in weekly practice routines, so a weekly nudge
 * is more likely to be acted on than a monthly one — and the user can re-test more
 * often if they want by simply opening the app, which auto-pushes the next reminder
 * to a week from then via [bumpIfOptedIn].
 */
class ReminderScheduler(private val context: Context) {

    private val prefs = ReminderPreferences(context)

    val isOptedIn: Boolean get() = prefs.optedIn
    val scheduledAtMs: Long get() = prefs.scheduledAtMs

    /**
     * Schedules a reminder [REMINDER_DELAY_DAYS] from now, replacing any pending one.
     * Marks the user as opted in.
     */
    fun scheduleOrReplace() {
        val delayMs = TimeUnit.DAYS.toMillis(REMINDER_DELAY_DAYS)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ReminderWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )

        prefs.optedIn = true
        prefs.scheduledAtMs = System.currentTimeMillis() + delayMs
        AppLogger.i("$TAG scheduled reminder for ${prefs.scheduledAtMs}")
    }

    /**
     * Cancels any pending reminder and clears the opt-in flag. Called when the user
     * dismisses an existing reminder from the Results card.
     */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(ReminderWorker.UNIQUE_WORK_NAME)
        prefs.optedIn = false
        prefs.scheduledAtMs = 0L
        AppLogger.i("$TAG reminder cancelled")
    }

    /**
     * Called after every completed analysis. If the user has previously opted in,
     * pushes the next reminder out to [REMINDER_DELAY_DAYS] from now — so the reminder
     * is always "one week from your last session", never stale.
     * No-op if the user has not opted in.
     */
    fun bumpIfOptedIn() {
        if (!prefs.optedIn) return
        scheduleOrReplace()
    }

    companion object {
        private const val TAG = "ReminderScheduler"
        const val REMINDER_DELAY_DAYS = 7L
    }
}
