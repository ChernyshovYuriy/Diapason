package com.yuriy.diapason.ui.screens.results

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.yuriy.diapason.R
import com.yuriy.diapason.analytics.AppAnalytics
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.CombinedVoiceProfile
import com.yuriy.diapason.analyzer.FachMatch
import com.yuriy.diapason.analyzer.FachDefinition
import com.yuriy.diapason.analyzer.VoiceFamilyRanges
import com.yuriy.diapason.analyzer.VoiceTypeReader
import com.yuriy.diapason.analyzer.VoiceTypeReading
import com.yuriy.diapason.analyzer.VoiceProfile
import com.yuriy.diapason.reminder.ReminderOffer
import com.yuriy.diapason.reminder.ReminderPreferences
import com.yuriy.diapason.reminder.ReminderScheduler
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

private fun shareResult(
    context: Context,
    text: String,
    chooserTitle: String,
    clipboardLabel: String,
    toastMessage: String
) {
    // Copy to clipboard first — apps like Facebook strip Intent.EXTRA_TEXT,
    // so the user can paste the text manually after tapping a Facebook post.
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    profile: VoiceProfile,
    matches: List<FachMatch>,
    combinedProfile: CombinedVoiceProfile? = null,
    savedSessionCount: Int = 0,
    onBack: () -> Unit,
    onAnalyzeAgain: () -> Unit,
    /** Opens Voice Types focused on the Fach with this name resource. */
    onOpenVoiceType: (fachNameRes: Int) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenWarmUp: () -> Unit = {},
) {

    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val topMatch = matches.firstOrNull()
    val reading = remember(matches) { VoiceTypeReader.read(matches) }
    // Several recordings say more than one: when they exist they lead, this take alongside.
    val headline = combinedProfile?.reading ?: reading
    val families = remember(matches) {
        VoiceFamilyRanges.of(matches.map { it.fach.voiceGroup }.distinct().singleOrNull())
    }
    val topFachKey = remember(topMatch) {
        topMatch?.let {
            runCatching { context.resources.getResourceEntryName(it.fach.nameRes) }.getOrNull()
        }
    }

    // dwell tracking — fire result_viewed on entry, result_dismissed on dispose
    // with elapsed seconds. Without this the funnel can't distinguish "user read
    // the result" from "user opened and bounced".
    val openedAtMs = remember { System.currentTimeMillis() }
    DisposableEffect(Unit) {
        AppAnalytics.resultViewed(topFachKey)
        onDispose {
            val dwellSeconds = (System.currentTimeMillis() - openedAtMs) / 1000L
            AppAnalytics.resultDismissed(topFachKey, dwellSeconds)
        }
    }

    val shareText = stringResource(
        R.string.share_text,
        headline?.let { familyLabel(it) } ?: stringResource(R.string.share_unknown_voice_type),
        FachClassifier.hzToNoteName(profile.detectedMinHz),
        FachClassifier.hzToNoteName(profile.detectedMaxHz),
        FachClassifier.hzToNoteName(profile.comfortableLowHz),
        FachClassifier.hzToNoteName(profile.comfortableHighHz),
        stringResource(R.string.share_app_link)
    )
    val shareChooserTitle = stringResource(R.string.share_chooser_title)
    val shareClipboardLabel = stringResource(R.string.share_clipboard_label)
    val shareToastMessage = stringResource(R.string.share_clipboard_toast)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    ShareButton(
                        onClick = {
                            AppAnalytics.resultShared(topFachKey)
                            shareResult(
                                context,
                                shareText,
                                shareChooserTitle,
                                shareClipboardLabel,
                                shareToastMessage
                            )
                        }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Voice-type reading (hero card) ────────────────────────────
            headline?.let {
                VoiceReadingCard(
                    reading = it,
                    combined = combinedProfile,
                    thisRecording = reading.takeIf { combinedProfile != null },
                    onOpenFach = { fach ->
                        AppAnalytics.resultNextStep(AppAnalytics.NextStep.VoiceType, topFachKey)
                        onOpenVoiceType(fach.nameRes)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── This recording against the voice families ─────────────────
            VoiceRangeChart(profile = profile, families = families)

            Spacer(Modifier.height(16.dp))

            // ── Range statistics ──────────────────────────────────────────
            SectionLabel(stringResource(R.string.results_section_range))
            RangeStatsCard(profile = profile)

            Spacer(Modifier.height(16.dp))

            // ── What's next ───────────────────────────────────────────────
            // Results used to be a dead end: ~6,000 views led anywhere but "back" only
            // 5 times. The Fach chips above open Voice Types; these are the other two
            // places users already went on their own.
            SectionLabel(stringResource(R.string.results_section_next_steps))
            NextStepButton(
                text = stringResource(R.string.results_next_history),
                icon = Icons.Filled.History,
                onClick = {
                    AppAnalytics.resultNextStep(AppAnalytics.NextStep.History, topFachKey)
                    onOpenHistory()
                },
            )
            NextStepButton(
                text = stringResource(R.string.results_next_warmup),
                icon = Icons.Filled.Whatshot,
                onClick = {
                    AppAnalytics.resultNextStep(AppAnalytics.NextStep.WarmUp, topFachKey)
                    onOpenWarmUp()
                },
            )
            Spacer(Modifier.height(16.dp))

            // ── Disclaimer ────────────────────────────────────────────────
            DisclaimerCard()

            Spacer(Modifier.height(16.dp))

            // ── Re-test reminder opt-in (offered once — see ReminderOffer) ─
            val reminderPrefs = remember(context) { ReminderPreferences(context) }
            val showReminder = remember(savedSessionCount) {
                ReminderOffer.shouldShow(reminderPrefs.optedIn, reminderPrefs.offerShown, savedSessionCount)
            }
            if (showReminder) ReTestReminderCard()

            Spacer(Modifier.height(20.dp))

            // ── Action buttons ────────────────────────────────────────────
            Button(
                onClick = onAnalyzeAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh, contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.results_btn_analyze_again))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShareButton(onClick: () -> Unit) {
    var hasBeenTapped by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    // Beacon: a circle that expands outward and fades, restarting every 1.4s.
    // Drives both radius and alpha from a single 0→1 progress value so they
    // stay in sync. Stops rendering (but keeps running internally) after tap.
    val infiniteTransition = rememberInfiniteTransition(label = "share_beacon")
    val beaconProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "share_beacon_progress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp)
    ) {
        if (!hasBeenTapped) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension * 0.80f
                val minRadius = size.minDimension * 0.28f
                val radius = minRadius + (maxRadius - minRadius) * beaconProgress
                val alpha = 0.55f * (1f - beaconProgress)   // bright → invisible
                drawCircle(color = primaryColor, radius = radius, alpha = alpha)
            }
        }
        IconButton(
            onClick = {
                hasBeenTapped = true
                onClick()
            }
        ) {
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.share_cd_button),
                tint = primaryColor
            )
        }
    }
}

