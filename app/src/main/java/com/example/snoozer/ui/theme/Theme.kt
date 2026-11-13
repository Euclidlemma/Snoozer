package com.example.snoozer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/** Background and text color for one schedule category (Work or Study). */
@Immutable
data class CategoryColors(val container: Color, val content: Color)

/**
 * Brand colors that Material's color scheme doesn't cover. They stay the same with
 * dynamic color (Android 12+ wallpaper colors), so Work/Study keep their identity.
 */
@Immutable
data class SnoozerColors(
    val work: CategoryColors,
    val study: CategoryColors,
    val bell: Color,
    val illustrationBacking: Color,
    val rainbow: List<Color>,
) {
    /** Colors for an alarm's category string as stored in the database. */
    fun forCategory(category: String): CategoryColors = if (category == "Study") study else work
}

private val LightSnoozerColors = SnoozerColors(
    work = CategoryColors(WorkContainerLight, OnWorkContainerLight),
    study = CategoryColors(StudyContainerLight, OnStudyContainerLight),
    bell = BellLight,
    illustrationBacking = IllustrationBackingLight,
    rainbow = RainbowLight,
)

private val DarkSnoozerColors = SnoozerColors(
    work = CategoryColors(WorkContainerDark, OnWorkContainerDark),
    study = CategoryColors(StudyContainerDark, OnStudyContainerDark),
    bell = BellDark,
    illustrationBacking = IllustrationBackingDark,
    rainbow = RainbowDark,
)

private val LocalSnoozerColors = staticCompositionLocalOf { LightSnoozerColors }

@Composable
fun SnoozerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

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
}
