package com.yuriy.diapason.ui.screens.analyze

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuriy.diapason.analyzer.RecordingGate
import java.util.Locale

// internal, not private: unit-testable, like formatHz. Locale.ROOT for the same reason —
// a live numeric readout must not switch digit glyphs with the device locale.
internal fun formatGateProgress(sampleCount: Int): String =
    "%d / %d".format(Locale.ROOT, sampleCount.coerceAtMost(RecordingGate.MIN_SAMPLES), RecordingGate.MIN_SAMPLES)

/**
 * Progress toward the minimum number of voice samples, shown while recording until it's
 * reached — so a singer can see they're seconds short instead of pressing Stop blind.
 * Shared by the Analyze and warm-up recording screens.
 */
@Composable
fun RecordingGateProgress(sampleCount: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { RecordingGate.progress(sampleCount) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = formatGateProgress(sampleCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
