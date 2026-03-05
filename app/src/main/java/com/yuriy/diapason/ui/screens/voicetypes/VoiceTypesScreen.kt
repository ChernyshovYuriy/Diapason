package com.yuriy.diapason.ui.screens.voicetypes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.ALL_FACH
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.FachDefinition

// Fach grouped by category in display order
private val CATEGORY_ORDER = listOf(
    R.string.fach_category_soprano,
    R.string.fach_category_mezzo_soprano,
    R.string.fach_category_contralto,
    R.string.fach_category_countertenor,
    R.string.fach_category_tenor,
    R.string.fach_category_baritone,
    R.string.fach_category_bass
)

@Composable
fun VoiceTypesScreen() {

    val grouped = remember {
        CATEGORY_ORDER.associateWith { categoryRes ->
            ALL_FACH.filter { it.categoryRes == categoryRes }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.voice_types_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.voice_types_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }

        CATEGORY_ORDER.forEach { categoryRes ->
            val fachwList = grouped[categoryRes] ?: return@forEach
            if (fachwList.isEmpty()) return@forEach

            item(key = "header_$categoryRes") {
                CategoryHeader(categoryRes = categoryRes)
            }

            items(fachwList.size, key = { fachwList[it].nameRes }) { index ->
                FachCard(fach = fachwList[index])
                Spacer(Modifier.height(8.dp))
            }

            item(key = "spacer_$categoryRes") {
                Spacer(Modifier.height(8.dp))
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryHeader(categoryRes: Int) {
    Text(
        text = stringResource(categoryRes).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FachCard(fach: FachDefinition) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // ── Collapsed header ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(fach.nameRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${FachClassifier.hzToNoteName(fach.rangeMinHz)} – ${
                            FachClassifier.hzToNoteName(fach.rangeMaxHz)
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.voice_types_cd_collapse
                        else R.string.voice_types_cd_expand
                    ),
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Expanded detail ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 10.dp))

                    Text(
                        stringResource(fach.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(10.dp))

                    RangeGrid(fach)

                    // Famous roles
                    val famousRoles = stringArrayResource(fach.famousRolesRes)
                    if (famousRoles.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        DetailSectionLabel(
                            icon = Icons.Filled.MusicNote,
                            label = stringResource(R.string.voice_types_label_famous_roles)
                        )
                        famousRoles.forEach { role ->
                            Text(
                                stringResource(R.string.common_bullet_item, role),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // Example singers
                    val exampleSingers = stringArrayResource(fach.exampleSingersRes)
                    if (exampleSingers.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        DetailSectionLabel(
                            icon = Icons.Filled.Person,
                            label = stringResource(R.string.voice_types_label_example_singers)
                        )
                        Text(
                            text = exampleSingers.joinToString(stringResource(R.string.voice_types_separator_dot)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeGrid(fach: FachDefinition) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RangePill(
            label = stringResource(R.string.voice_types_label_range),
            value = "${FachClassifier.hzToNoteName(fach.rangeMinHz)} – ${
                FachClassifier.hzToNoteName(fach.rangeMaxHz)
            }"
        )
        RangePill(
            label = stringResource(R.string.voice_types_label_tessitura),
            value = "${FachClassifier.hzToNoteName(fach.tessituraMinHz)} – ${
                FachClassifier.hzToNoteName(fach.tessituraMaxHz)
            }"
        )
        RangePill(
            label = stringResource(R.string.voice_types_label_passaggio),
            value = FachClassifier.hzToNoteName(fach.passaggioHz)
        )
    }
}

@Composable
private fun RangePill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun DetailSectionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 2.dp)
    ) {
        Icon(
            icon, contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.voice_types_detail_label_format, label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
