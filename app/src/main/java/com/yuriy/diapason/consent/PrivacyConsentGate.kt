package com.yuriy.diapason.consent

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.yuriy.diapason.MainApp
import com.yuriy.diapason.R
import com.yuriy.diapason.analytics.AppAnalytics

/**
 * Shown once, on first launch: blocks the rest of the app behind a modal `AlertDialog`
 * until the user agrees to the privacy policy (Huawei AppGallery Review Guidelines
 * rule 7.5 / PIPL — the app must prompt for this before any personal-info-collecting
 * SDK runs). Renders nothing on subsequent launches once [PrivacyConsentPreferences]
 * records agreement.
 *
 * Not dismissible by back-press or tapping outside: the user must make an explicit
 * choice. Disagreeing closes the app rather than leaving it in a half-consented state,
 * since Diapason cannot function without microphone access and this is the simplest,
 * least-surprising outcome for a user who declines.
 */
@Composable
fun PrivacyConsentGate() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(PrivacyConsentPreferences(context).granted) }

    if (granted) return

    val policyUrl = stringResource(R.string.about_privacy_policy_url)

    AlertDialog(
        onDismissRequest = { /* not dismissible except via the buttons below */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.privacy_consent_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.privacy_consent_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { openPrivacyPolicy(context, policyUrl) }) {
                    Text(stringResource(R.string.privacy_consent_read_policy))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                PrivacyConsentPreferences(context).granted = true
                MainApp.setCollectionEnabled(enabled = true)
                AppAnalytics.privacyConsentAccepted()
                granted = true
            }) {
                Text(stringResource(R.string.privacy_consent_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = { (context as? Activity)?.finish() }) {
                Text(stringResource(R.string.privacy_consent_disagree))
            }
        },
    )
}

private fun openPrivacyPolicy(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
