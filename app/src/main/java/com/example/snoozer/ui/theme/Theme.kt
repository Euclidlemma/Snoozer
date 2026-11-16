package com.example.snoozer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Pencil on paper
private val LightColorScheme = lightColorScheme(
    primary = PencilBlue,
    onPrimary = Color.White,
    secondary = PencilLilac,
    onSecondary = Color.White,
    secondaryContainer = PaperButton,
    onSecondaryContainer = Graphite,
    tertiary = PencilYellow,
    onTertiary = Graphite,
    background = Paper,
    onBackground = Graphite,
    surface = PaperCard,
    onSurface = Graphite,
    surfaceVariant = PaperShade3,
    onSurfaceVariant = GraphiteSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperShade1,
    surfaceContainer = PaperShade2,
    surfaceContainerHigh = PaperShade3,
    surfaceContainerHighest = PaperShade4,
    outline = GraphiteLine,
)

// Chalk on board
private val DarkColorScheme = darkColorScheme(
    primary = ChalkBlue,
    onPrimary = Color(0xFF10273A),
    secondary = ChalkLilac,
    onSecondary = Color(0xFF321A3A),
    secondaryContainer = BoardButton,
    onSecondaryContainer = Chalk,
    tertiary = ChalkYellow,
    onTertiary = Board,
    background = Board,
    onBackground = Chalk,
    surface = BoardCard,
    onSurface = Chalk,
    surfaceVariant = BoardShade3,
    onSurfaceVariant = ChalkSoft,
    surfaceContainerLowest = BoardShade0,
    surfaceContainerLow = BoardShade1,
    surfaceContainer = BoardShade2,
    surfaceContainerHigh = BoardShade3,
    surfaceContainerHighest = BoardShade4,
    outline = ChalkLine,
)

/**
 * Colors for one schedule category (Work or Study): the card's base fill, its text,
 * and the colored-pencil hatching drawn over the fill.
 */
@Immutable
data class CategoryColors(val container: Color, val content: Color, val hatch: Color)

/** Brand colors that Material's color scheme doesn't cover. */
@Immutable
data class SnoozerColors(
    val work: CategoryColors,
    val study: CategoryColors,
    val bell: Color,
    val illustrationBacking: Color,
    val rainbow: List<Color>,
    /** Faint ruled lines on the paper background (notebook look). */
    val paperLine: Color,
    /** Highlighter-marker stroke, e.g. under the selected day. */
    val highlighter: Color,
) {
    /** Colors for an alarm's category string as stored in the database. */
    fun forCategory(category: String): CategoryColors = if (category == "Study") study else work
}

private val LightSnoozerColors = SnoozerColors(
    work = CategoryColors(WorkContainerLight, OnWorkContainerLight, WorkHatchLight),
    study = CategoryColors(StudyContainerLight, OnStudyContainerLight, StudyHatchLight),
    bell = BellLight,
    illustrationBacking = IllustrationBackingLight,
    rainbow = RainbowLight,
    paperLine = PaperLineLight,
    highlighter = PencilYellow,
)

private val DarkSnoozerColors = SnoozerColors(
    work = CategoryColors(WorkContainerDark, OnWorkContainerDark, WorkHatchDark),
    study = CategoryColors(StudyContainerDark, OnStudyContainerDark, StudyHatchDark),
    bell = BellDark,
    illustrationBacking = IllustrationBackingDark,
    rainbow = RainbowDark,
    paperLine = PaperLineDark,
    highlighter = ChalkYellow,
)

private val LocalSnoozerColors = staticCompositionLocalOf { LightSnoozerColors }

/**
 * App theme. Wallpaper-based dynamic color (Android 12+) is intentionally not used:
 * it would replace the paper and pencil palette that the doodle style depends on.
 */
@Composable
fun SnoozerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalSnoozerColors provides if (darkTheme) DarkSnoozerColors else LightSnoozerColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/** Access point for brand colors, alongside MaterialTheme.colorScheme. */
object SnoozerTheme {
    val colors: SnoozerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSnoozerColors.current

    /** Text roles (title, headline, body, caption, label) built from the theme's typography. */
    val type: SnoozerType
        @Composable
        @ReadOnlyComposable
        get() = snoozerType(MaterialTheme.typography)
}
