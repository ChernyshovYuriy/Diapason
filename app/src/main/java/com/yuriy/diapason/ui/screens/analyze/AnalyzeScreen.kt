package com.yuriy.diapason.ui.screens.analyze

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.FachClassifier

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AnalyzeScreen(
    viewModel: AnalyzeViewModel,
    onNavigateToResults: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastResultFlow.collectAsStateWithLifecycle()

    // Auto-navigate when a new result is produced
    LaunchedEffect(uiState) {
        if (uiState is AnalyzeUiState.ResultReady) {
            onNavigateToResults()
        }
    }

    val micPermission = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(32.dp))

        // ── App title ────────────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // ── Previous result banner (visible only when idle + result exists) ──
        val isIdle = uiState is AnalyzeUiState.Idle
        AnimatedVisibility(visible = isIdle && lastResult != null) {
            lastResult?.let { result ->
                PreviousResultBanner(
                    voiceType = result.matches.firstOrNull()?.fach?.let { stringResource(it.nameRes) }
                        ?: stringResource(R.string.analyze_unknown),
                    minNote = FachClassifier.hzToNoteName(result.profile.absoluteMinHz),
                    maxNote = FachClassifier.hzToNoteName(result.profile.absoluteMaxHz),
                    onClick = onNavigateToResults
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Central pitch display ─────────────────────────────────────────────
        PitchDisplay(uiState = uiState)

        Spacer(Modifier.height(16.dp))

        // ── Status / error message ────────────────────────────────────────────
        AnimatedContent(
            targetState = uiState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "status"
        ) { state ->
            Text(
                text = when (state) {
                    is AnalyzeUiState.Idle -> stringResource(R.string.analyze_idle_prompt)
                    is AnalyzeUiState.Recording -> state.statusMessage
                    is AnalyzeUiState.InsufficientData -> state.reason
                    is AnalyzeUiState.ResultReady -> stringResource(R.string.analyze_complete)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when (state) {
                    is AnalyzeUiState.InsufficientData -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Stats row (only while recording) ─────────────────────────────────
        AnimatedVisibility(visible = uiState is AnalyzeUiState.Recording) {
            val recording = uiState as? AnalyzeUiState.Recording
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    label = stringResource(R.string.analyze_stat_samples),
                    value = recording?.sampleCount?.toString() ?: "0"
                )
                StatCard(
                    label = stringResource(R.string.analyze_stat_hz),
                    value = if ((recording?.currentHz ?: 0f) > 0)
                        "%.1f".format(recording?.currentHz) else "—"
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Start / Stop button ───────────────────────────────────────────────
        val isRecording = uiState is AnalyzeUiState.Recording
        if (isRecording) {
            PulsingButton(
                onClick = { viewModel.stopRecording() }
            )
        } else {
            Button(
                onClick = {
                    if (micPermission.status.isGranted) {
                        viewModel.startRecording()
                    } else {
                        micPermission.launchPermissionRequest()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (!micPermission.status.isGranted
                        && micPermission.status.shouldShowRationale
                    )
                        stringResource(R.string.analyze_btn_grant_permission)
                    else
                        stringResource(R.string.analyze_btn_start),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Reset button after insufficient data
        AnimatedVisibility(visible = uiState is AnalyzeUiState.InsufficientData) {
            FilledTonalButton(
                onClick = { viewModel.resetToIdle() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) { Text(stringResource(R.string.analyze_btn_try_again)) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previous result banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PreviousResultBanner(
    voiceType: String,
    minNote: String,
    maxNote: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 0.dp)
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = stringResource(
                            R.string.analyze_last_result_type,
                            voiceType
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(
                            R.string.analyze_range, minNote, maxNote
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    stringResource(R.string.analyze_view),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PitchDisplay(uiState: AnalyzeUiState) {
    val isRecording = uiState is AnalyzeUiState.Recording
    val note = (uiState as? AnalyzeUiState.Recording)?.currentNote ?: "—"

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .border(
                    width = 2.dp,
                    color = if (isRecording) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(170.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = if (isRecording) 0.25f else 0.12f
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = note,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "note"
            ) { n ->
                Text(
                    text = n,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PulsingButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        )
    ) {
        Icon(
            Icons.Filled.Stop,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            stringResource(
                R.string.analyze_btn_stop
            ),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.padding(4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
