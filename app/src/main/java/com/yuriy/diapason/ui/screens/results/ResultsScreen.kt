package com.yuriy.diapason.ui.screens.results

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.FachMatch
import com.yuriy.diapason.analyzer.VoiceProfile

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
    onBack: () -> Unit,
    onAnalyzeAgain: () -> Unit
) {

    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val topMatch = matches.firstOrNull()
    val shareText = stringResource(
        R.string.share_text,
        stringResource(topMatch?.fach?.nameRes ?: R.string.share_unknown_voice_type),
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
                            contentDescription = stringResource(R.string.results_cd_back)
                        )
                    }
                },
                actions = {
                    ShareButton(
                        onClick = {
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

            // ── Top match (hero card) ──────────────────────────────────────
            matches.firstOrNull()?.let { top ->
                TopMatchCard(match = top)
            }

            Spacer(Modifier.height(16.dp))

            // ── Range statistics ──────────────────────────────────────────
            SectionLabel(stringResource(R.string.results_section_range))
            RangeStatsCard(profile = profile)

            Spacer(Modifier.height(16.dp))

            // ── Runner-up matches ─────────────────────────────────────────
            if (matches.size > 1) {
                SectionLabel(stringResource(R.string.results_section_other_matches))
                matches.drop(1).take(4).forEachIndexed { index, match ->
                    RunnerUpRow(rank = index + 2, match = match)
                    if (index < 3) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Disclaimer ────────────────────────────────────────────────
            DisclaimerCard()

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

@Composable
private fun TopMatchCard(match: FachMatch) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.results_best_match_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(match.fach.nameRes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(match.fach.categoryRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(match.fach.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))

            val pct = match.score.toFloat() / match.maxScore.toFloat()
            ConfidenceBar(
                label = stringResource(R.string.results_confidence_label),
                fraction = pct
            )

            Spacer(Modifier.height(14.dp))

            val famousRoles = stringArrayResource(match.fach.famousRolesRes)
            if (famousRoles.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.results_famous_roles_subheading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                famousRoles.forEach { role ->
                    Text(
                        text = stringResource(R.string.common_bullet_item, role),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    )
                }
            }
        }
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
private fun RunnerUpRow(rank: Int, match: FachMatch) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.results_runner_up_rank_format, rank),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(match.fach.nameRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                stringResource(match.fach.categoryRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.results_score_format, match.score, match.maxScore),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConfidenceBar(label: String, fraction: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                stringResource(R.string.results_percent_format, (fraction * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.20f),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.80f),
                    size = size.copy(width = size.width * fraction),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
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
