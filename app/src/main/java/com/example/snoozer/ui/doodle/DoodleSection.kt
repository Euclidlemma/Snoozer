package com.example.snoozer.ui.doodle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing

/**
 * A paper card with a light pencil outline and a small heading, grouping related content
 * ("When", "Repeat", "Setup"). Not clickable itself; the content inside is.
 */
@Composable
fun DoodleSection(
    title: String,
    seed: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .doodleFill(scheme.surface, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .sketchyBorder(scheme.outline, width = 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Text(title, style = SnoozerTheme.type.title, modifier = Modifier.semantics { heading() })
        content()
    }
}
