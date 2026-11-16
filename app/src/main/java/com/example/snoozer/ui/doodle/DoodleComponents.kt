package com.example.snoozer.ui.doodle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.snoozer.ui.theme.CategoryColors

/**
 * A hand-drawn button: wobbly outline, solid or hatched fill, Gaegu label.
 * Keeps Material's ripple, semantics and 48dp minimum touch target via [Surface].
 *
 * @param hatchColor if set, colored-pencil hatching is drawn over [containerColor].
 * @param seed varies the wobble; give neighbouring buttons different seeds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoodleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    hatchColor: Color? = null,
    borderColor: Color = MaterialTheme.colorScheme.onSurface,
    seed: Int = 0,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = remember(seed) { SketchyShape(seed = seed) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .semantics { role = Role.Button }
            .doodleFill(containerColor, hatchColor, seed = seed)
            .sketchyBorder(borderColor, seed = seed),
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/**
 * A hand-drawn card with a hatched colored-pencil fill. Clickable when [onClick] is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoodleCard(
    colors: CategoryColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = MaterialTheme.colorScheme.onSurface,
    seed: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(seed) { SketchyShape(seed = seed) }
    val decorated = modifier
        .doodleFill(colors.container, colors.hatch, seed = seed)
        .sketchyBorder(borderColor, seed = seed)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = decorated,
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.content,
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = decorated,
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.content,
        ) { Column(content = content) }
    }
}

/**
 * A hand-drawn toggle chip (e.g. a weekday). Selected chips get a hatched fill;
 * unselected chips are just an outline on the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoodleChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    seed: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val shape = remember(seed) { SketchyShape(cornerRadius = 12.dp, seed = seed) }
    val fill = if (selected) colors.secondaryContainer else Color.Transparent
    val hatch = if (selected) colors.primary else null
    val border = if (selected) colors.onSurface else colors.outline
    Surface(
        onClick = onClick,
        modifier = modifier
            .semantics { this.selected = selected }
            .doodleFill(fill, hatch, cornerRadius = 12.dp, seed = seed, hatchAlpha = 0.3f)
            .sketchyBorder(border, width = if (selected) 2.dp else 1.5.dp, cornerRadius = 12.dp, seed = seed),
        shape = shape,
        color = Color.Transparent,
        contentColor = colors.onSurface,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
