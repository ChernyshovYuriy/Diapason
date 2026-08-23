package com.yuriy.diapason.reminder

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.yuriy.diapason.logging.AppLogger

/**
 * Manually drives WorkManager's one-time init instead of relying on the androidx.startup
 * `WorkManagerInitializer` ContentProvider (disabled in the manifest — see the comment there).
 *
 * Some Android 14 device firmwares report `SDK_INT` 34 but ship a `framework.jar` missing
 * `JobScheduler.forNamespace(String)`, which WorkManager calls unconditionally once
 * `SDK_INT >= 34`. When that happens inside the auto-init ContentProvider during
 * `handleBindApplication`, the resulting `NoSuchMethodError` is unrecoverable and kills the
 * process before `MainApp.onCreate()` even runs — every launch, for every feature, not just
 * reminders. Calling [initialize] from `MainApp.onCreate()` instead lets that failure be caught
 * here; [ReminderScheduler] checks [isAvailable] and no-ops instead of calling into a
 * `WorkManager` that never started.
 */
object WorkManagerSupport {

    @Volatile
    var isAvailable: Boolean = false
        private set

    fun initialize(context: Context) {
        try {
            WorkManager.initialize(context.applicationContext, Configuration.Builder().build())
            isAvailable = true
        } catch (t: Throwable) {
            // Broad catch is deliberate: the failure mode is a device/firmware defect
            // (NoSuchMethodError observed in the wild on Android 14), not a checked exception
            // WorkManager's API contract documents, so no narrower catch is guaranteed to cover
            // the next OEM's variant.
            AppLogger.e("WorkManagerSupport failed to initialize WorkManager", t)
            isAvailable = false
        }
    }
}
