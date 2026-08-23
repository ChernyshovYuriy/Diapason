package com.yuriy.diapason

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

/**
 * Resolves a string against the user's in-app language override (set via the language picker
 * on [com.yuriy.diapason.ui.screens.about.AboutScreen], `AppCompatDelegate.setApplicationLocales`),
 * rather than whatever locale this [Context] itself is currently configured with.
 *
 * `AndroidViewModel.getApplication<Application>().getString(...)` is the trap this works around:
 * AppCompat's per-app language override reliably patches every *Activity* context (Compose's
 * `stringResource()` reads through `LocalContext`, which is Activity-backed, so it always sees
 * the override) but the retained `Application` singleton's own `Resources` configuration is not
 * guaranteed to reflect the override in the same process — so a ViewModel that pre-resolves a
 * string via the Application context can hand the UI an English string while every
 * directly-composed string on the same screen is correctly in the user's chosen language.
 * Symptom: "Not enough data..." showing in English on an otherwise fully-Persian screen.
 *
 * Route every ViewModel-side [Context.getString] call for user-facing text through this instead.
 */
fun Context.localizedString(@StringRes resId: Int): String {
    val override = AppCompatDelegate.getApplicationLocales()
    if (override.isEmpty) return getString(resId)

    val config = Configuration(resources.configuration).apply {
        setLocales(override.unwrap() as android.os.LocaleList)
    }
    return createConfigurationContext(config).getString(resId)
}
