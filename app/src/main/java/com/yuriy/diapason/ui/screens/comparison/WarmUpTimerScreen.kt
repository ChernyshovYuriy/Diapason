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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                title = { Text("Warm-up") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit")
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
                text = "Baseline recorded.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Now warm up your voice, then record again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))

            // ── Timer display ────────────────────────────────────────────────
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = if (isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
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
                    text = "Press Start when you're ready to begin the timer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else if (isRunning) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Timer running…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Warm-up guidance ─────────────────────────────────────────────
            Text(
                text = "Suggested warm-up exercises",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            WarmUpCard(
                minuteRange = "0–1 min",
                title = "Lip trills",
                body = "Blow air through loosely closed lips to make them vibrate. " +
                        "Slide gently up and down your range."
            )
            WarmUpCard(
                minuteRange = "1–2 min",
                title = "Humming",
                body = "Hum on a comfortable pitch with your mouth closed. " +
                        "Move slowly up and down a fifth."
            )
            WarmUpCard(
                minuteRange = "2–3 min",
                title = "Five-note scales",
                body = "Sing 'mah-may-mee-mo-moo' on a five-note ascending scale. " +
                        "Start in mid-range, step up a half-step each repeat."
            )
            WarmUpCard(
                minuteRange = "3–4 min",
                title = "Octave slides",
                body = "On a comfortable vowel, slide from a comfortable low note to " +
                        "a comfortable high note and back. Keep it easy — no forcing."
            )
            WarmUpCard(
                minuteRange = "4–5 min",
                title = "Gentle sustained notes",
                body = "Hold 3–4 comfortable notes for 3 seconds each. " +
                        "These are the same sustained notes Diapason will detect."
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "These are general suggestions only. Follow your own warm-up routine if you have one.",
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
                        .height(52.dp)
                ) {
                    Text("Start 5-Minute Timer")
                }
                Spacer(Modifier.height(8.dp))
            }

            FilledTonalButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Skip Timer & Start Retest" else "Skip Timer")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exit Comparison")
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
            .padding(bottom = 8.dp)
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
