package com.yuriy.diapason.consent

import android.content.Context

/**
 * Persists whether the user has agreed to the privacy policy on first launch
 * (Huawei AppGallery Review Guidelines rule 7.5 / PIPL: personal-info-collecting
 * SDKs must not run before the user has had a chance to read and accept the
 * policy). [MainApp] reads [granted] at startup to decide whether Firebase
 * Analytics/Crashlytics collection should be (re-)enabled, and [PrivacyConsentGate]
 * reads it to decide whether the first-launch dialog needs to be shown.
 */
class PrivacyConsentPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var granted: Boolean
        get() = prefs.getBoolean(KEY_GRANTED, false)
        set(value) = prefs.edit().putBoolean(KEY_GRANTED, value).apply()

    companion object {
        private const val PREFS_NAME = "diapason_privacy_consent"
        private const val KEY_GRANTED = "granted"
    }
}
