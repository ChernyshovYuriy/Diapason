package com.yuriy.diapason

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yuriy.diapason.analytics.AppAnalytics
import com.yuriy.diapason.consent.PrivacyConsentPreferences
import com.yuriy.diapason.data.db.DiapasonDatabase
import com.yuriy.diapason.data.repository.SessionRepository
import com.yuriy.diapason.data.repository.SessionRepositoryImpl
import com.yuriy.diapason.logging.AppLogger
import com.yuriy.diapason.reminder.ReminderWorker
import com.yuriy.diapason.reminder.WorkManagerSupport
import java.util.Locale

class MainApp : Application() {

    /**
     * Application-scoped repository. Initialised lazily on first access so that
     * the database is not opened until it is actually needed.
     *
     * Tests inject a fake repository via constructor parameters on the ViewModel
     */
    val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(
            DiapasonDatabase.getInstance(
                applicationContext
            ).sessionDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.setDebug(isDebug(applicationContext))
        FirebaseApp.initializeApp(applicationContext)
        AppAnalytics.init(applicationContext)
        // Analytics/Crashlytics collection defaults to off via the manifest meta-data
        // (see AndroidManifest.xml) so nothing is collected before the user has agreed
        // to the privacy policy. A returning user who already agreed gets collection
        // turned back on here; a first-time user gets it turned on later, when
        // PrivacyConsentGate calls setCollectionEnabled(true) after they tap Agree.
        if (PrivacyConsentPreferences(applicationContext).granted) {
            setCollectionEnabled(enabled = true)
        }
        WorkManagerSupport.initialize(applicationContext)
        ReminderWorker.Channel.ensureRegistered(applicationContext)
    }

    private fun isDebug(context: Context): Boolean {
        val appInfo = context.applicationInfo
        return (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    companion object {
        /**
         * Flips Analytics and Crashlytics collection on together. Both SDKs persist the
         * flag across app restarts on their own, but [onCreate] re-asserts it every launch
         * for a consenting user anyway, so PrivacyConsentPreferences — not Firebase's
         * internal state — stays the single source of truth.
         */
        fun setCollectionEnabled(enabled: Boolean) {
            AppAnalytics.setCollectionEnabled(enabled)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
            // The display language splits engagement metrics by locale. Set only once
            // collection is on: a user property set while collection is off is dropped,
            // which left app_language empty for ~80% of users when this ran earlier in
            // onCreate, before the consent check.
            if (enabled) AppAnalytics.setLanguage(Locale.getDefault().language)
        }
    }
}
