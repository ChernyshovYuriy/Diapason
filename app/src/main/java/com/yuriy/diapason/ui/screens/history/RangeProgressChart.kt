package com.yuriy.diapason.ui.screens.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.FachClassifier
import kotlin.math.roundToInt

/**
 * "Your range over time": the comfortable range as a band, detected extremes as
 * whiskers and the passaggio as dots, one column per session, oldest on the left, on a
 * semitone axis labelled at each C. The reason to come back to the app is seeing this
 * move — the ~14% of users still active after a month had no way to see it before.
 */
@Composable
fun RangeProgressChart(progress: RangeProgress, modifier: Modifier = Modifier) {
    val bandColor = MaterialTheme.colorScheme.primary
    val extremesColor = MaterialTheme.colorScheme.onSurfaceVariant
    val passaggioColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    val first = progress.points.first()
    val last = progress.points.last()
    val description = stringResource(
        R.string.history_progress_cd_format,
        progress.points.size,
        noteName(first.comfortableLow), noteName(first.comfortableHigh),
        noteName(last.comfortableLow), noteName(last.comfortableHigh),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.history_progress_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .semantics { contentDescription = description },
            ) {
                val gutter = 32.dp.toPx()
                val plotLeft = gutter
                val plotWidth = size.width - gutter
                val span = (progress.maxMidi - progress.minMidi).toFloat()
                fun y(midi: Float) = size.height * (1f - (midi - progress.minMidi) / span)
                val count = progress.points.size
                fun x(i: Int) = plotLeft + plotWidth * (i + 0.5f) / count

                // Octave grid, labelled at each C.
                progress.octaveLines.forEach { c ->
                    val lineY = y(c.toFloat())
                    drawLine(gridColor, Offset(plotLeft, lineY), Offset(size.width, lineY), strokeWidth = 1.dp.toPx())
                    val label = textMeasurer.measure(FachClassifier.midiToNoteName(c), labelStyle)
                    val labelY = (lineY - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)
                    drawText(label, topLeft = Offset(0f, labelY))
                }

                // Comfortable range band: along the tops, back along the bottoms.
                val band = Path().apply {
                    progress.points.forEachIndexed { i, p ->
                        if (i == 0) moveTo(x(i), y(p.comfortableHigh)) else lineTo(x(i), y(p.comfortableHigh))
                    }
                    for (i in progress.points.indices.reversed()) lineTo(x(i), y(progress.points[i].comfortableLow))
                    close()
                }
                drawPath(band, bandColor.copy(alpha = 0.25f))
                drawPath(band, bandColor, style = Stroke(width = 2.dp.toPx()))

                progress.points.forEachIndexed { i, p ->
                    // Detected extremes as a whisker with end caps.
                    val cap = 4.dp.toPx()
                    val top = y(p.detectedMax)
                    val bottom = y(p.detectedMin)
                    drawLine(extremesColor, Offset(x(i), top), Offset(x(i), bottom), strokeWidth = 1.dp.toPx())
                    drawLine(extremesColor, Offset(x(i) - cap, top), Offset(x(i) + cap, top), strokeWidth = 1.dp.toPx())
                    drawLine(extremesColor, Offset(x(i) - cap, bottom), Offset(x(i) + cap, bottom), strokeWidth = 1.dp.toPx())
                    // Passaggio dot.
                    drawCircle(passaggioColor, radius = 4.dp.toPx(), center = Offset(x(i), y(p.passaggio)))
                }
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LegendItem(bandColor.copy(alpha = 0.5f), stringResource(R.string.history_label_comfortable_range))
                LegendItem(extremesColor, stringResource(R.string.history_label_detected_extremes))
                LegendItem(passaggioColor, stringResource(R.string.history_label_passaggio))
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun noteName(midi: Float): String = FachClassifier.midiToNoteName(midi.roundToInt().coerceIn(0, 127))
