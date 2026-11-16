package com.example.snoozer.ui.doodle

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val TrackWidth = 48.dp
private val TrackHeight = 28.dp
private val ThumbSize = 20.dp
private val ThumbInset = 4.dp

/**
 * A hand-drawn on/off switch: a wobbly pill with a pencil-circle thumb that slides across.
 * On: hatched track and a filled thumb. Off: an empty outline and a grey thumb.
 * Keeps the 48dp touch target and announces itself as a switch.
 */
@Composable
fun DoodleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    seed: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - ThumbInset * 2 else 0.dp,
        label = "thumb",
    )
    val trackFill = if (checked) colors.secondaryContainer else Color.Transparent
    val trackBorder = if (checked) colors.onSurface else colors.outline
    val thumbFill = if (checked) colors.primary else colors.outline

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(TrackWidth, TrackHeight)
                .doodleFill(trackFill, if (checked) colors.primary else null, cornerRadius = DoodleDefaults.Round,
                    wobble = DoodleDefaults.WobbleLight, seed = seed, hatchAlpha = 0.3f)
                .sketchyBorder(trackBorder, width = 1.5.dp, cornerRadius = DoodleDefaults.Round,
                    wobble = DoodleDefaults.WobbleLight, seed = seed),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = ThumbInset)
                    .offset(x = thumbOffset)
                    .size(ThumbSize)
                    .doodleFill(thumbFill, cornerRadius = DoodleDefaults.Round, wobble = DoodleDefaults.WobbleLight, seed = seed + 1)
                    .sketchyBorder(colors.onSurface, width = 1.5.dp, cornerRadius = DoodleDefaults.Round,
                        wobble = DoodleDefaults.WobbleLight, seed = seed + 1)
            )
        }
    }
}
