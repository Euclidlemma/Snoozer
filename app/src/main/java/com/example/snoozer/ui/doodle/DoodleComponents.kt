package com.example.snoozer.ui.doodle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.snoozer.ui.theme.CategoryColors
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A hand-drawn button: wobbly outline, solid or hatched fill, Gaegu label.
 * Keeps Material's ripple, semantics and 48dp minimum touch target via [Surface].
 *
 * @param hatchColor if set, colored-pencil hatching is drawn over [containerColor].
 * @param seed varies the wobble; give neighbouring buttons different seeds.
 * @param wobble outline strength: [DoodleDefaults.Wobble] to stand out, [DoodleDefaults.WobbleLight] for calmer, secondary elements.
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
    wobble: Dp = DoodleDefaults.Wobble,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = remember(seed, wobble) { SketchyShape(wobble = wobble, seed = seed) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .semantics { role = Role.Button }
            .doodleFill(containerColor, hatchColor, wobble = wobble, seed = seed)
            .sketchyBorder(borderColor, wobble = wobble, seed = seed),
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
    wobble: Dp = DoodleDefaults.Wobble,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(seed, wobble) { SketchyShape(wobble = wobble, seed = seed) }
    val decorated = modifier
        .doodleFill(colors.container, colors.hatch, wobble = wobble, seed = seed)
        .sketchyBorder(borderColor, wobble = wobble, seed = seed)
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
 * A round, hand-drawn floating action button in highlighter yellow.
 * [contentDescription] is what screen readers announce, since the content is usually a symbol.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoodleFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    seed: Int = 0,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = remember(seed) { SketchyShape(cornerRadius = DoodleDefaults.Round, seed = seed) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .doodleFill(colors.tertiary, colors.onTertiary, cornerRadius = DoodleDefaults.Round, seed = seed, hatchAlpha = 0.12f)
            .sketchyBorder(colors.onSurface, cornerRadius = DoodleDefaults.Round, seed = seed),
        shape = shape,
        color = Color.Transparent,
        contentColor = colors.onTertiary,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** A gently wavy hand-drawn line, used instead of a straight divider. */
@Composable
fun DoodleDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline,
    seed: Int = 0,
) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .drawWithCache {
                val random = Random(seed)
                val y = size.height / 2f
                val segments = max(2, (size.width / 60.dp.toPx()).roundToInt())
                val step = size.width / segments
                val path = Path().apply {
                    moveTo(0f, y)
                    for (i in 1..segments) {
                        quadraticBezierTo(
                            step * (i - 0.5f), y + (random.nextFloat() * 2f - 1f) * 2.dp.toPx(),
                            step * i, y + (random.nextFloat() * 2f - 1f) * 1.dp.toPx(),
                        )
                    }
                }
                val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind { drawPath(path, color, style = stroke) }
            }
    )
}

/**
 * A confirm/cancel dialog in the doodle style: paper card with a pencil border,
 * doodle buttons, Gaegu text. [destructive] paints the confirm button in the error color.
 */
@Composable
fun DoodleAlertDialog(
    onDismissRequest: () -> Unit,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    title: String? = null,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val radius = 24.dp
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.sketchyBorder(colors.onSurface, cornerRadius = radius, seed = 101),
        shape = SketchyShape(cornerRadius = radius, seed = 101),
        containerColor = colors.surface,
        // No elevation tint: it would turn the paper color slightly purple-grey.
        tonalElevation = 0.dp,
        title = if (title != null) { { Text(title) } } else null,
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            DoodleButton(
                onClick = onConfirm,
                seed = 102,
                containerColor = if (destructive) colors.errorContainer else colors.secondaryContainer,
                contentColor = if (destructive) colors.onErrorContainer else colors.onSecondaryContainer,
                hatchColor = if (destructive) colors.error else colors.primary,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            DoodleButton(
                onClick = onDismissRequest,
                seed = 103,
                containerColor = Color.Transparent,
                contentColor = colors.onSurface,
            ) { Text(dismissLabel) }
        },
    )
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
    wobble: Dp = DoodleDefaults.Wobble,
) {
    val colors = MaterialTheme.colorScheme
    val shape = remember(seed, wobble) { SketchyShape(cornerRadius = 12.dp, wobble = wobble, seed = seed) }
    val fill = if (selected) colors.secondaryContainer else Color.Transparent
    val hatch = if (selected) colors.primary else null
    val border = if (selected) colors.onSurface else colors.outline
    Surface(
        onClick = onClick,
        modifier = modifier
            .semantics { this.selected = selected }
            .doodleFill(fill, hatch, cornerRadius = 12.dp, wobble = wobble, seed = seed, hatchAlpha = 0.3f)
            .sketchyBorder(border, width = if (selected) 2.dp else 1.5.dp, cornerRadius = 12.dp, wobble = wobble, seed = seed),
        shape = shape,
        color = Color.Transparent,
        contentColor = colors.onSurface,
    ) {
        // Centered, for chips stretched wider than their label (Every week / One date).
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
