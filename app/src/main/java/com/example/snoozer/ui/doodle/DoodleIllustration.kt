package com.example.snoozer.ui.doodle

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.snoozer.R
import com.example.snoozer.alarm.Category
import com.example.snoozer.ui.theme.SnoozerTheme

/** The artist's drawings. Black line art on a transparent background, 600px, in drawable-nodpi. */
object Illustrations {
    /** The bell-hat guard with crossed arms: Snoozer's mascot. */
    @DrawableRes val Guard = R.drawable.illus_guard
}

/** The drawing for a category: the tired office worker for Work, the pencil-hauler for Study. */
@get:DrawableRes
val Category.illustration: Int
    get() = when (this) {
        Category.Work -> R.drawable.illus_work
        Category.Study -> R.drawable.illus_study
    }

/**
 * One of the artist's drawings on a light backing inside a pencil outline.
 *
 * The drawings are black lines on transparent pixels, so they always need something light
 * behind them; the default [backing] stays near-white in dark mode too. Decorative by default
 * (no [contentDescription]), since the text next to each drawing already says what it means.
 *
 * @param round a circle when true, a rounded square otherwise.
 * @param imageScale how much of the frame the drawing fills; the art has its own margins.
 * @param dimmed fades the drawing, e.g. for the unchosen option of a choice.
 */
@Composable
fun DoodleIllustration(
    @DrawableRes image: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    backing: Color = SnoozerTheme.colors.illustrationBacking,
    outline: Color? = MaterialTheme.colorScheme.onSurface,
    imageScale: Float = 0.95f,
    contentAlignment: Alignment = Alignment.Center,
    dimmed: Boolean = false,
    seed: Int = 0,
    contentDescription: String? = null,
) {
    val corner = if (round) DoodleDefaults.Round else 12.dp
    val shape = remember(corner, seed) {
        SketchyShape(cornerRadius = corner, wobble = DoodleDefaults.WobbleLight, seed = seed)
    }
    // Same order as the other doodle boxes: outline first so clipping doesn't cut its stroke.
    val outlined = if (outline != null) {
        Modifier.sketchyBorder(outline, width = 1.5.dp, cornerRadius = corner,
            wobble = DoodleDefaults.WobbleLight, seed = seed)
    } else Modifier
    Box(
        modifier = modifier
            .size(size)
            .then(outlined)
            .clip(shape)
            .doodleFill(backing, cornerRadius = corner, wobble = DoodleDefaults.WobbleLight, seed = seed),
        contentAlignment = contentAlignment,
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size * imageScale)
                .alpha(if (dimmed) DimmedAlpha else 1f),
        )
    }
}

private const val DimmedAlpha = 0.55f

@Preview(name = "Illustrations - light", widthDp = 360, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Illustrations - dark", widthDp = 360, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DoodleIllustrationPreview() {
    SnoozerTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            DoodleIllustration(Illustrations.Guard, 120.dp, round = true,
                backing = SnoozerTheme.colors.bell, seed = 1)
            DoodleIllustration(Category.Work.illustration, 96.dp, seed = 2)
            DoodleIllustration(Category.Study.illustration, 64.dp, round = true, seed = 3, dimmed = true)
        }
    }
}
