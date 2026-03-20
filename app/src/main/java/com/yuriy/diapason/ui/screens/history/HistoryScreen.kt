package com.yuriy.diapason.ui.screens.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.data.SessionRecord
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.history_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        when (val state = uiState) {
            is HistoryUiState.Loading -> LoadingContent()
            is HistoryUiState.Empty -> EmptyContent()
            is HistoryUiState.Sessions -> SessionList(items = state.items)
        }
    }
}

// ── Loading ───────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .padding(bottom = 12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = stringResource(R.string.history_empty_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.history_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Session list ──────────────────────────────────────────────────────────────

@Composable
private fun SessionList(items: List<SessionRecord>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { session ->
            SessionCard(session = session)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Session card ──────────────────────────────────────────────────────────────

@Composable
private fun SessionCard(session: SessionRecord) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

            // ── Header: date + optional Fach label ────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(session.timestampMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                session.topFachKey?.let { key ->
                    val context = LocalContext.current
                    // topFachKey is stored as a resource entry name ("fach_name_lyric_soprano").
                    // Resolve it to the current-locale display name.
                    // Fall back to the raw value for legacy rows that stored a translated string.
                    val fachName = try {
                        val resId =
                            context.resources.getIdentifier(key, "string", context.packageName)
                        if (resId != 0) context.getString(resId) else key
                    } catch (_: Exception) {
                        key
                    }
                    val suffix =
                        if (session.topFachScore != null && session.topFachMaxScore != null) {
                            "  ${session.topFachScore}/${session.topFachMaxScore}"
                        } else ""
                    Text(
                        text = "$fachName$suffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Comfortable range (primary, larger) ───────────────────────
            Text(
                text = stringResource(R.string.history_label_comfortable_range),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = stringResource(
                    R.string.history_range_format,
                    FachClassifier.hzToNoteName(session.comfortableLowHz),
                    FachClassifier.hzToNoteName(session.comfortableHighHz)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(6.dp))

            // ── Detected extremes (secondary, smaller) ────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SecondaryField(
                    label = stringResource(R.string.history_label_detected_extremes),
                    value = stringResource(
                        R.string.history_range_format,
                        FachClassifier.hzToNoteName(session.detectedMinHz),
                        FachClassifier.hzToNoteName(session.detectedMaxHz)
                    )
                )

                // Passaggio — only shown when the value is meaningful (> 0)
                if (session.passaggioHz > 0f) {
                    SecondaryField(
                        label = stringResource(R.string.history_label_passaggio),
                        value = FachClassifier.hzToNoteName(session.passaggioHz),
                        alignEnd = true
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SecondaryField(
    label: String,
    value: String,
    alignEnd: Boolean = false
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val date = Date(epochMs)
    val datePart = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
    val timePart = DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    return "$datePart · $timePart"
}
