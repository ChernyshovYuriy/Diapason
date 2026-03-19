package com.yuriy.diapason.ui.screens.comparison

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.comparison.ComparisonResult
import com.yuriy.diapason.comparison.HzDelta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareResultScreen(
    result: ComparisonResult,
    onDone: () -> Unit,
    onCompareAgain: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.compare_result_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SummaryHeadline(result)

            Spacer(Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.compare_result_section_comfortable))
            ComfortableRangeCard(result)

            Spacer(Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.compare_result_section_extremes))
            DetectedExtremesCard(result)

            result.passaggio?.let { passaggio ->
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.compare_result_section_passaggio))
                PassaggioCard(passaggio)
            }

            val beforeMatch = result.beforeTopMatch
            val afterMatch = result.afterTopMatch
            if (beforeMatch != null && afterMatch != null) {
                val beforeName = stringResource(beforeMatch.fach.nameRes)
                val afterName = stringResource(afterMatch.fach.nameRes)
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.compare_result_section_voice_type))
                VoiceTypeCard(
                    beforeName = beforeName,
                    afterName = afterName,
                    beforeScore = stringResource(
                        R.string.results_score_format,
                        beforeMatch.score,
                        beforeMatch.maxScore
                    ),
                    afterScore = stringResource(
                        R.string.results_score_format,
                        afterMatch.score,
                        afterMatch.maxScore
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.compare_result_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(14.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.compare_result_btn_done))
            }

            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onCompareAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.compare_result_btn_again))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SummaryHeadline(result: ComparisonResult) {
    val comfBefore = "${FachClassifier.hzToNoteName(result.before.comfortableLowHz)} – " +
            FachClassifier.hzToNoteName(result.before.comfortableHighHz)
    val comfAfter = "${FachClassifier.hzToNoteName(result.after.comfortableLowHz)} – " +
            FachClassifier.hzToNoteName(result.after.comfortableHighHz)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CompareArrows,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.compare_result_sessions_recorded),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.compare_result_before_range, comfBefore),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = stringResource(R.string.compare_result_after_range, comfAfter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ComfortableRangeCard(result: ComparisonResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CompareRow(
                label = stringResource(R.string.compare_result_label_comfortable_low),
                before = FachClassifier.hzToNoteName(result.comfortableLow.beforeHz),
                after = FachClassifier.hzToNoteName(result.comfortableLow.afterHz),
                delta = result.comfortableLow,
                positiveWhenDown = true,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            CompareRow(
                label = stringResource(R.string.compare_result_label_comfortable_high),
                before = FachClassifier.hzToNoteName(result.comfortableHigh.beforeHz),
                after = FachClassifier.hzToNoteName(result.comfortableHigh.afterHz),
                delta = result.comfortableHigh,
                positiveWhenDown = false,
            )
        }
    }
}

@Composable
private fun DetectedExtremesCard(result: ComparisonResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CompareRow(
                label = stringResource(R.string.compare_result_label_detected_min),
                before = FachClassifier.hzToNoteName(result.detectedMin.beforeHz),
                after = FachClassifier.hzToNoteName(result.detectedMin.afterHz),
                delta = result.detectedMin,
                positiveWhenDown = true,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            CompareRow(
                label = stringResource(R.string.compare_result_label_detected_max),
                before = FachClassifier.hzToNoteName(result.detectedMax.beforeHz),
                after = FachClassifier.hzToNoteName(result.detectedMax.afterHz),
                delta = result.detectedMax,
                positiveWhenDown = false,
            )
        }
    }
}

@Composable
private fun PassaggioCard(passaggio: HzDelta) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CompareRow(
            label = stringResource(R.string.compare_result_label_passaggio),
            before = FachClassifier.hzToNoteName(passaggio.beforeHz),
            after = FachClassifier.hzToNoteName(passaggio.afterHz),
            delta = passaggio,
            positiveWhenDown = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun VoiceTypeCard(
    beforeName: String,
    afterName: String,
    beforeScore: String,
    afterScore: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SessionColumn(
                sessionLabel = stringResource(R.string.compare_result_before_label),
                voiceType = beforeName,
                score = beforeScore,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            SessionColumn(
                sessionLabel = stringResource(R.string.compare_result_after_label),
                voiceType = afterName,
                score = afterScore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SessionColumn(
    sessionLabel: String,
    voiceType: String,
    score: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = sessionLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = voiceType,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = score,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CompareRow(
    label: String,
    before: String,
    after: String,
    delta: HzDelta,
    positiveWhenDown: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = before,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Icon(
            imageVector = when {
                !delta.isMeaningful -> Icons.Filled.Remove
                delta.deltaHz < 0 -> Icons.Filled.ArrowDownward
                else -> Icons.Filled.ArrowUpward
            },
            contentDescription = null,
            tint = deltaColour(delta, positiveWhenDown),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = after,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = deltaColour(delta, positiveWhenDown),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun deltaColour(delta: HzDelta, positiveWhenDown: Boolean): Color {
    if (!delta.isMeaningful) return MaterialTheme.colorScheme.onSurfaceVariant
    val widened = if (positiveWhenDown) delta.deltaHz < 0 else delta.deltaHz > 0
    return if (widened) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
