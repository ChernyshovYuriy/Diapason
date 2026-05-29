package com.yuriy.diapason.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuriy.diapason.MainActivity
import com.yuriy.diapason.R
import com.yuriy.diapason.analytics.AppAnalytics
import com.yuriy.diapason.logging.AppLogger

/**
 * Fires once per scheduled reminder. Posts a single notification that taps through to
 * [MainActivity]; WorkManager guarantees a single delivery and survives reboots.
 *
 * Clears [ReminderPreferences.scheduledAtMs] after firing so the Results card knows the
 * reminder has been delivered (the user can opt back in to schedule another).
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        // The user may have revoked the runtime permission since opt-in; if so we drop
        // the notification silently. The Results card will re-prompt next visit.
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (!canPost) {
            AppLogger.w("$TAG skipped — POST_NOTIFICATIONS not granted")
            ReminderPreferences(context).scheduledAtMs = 0L
            return Result.success()
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            PENDING_INTENT_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.reminder_notification_body)),
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        AppAnalytics.reminderNotificationPosted()
        AppLogger.i("$TAG posted reminder notification")

        ReminderPreferences(context).scheduledAtMs = 0L
        return Result.success()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "diapason_reminder"

        private const val TAG = "ReminderWorker"
        private const val NOTIFICATION_ID = 1001
        private const val PENDING_INTENT_REQUEST_CODE = 2001

        /** Used by [ReminderScheduler] to enqueue / cancel the unique work request. */
        const val UNIQUE_WORK_NAME = "diapason_retest_reminder"
    }

    /**
     * Creates (and registers) the notification channel for reminder notifications.
     * Idempotent — safe to call from [com.yuriy.diapason.MainApp.onCreate] every launch.
     */
    object Channel {
        fun ensureRegistered(context: Context) {
            // NotificationChannel was introduced in API 26; minSdk is 24 so we still need
            // the version guard. NotificationManagerCompat would silently no-op on older
            // versions but we want explicit control.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val channel = android.app.NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
