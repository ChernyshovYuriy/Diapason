package com.yuriy.diapason.ui.screens.results

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.FachMatch
import com.yuriy.diapason.analyzer.VoiceProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    profile: VoiceProfile,
    matches: List<FachMatch>,
    onBack: () -> Unit,
    onAnalyzeAgain: () -> Unit
) {
    val context = LocalContext.current
    val shareText = remember(context, profile, matches) {
        buildResultsShareText(context = context, profile = profile, matches = matches)
    }

    fun onShareResult() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.results_share_subject))
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.results_share_chooser_title))
        )
    }

    BackHandler(onBack = onBack)

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
                    IconButton(onClick = ::onShareResult) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.results_cd_share)
                        )
                    }
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

private enum class ResultsShareTemplate {
    VIRAL_CURIOSITY,
    CONFIDENT_REVEAL,
    CHALLENGE_STYLE
}

private fun buildResultsShareText(
    context: Context,
    profile: VoiceProfile,
    matches: List<FachMatch>
): String {
    val selectedTemplate = ResultsShareTemplate.VIRAL_CURIOSITY
    val topMatch = matches.firstOrNull()

    val voiceType = topMatch?.let { fachMatch ->
        context.getString(
            R.string.results_share_voice_type_format,
            context.getString(fachMatch.fach.nameRes),
            context.getString(fachMatch.fach.categoryRes)
        )
    } ?: context.getString(R.string.results_share_unknown_voice_type)

    val range = "${FachClassifier.hzToNoteName(profile.absoluteMinHz)}–${FachClassifier.hzToNoteName(profile.absoluteMaxHz)}"
    val tessitura = "${FachClassifier.hzToNoteName(profile.tessituraLowHz)}–${FachClassifier.hzToNoteName(profile.tessituraHighHz)}"
    val appLink = context.getString(R.string.app_google_play_link)

    // Candidate template 1 (default): curious + social + easy to skim.
    val templateViralCuriosity = context.getString(
        R.string.results_share_template_viral_curiosity,
        voiceType,
        range,
        tessitura,
        appLink
    )

    // Candidate template 2: short confidence reveal.
    val templateConfidentReveal = context.getString(
        R.string.results_share_template_confident_reveal,
        voiceType,
        range,
        tessitura,
        appLink
    )

    // Candidate template 3: challenge-style hook.
    val templateChallengeStyle = context.getString(
        R.string.results_share_template_challenge_style,
        voiceType,
        range,
        tessitura,
        appLink
    )

    return when (selectedTemplate) {
        ResultsShareTemplate.VIRAL_CURIOSITY -> templateViralCuriosity
        ResultsShareTemplate.CONFIDENT_REVEAL -> templateConfidentReveal
        ResultsShareTemplate.CHALLENGE_STYLE -> templateChallengeStyle
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

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
            StatRow(
                label = stringResource(R.string.results_stat_lowest),
                value = FachClassifier.hzToNoteName(profile.absoluteMinHz),
                sub = stringResource(R.string.results_hz_format, profile.absoluteMinHz)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            StatRow(
                label = stringResource(R.string.results_stat_highest),
                value = FachClassifier.hzToNoteName(profile.absoluteMaxHz),
                sub = stringResource(R.string.results_hz_format, profile.absoluteMaxHz)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            StatRow(
                label = stringResource(R.string.results_stat_tessitura),
                value = "${FachClassifier.hzToNoteName(profile.tessituraLowHz)} – ${
                    FachClassifier.hzToNoteName(profile.tessituraHighHz)
                }",
                sub = stringResource(R.string.results_stat_percentile_range)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            StatRow(
                label = stringResource(R.string.results_stat_passaggio),
                value = FachClassifier.hzToNoteName(profile.estimatedPassaggioHz),
                sub = stringResource(R.string.results_hz_format, profile.estimatedPassaggioHz)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
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
private fun StatRow(label: String, value: String, sub: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                drawRoundRect(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.80f),
                    size = size.copy(width = size.width * fraction),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
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
