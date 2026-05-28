package com.yuriy.diapason

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.yuriy.diapason.analytics.AppAnalytics
import com.yuriy.diapason.data.db.DiapasonDatabase
import com.yuriy.diapason.data.repository.SessionRepository
import com.yuriy.diapason.data.repository.SessionRepositoryImpl
import com.yuriy.diapason.logging.AppLogger
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
        // The display language splits all engagement metrics by locale — the
        // Firebase overview shows French/Portuguese/Italian dominate, so confirm
        // that signal at user-property level rather than guessing from country.
        AppAnalytics.setLanguage(Locale.getDefault().language)
    }

    private fun isDebug(context: Context): Boolean {
        val appInfo = context.applicationInfo
        return (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
