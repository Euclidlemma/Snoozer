package com.example.snoozer.ui.doodle

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.snoozer.R
import com.example.snoozer.ui.theme.SnoozerTheme

/**
 * A small hand-drawn phone used to *show* what Snoozer does (buzzing, silent, a notice sliding
 * in) instead of only saying it. Drawn with the doodle outline, not a screenshot, so it follows
 * light and dark mode. [content] fills the screen area, centered by default.
 */
@Composable
fun DoodlePhone(
    modifier: Modifier = Modifier,
    width: Dp = 92.dp,
    height: Dp = 150.dp,
    screen: Color = SnoozerTheme.colors.illustrationBacking,
    seed: Int = 0,
    content: @Composable BoxScope.() -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val corner = 16.dp
    val shape = remember(seed) { SketchyShape(cornerRadius = corner, wobble = DoodleDefaults.WobbleLight, seed = seed) }
    Box(
        modifier = modifier
            .size(width, height)
            .sketchyBorder(ink, width = 2.dp, cornerRadius = corner, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .clip(shape)
            .doodleFill(screen, cornerRadius = corner, wobble = DoodleDefaults.WobbleLight, seed = seed)
            // The speaker slit at the top, so it reads as a phone at a glance.
            .drawWithContent {
                drawContent()
                val y = 8.dp.toPx()
                drawLine(ink, Offset(size.width * 0.4f, y), Offset(size.width * 0.6f, y),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// The pieces from step 0 together: the new icons, the phone, and the motions at rest.
@Preview(name = "Explainer kit - light", widthDp = 360, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Explainer kit - dark", widthDp = 360, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExplainerKitPreview() {
    SnoozerTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(R.drawable.ic_doodle_vibrate, R.drawable.ic_doodle_silent, R.drawable.ic_doodle_alarm,
                    R.drawable.ic_doodle_call, R.drawable.ic_doodle_notify).forEach {
                    Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DoodlePhone(Modifier.buzz(), seed = 1) {
                    Icon(painterResource(R.drawable.ic_doodle_vibrate), contentDescription = null,
                        tint = Color(0xFF2E2B26), modifier = Modifier.size(36.dp))
                }
                DoodlePhone(seed = 2) {
                    Icon(painterResource(R.drawable.ic_doodle_silent), contentDescription = null,
                        tint = Color(0xFF2E2B26), modifier = Modifier.size(36.dp))
                }
                Image(painterResource(Illustrations.Guard), contentDescription = null,
                    modifier = Modifier.size(80.dp).bob())
                Text("+", style = SnoozerTheme.type.headline, modifier = Modifier.pulse())
            }
        }
    }
}
