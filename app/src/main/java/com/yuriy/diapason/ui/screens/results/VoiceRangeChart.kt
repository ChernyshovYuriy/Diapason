package com.yuriy.diapason.ui.screens.results

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.FamilyRange
import com.yuriy.diapason.analyzer.VoiceProfile
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/**
 * The singer's measured range drawn against each voice family's typical range — the chart a
 * voice teacher sketches, and an answer that claims nothing pitch can't support. Top row is
 * the singer (comfortable range as a band, lowest–highest as a line, passaggio as a dot);
 * below, each family's typical comfortable range as a band and full range as a line. A light
 * column carries the singer's comfortable range down through the families.
 *
 * Low to high runs left to right in every language, like a keyboard and the History chart.
 */
@Composable
fun VoiceRangeChart(profile: VoiceProfile, families: List<FamilyRange>, modifier: Modifier = Modifier) {
    val youColor = MaterialTheme.colorScheme.primary
    val passaggioColor = MaterialTheme.colorScheme.tertiary
    val familyColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val youLabelStyle = labelStyle.copy(color = youColor, fontWeight = FontWeight.Bold)
    val textMeasurer = rememberTextMeasurer()

    val youLabel = stringResource(R.string.results_chart_you)
    val familyLabels = families.map { stringResource(it.categoryRes) }
    val description = stringResource(
        R.string.results_chart_cd_format,
        FachClassifier.hzToNoteName(profile.comfortableLowHz),
        FachClassifier.hzToNoteName(profile.comfortableHighHz),
        familyLabels.joinToString(", "),
    )

    val lowest = minOf(profile.detectedMinHz, families.minOf { it.rangeMinHz })
    val highest = maxOf(profile.detectedMaxHz, families.maxOf { it.rangeMaxHz })
    // Axis snapped outward to whole octaves at C, so every gridline is a labelled C.
    val minMidi = floor(midi(lowest) / 12f) * 12f
    val maxMidi = ceil(midi(highest) / 12f) * 12f

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.results_chart_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            val rowCount = families.size + 1
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((ROW_HEIGHT_DP * rowCount + AXIS_HEIGHT_DP).dp)
                    .semantics { contentDescription = description },
            ) {
                val rowHeight = ROW_HEIGHT_DP.dp.toPx()
                val labels = listOf(textMeasurer.measure(youLabel, youLabelStyle)) +
                    familyLabels.map { textMeasurer.measure(it, labelStyle) }
                val gutter = (labels.maxOf { it.size.width } + 8.dp.toPx()).coerceAtMost(size.width * 0.35f)
                val plotWidth = size.width - gutter
                val plotBottom = rowHeight * rowCount
                fun x(hz: Float) = gutter + plotWidth * (midi(hz) - minMidi) / (maxMidi - minMidi)
                fun xMidi(m: Float) = gutter + plotWidth * (m - minMidi) / (maxMidi - minMidi)
                fun rowCenter(i: Int) = rowHeight * (i + 0.5f)

                // Octave gridlines, labelled at each C under the plot.
                var c = minMidi
                while (c <= maxMidi) {
                    val lineX = xMidi(c)
                    drawLine(gridColor, Offset(lineX, 0f), Offset(lineX, plotBottom), strokeWidth = 1.dp.toPx())
                    val label = textMeasurer.measure(FachClassifier.midiToNoteName(c.toInt()), labelStyle)
                    val labelX = (lineX - label.size.width / 2f).coerceIn(gutter, size.width - label.size.width)
                    drawText(label, topLeft = Offset(labelX, plotBottom + 4.dp.toPx()))
                    c += 12f
                }

                // The singer's comfortable range, carried down through the families.
                drawRect(
                    youColor.copy(alpha = 0.08f),
                    topLeft = Offset(x(profile.comfortableLowHz), 0f),
                    size = Size(x(profile.comfortableHighHz) - x(profile.comfortableLowHz), plotBottom),
                )

                labels.forEachIndexed { i, label ->
                    drawText(label, topLeft = Offset(0f, rowCenter(i) - label.size.height / 2f))
                }

                fun span(from: Float, to: Float, row: Int, color: Color, thickness: Float) {
                    val y = rowCenter(row)
                    drawRoundRect(
                        color,
                        topLeft = Offset(x(from), y - thickness / 2f),
                        size = Size((x(to) - x(from)).coerceAtLeast(thickness), thickness),
                        cornerRadius = CornerRadius(thickness / 2f),
                    )
                }

                // You.
                drawLine(
                    youColor, Offset(x(profile.detectedMinHz), rowCenter(0)), Offset(x(profile.detectedMaxHz), rowCenter(0)),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                )
                span(profile.comfortableLowHz, profile.comfortableHighHz, 0, youColor, 12.dp.toPx())
                drawCircle(passaggioColor, radius = 5.dp.toPx(), center = Offset(x(profile.estimatedPassaggioHz), rowCenter(0)))

                // Voice families.
                families.forEachIndexed { index, family ->
                    val row = index + 1
                    drawLine(
                        familyColor.copy(alpha = 0.45f),
                        Offset(x(family.rangeMinHz), rowCenter(row)), Offset(x(family.rangeMaxHz), rowCenter(row)),
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                    )
                    span(family.tessituraMinHz, family.tessituraMaxHz, row, familyColor.copy(alpha = 0.55f), 8.dp.toPx())
                }
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LegendItem(youColor, stringResource(R.string.history_label_comfortable_range))
                LegendItem(youColor.copy(alpha = 0.5f), stringResource(R.string.history_label_detected_extremes))
                LegendItem(passaggioColor, stringResource(R.string.history_label_passaggio))
                LegendItem(familyColor.copy(alpha = 0.55f), stringResource(R.string.results_chart_legend_typical))
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

/** Fractional MIDI note number (A4 = 69). */
private fun midi(hz: Float): Float = (12.0 * ln(hz / 440.0) / ln(2.0) + 69.0).toFloat()

private const val ROW_HEIGHT_DP = 28
private const val AXIS_HEIGHT_DP = 20
