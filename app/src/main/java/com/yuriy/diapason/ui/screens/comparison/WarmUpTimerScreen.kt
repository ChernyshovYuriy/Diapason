package com.yuriy.diapason.ui.screens.comparison

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuriy.diapason.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarmUpTimerScreen(
    remainingSeconds: Int,
    isRunning: Boolean,
    onStartTimer: () -> Unit,
    onSkip: () -> Unit,
    onExit: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_warmup_title)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_exit),
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.compare_warmup_baseline_done),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.compare_warmup_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))

            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = if (isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatTime(remainingSeconds),
                fontSize = 52.sp,
                fontWeight = FontWeight.Light,
                color = if (isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )

            if (!isRunning && remainingSeconds > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.compare_warmup_timer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else if (isRunning) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.compare_warmup_timer_running),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.compare_warmup_exercises_heading),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            WarmUpCard(
                minuteRange = stringResource(R.string.compare_warmup_card1_range),
                title = stringResource(R.string.compare_warmup_card1_title),
                body = stringResource(R.string.compare_warmup_card1_body),
            )
            WarmUpCard(
                minuteRange = stringResource(R.string.compare_warmup_card2_range),
                title = stringResource(R.string.compare_warmup_card2_title),
                body = stringResource(R.string.compare_warmup_card2_body),
            )
            WarmUpCard(
                minuteRange = stringResource(R.string.compare_warmup_card3_range),
                title = stringResource(R.string.compare_warmup_card3_title),
                body = stringResource(R.string.compare_warmup_card3_body),
            )
            WarmUpCard(
                minuteRange = stringResource(R.string.compare_warmup_card4_range),
                title = stringResource(R.string.compare_warmup_card4_title),
                body = stringResource(R.string.compare_warmup_card4_body),
            )
            WarmUpCard(
                minuteRange = stringResource(R.string.compare_warmup_card5_range),
                title = stringResource(R.string.compare_warmup_card5_title),
                body = stringResource(R.string.compare_warmup_card5_body),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.compare_warmup_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            if (!isRunning && remainingSeconds > 0) {
                Button(
                    onClick = onStartTimer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(stringResource(R.string.compare_warmup_btn_start_timer))
                }
                Spacer(Modifier.height(8.dp))
            }

            FilledTonalButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (isRunning) R.string.compare_warmup_btn_skip_running
                        else R.string.compare_warmup_btn_skip_idle,
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.compare_warmup_btn_exit))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WarmUpCard(minuteRange: String, title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$minuteRange  ·  $title",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
