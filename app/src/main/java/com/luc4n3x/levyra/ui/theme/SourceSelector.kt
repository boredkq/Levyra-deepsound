package com.luc4n3x.levyra.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luc4n3x.levyra.feature.source.SourceId

/**
 * Picks the globally active [SourceId] (design doc §3). Additive control for the Design settings
 * area; it changes no existing layout and, like every other setting, flows through
 * `onInterfaceSettings` → `setInterfaceSettings` so the choice is persisted. Only the sources the
 * build registers are meaningful; until Yandex is wired (M2) the user sees YouTube selected.
 */
@Composable
internal fun SourceSelector(selected: SourceId, onSelect: (SourceId) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceId.entries.forEach { source ->
                FilterChip(
                    selected = source == selected,
                    onClick = { if (source != selected) onSelect(source) },
                    label = { Text(source.label) }
                )
            }
        }
    }
}
