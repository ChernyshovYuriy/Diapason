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
     * Tests that run AnalyzeViewModel in isolation should subclass [MainApp] and
     * override this property with a fake repository.
     */
    open val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(DiapasonDatabase.getInstance(this).sessionDao())
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
