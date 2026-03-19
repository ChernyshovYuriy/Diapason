package com.yuriy.diapason

import android.app.Application
import com.google.firebase.FirebaseApp
import com.yuriy.diapason.data.db.DiapasonDatabase
import com.yuriy.diapason.data.repository.SessionRepository
import com.yuriy.diapason.data.repository.SessionRepositoryImpl

class MainApp : Application() {

    /**
     * Application-scoped repository. Initialised lazily on first access so that
     * the database is not opened until it is actually needed.
     *
     * Tests inject a fake repository via constructor parameters on the ViewModel
     */
    val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(DiapasonDatabase.getInstance(this).sessionDao())
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