/**
 * The voice-type reading as a teacher would give it from a range: the family — or the two it
 * sits between — and the Fach types whose ranges fit, as places to explore rather than a
 * verdict (see [VoiceTypeReader]). With [combined], the reading is from several recordings
 * and [thisRecording] shows the take just made.
 */
@Composable
private fun VoiceReadingCard(
    reading: VoiceTypeReading,
    combined: CombinedVoiceProfile?,
    thisRecording: VoiceTypeReading?,
    onOpenFach: (FachDefinition) -> Unit,
) {
    val onCard = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.results_reading_title),
                style = MaterialTheme.typography.labelMedium,
                color = onCard.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = familyLabel(reading),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = onCard,
                textAlign = TextAlign.Center
            )

            if (combined != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.results_profile_based_on,
                        combined.sessionCount,
                        combined.sessionCount
                    ) + " · " + pluralStringResource(
                        // Quantity follows the agreeing count — it's the verb's subject, and
                        // it can be 0 or 1 even though the total is always at least 2.
                        R.plurals.results_profile_consistency,
                        combined.consistentCount,
                        combined.consistentCount,
                        combined.sessionCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = onCard.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                thisRecording?.let {
                    Text(
                        text = stringResource(R.string.results_this_recording_format, familyLabel(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = stringResource(R.string.results_profile_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = onCard.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.results_fach_types_title),
                style = MaterialTheme.typography.labelMedium,
                color = onCard.copy(alpha = 0.7f)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                reading.fachTypes.forEach { fach ->
                    SuggestionChip(
                        onClick = { onOpenFach(fach) },
                        label = { Text(stringResource(fach.nameRes)) }
                    )
                }
            }
        }
    }
}

/**
 * "Soprano", or "Between soprano and mezzo-soprano" when the range sits between two families.
 * Shared with History so a session reads the same in both places.
 */
@Composable
internal fun familyLabel(reading: VoiceTypeReading): String =
    if (reading.isBetween) {
        stringResource(
            R.string.results_reading_between_format,
            stringResource(reading.familyRes[0]),
            stringResource(reading.familyRes[1]),
        )
    } else {
        stringResource(reading.familyRes.first())
    }

@Composable
private fun NextStepButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(text, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
    }
}

