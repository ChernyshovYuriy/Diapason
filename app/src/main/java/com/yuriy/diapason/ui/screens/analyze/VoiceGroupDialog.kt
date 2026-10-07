package com.yuriy.diapason.ui.screens.analyze

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.VoiceGroupChoice

/**
 * "Male or female voice?" — shown once, before the first recording, to explain why the
 * app asks. Afterwards the same choice lives in [VoiceGroupSwitch] above Start.
 */
@Composable
fun VoiceGroupDialog(
    onSelect: (VoiceGroupChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_group_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.voice_group_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { onSelect(VoiceGroupChoice.MALE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.voice_group_male)) }
                Button(
                    onClick = { onSelect(VoiceGroupChoice.FEMALE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.voice_group_female)) }
                OutlinedButton(
                    onClick = { onSelect(VoiceGroupChoice.UNSURE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.voice_group_unsure)) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/**
 * Male · Female · Not sure, visible above Start on every recording and pre-set to the last
 * choice. Visible rather than a buried setting so a teacher testing several students — or
 * two people sharing a phone — sees it and switches it per singer instead of silently
 * classifying a student within the wrong half of the table.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceGroupSwitch(
    selected: VoiceGroupChoice,
    onSelect: (VoiceGroupChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = VoiceGroupChoice.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
            ) {
                Text(
                    stringResource(
                        when (choice) {
                            VoiceGroupChoice.MALE -> R.string.voice_group_male
                            VoiceGroupChoice.FEMALE -> R.string.voice_group_female
                            VoiceGroupChoice.UNSURE -> R.string.voice_group_unsure
                        }
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
