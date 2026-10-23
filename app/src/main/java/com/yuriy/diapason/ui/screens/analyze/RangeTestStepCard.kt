package com.yuriy.diapason.ui.screens.analyze

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.RangeTest
import com.yuriy.diapason.ui.screens.comparison.formatTime

/**
 * The range-test exercise running now, with its countdown — styled like the warm-up's
 * running step so the two guided flows read the same.
 */
@Composable
fun RangeTestStepCard(step: RangeTest.Step, secondsLeft: Int, modifier: Modifier = Modifier) {
    val (titleRes, bodyRes) = step.strings()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(
                    R.string.range_test_step_format,
                    step.ordinal + 1,
                    RangeTest.STEPS.size,
                    stringResource(titleRes),
                    formatTime(secondsLeft),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Title and instruction of each [RangeTest.Step]. */
private fun RangeTest.Step.strings(): Pair<Int, Int> = when (this) {
    RangeTest.Step.ComfortableNote -> R.string.range_test_step_note_title to R.string.range_test_step_note_body
    RangeTest.Step.Song -> R.string.range_test_step_song_title to R.string.range_test_step_song_body
    RangeTest.Step.SlideUp -> R.string.range_test_step_up_title to R.string.range_test_step_up_body
    RangeTest.Step.SlideDown -> R.string.range_test_step_down_title to R.string.range_test_step_down_body
}