@Composable
private fun RangeStatsCard(profile: VoiceProfile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── PRIMARY: Comfortable range ────────────────────────────────
            RangeSubheading(stringResource(R.string.results_section_comfortable_range))
            StatRow(
                label = stringResource(R.string.results_stat_comfortable_low),
                value = FachClassifier.hzToNoteName(profile.comfortableLowHz),
                sub = stringResource(R.string.results_hz_format, profile.comfortableLowHz)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            StatRow(
                label = stringResource(R.string.results_stat_comfortable_high),
                value = FachClassifier.hzToNoteName(profile.comfortableHighHz),
                sub = stringResource(R.string.results_hz_format, profile.comfortableHighHz)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            StatRow(
                label = stringResource(R.string.results_stat_passaggio),
                value = FachClassifier.hzToNoteName(profile.estimatedPassaggioHz),
                sub = stringResource(R.string.results_hz_format, profile.estimatedPassaggioHz)
            )

            Spacer(Modifier.height(12.dp))

            // ── SECONDARY: Detected extremes (visually muted) ─────────────
            RangeSubheading(
                stringResource(R.string.results_section_detected_extremes),
                muted = true
            )
            StatRow(
                label = stringResource(R.string.results_stat_lowest),
                value = FachClassifier.hzToNoteName(profile.detectedMinHz),
                sub = stringResource(R.string.results_hz_format, profile.detectedMinHz),
                muted = true
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )
            StatRow(
                label = stringResource(R.string.results_stat_highest),
                value = FachClassifier.hzToNoteName(profile.detectedMaxHz),
                sub = stringResource(R.string.results_hz_format, profile.detectedMaxHz),
                muted = true
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            // ── Session metadata ──────────────────────────────────────────
            Text(
                text = stringResource(R.string.results_session_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            StatRow(
                label = stringResource(R.string.results_stat_samples),
                value = profile.sampleCount.toString(),
                sub = stringResource(
                    R.string.results_stat_session_format,
                    profile.durationSeconds
                )
            )
        }
    }
}

@Composable
private fun RangeSubheading(text: String, muted: Boolean = false) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (muted)
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String, sub: String, muted: Boolean = false) {
    val contentAlpha = if (muted) 0.55f else 1f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * contentAlpha)
            )
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp, end = 10.dp)
            )
            Text(
                text = stringResource(R.string.results_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Re-test reminder card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ReTestReminderCard() {
    val context = LocalContext.current
    val scheduler = remember(context) { ReminderScheduler(context) }

    // Mirror scheduler state in Compose-visible holders so the card re-renders
    // after the user taps without us having to round-trip through a ViewModel.
    var scheduledAtMs by remember { mutableLongStateOf(scheduler.scheduledAtMs) }
    var optedIn by remember { mutableStateOf(scheduler.isOptedIn) }
    var dismissedThisView by remember { mutableStateOf(false) }

    fun schedule() {
        scheduler.scheduleOrReplace()
        scheduledAtMs = scheduler.scheduledAtMs
        optedIn = true
        AppAnalytics.reminderOptInAccepted()
    }

    // POST_NOTIFICATIONS only exists from Android 13+. Older devices grant it
    // implicitly so we skip the permission gate entirely.
    val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) { granted ->
            if (granted) schedule()
        }
    } else {
        null
    }

    LaunchedEffect(Unit) {
        // Only an actual offer counts — and uses up the one-time offer. An opted-in user
        // seeing their scheduled reminder isn't being offered anything.
        if (!optedIn) {
            ReminderPreferences(context).offerShown = true
            AppAnalytics.reminderOptInShown()
        }
    }

    if (dismissedThisView) return

    val dateFormatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val targetDateText = remember(scheduledAtMs) {
        val target = if (scheduledAtMs > 0L) scheduledAtMs
                     else System.currentTimeMillis() +
                             TimeUnit.DAYS.toMillis(ReminderScheduler.REMINDER_DELAY_DAYS)
        dateFormatter.format(Date(target))
    }

    val isScheduled = optedIn && scheduledAtMs > 0L
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isScheduled)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isScheduled) Icons.Filled.CheckCircle
                                  else Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = stringResource(
                        if (isScheduled) R.string.reminder_scheduled_title
                        else R.string.reminder_card_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isScheduled)
                    stringResource(R.string.reminder_scheduled_body, targetDateText)
                else
                    stringResource(R.string.reminder_card_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(12.dp))

            if (isScheduled) {
                OutlinedButton(
                    onClick = {
                        scheduler.cancel()
                        scheduledAtMs = 0L
                        optedIn = false
                        AppAnalytics.reminderCancelled()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.reminder_btn_cancel))
                }
            } else {
                Button(
                    onClick = {
                        val notif = notifPermission
                        if (notif == null || notif.status.isGranted) {
                            schedule()
                        } else {
                            notif.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.reminder_btn_opt_in, targetDateText))
                }
                TextButton(
                    onClick = {
                        AppAnalytics.reminderOptInDismissed()
                        dismissedThisView = true
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.reminder_btn_dismiss))
                }
            }
        }
    }
}
