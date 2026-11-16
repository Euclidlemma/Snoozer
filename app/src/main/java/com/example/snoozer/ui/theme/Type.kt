package com.example.snoozer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.snoozer.R

/**
 * Gaegu, a hand-drawn pencil font (SIL Open Font License, see assets/licenses).
 * The bundled files are Latin-only subsets (~40 KB each instead of ~3 MB) since the
 * app is English-only. Characters outside the subset fall back to the system font.
 */
val Gaegu = FontFamily(
    Font(R.font.gaegu_regular, FontWeight.Normal),
    Font(R.font.gaegu_bold, FontWeight.Bold),
)

// Gaegu's glyphs are smaller than Roboto's at the same size, so scale every style up.
private const val GaeguScale = 1.15f

private val Material = Typography()

private fun TextStyle.doodle(bold: Boolean) = copy(
    fontFamily = Gaegu,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = fontSize * GaeguScale,
    lineHeight = lineHeight * GaeguScale,
    letterSpacing = 0.sp,
)

// Gaegu ships only Regular and Bold; Material's Medium (500) styles map to Bold.
val Typography = Typography(
    displayLarge = Material.displayLarge.doodle(bold = true),
    displayMedium = Material.displayMedium.doodle(bold = true),
    displaySmall = Material.displaySmall.doodle(bold = true),
    headlineLarge = Material.headlineLarge.doodle(bold = true),
    headlineMedium = Material.headlineMedium.doodle(bold = true),
    headlineSmall = Material.headlineSmall.doodle(bold = true),
    titleLarge = Material.titleLarge.doodle(bold = true),
    titleMedium = Material.titleMedium.doodle(bold = true),
    titleSmall = Material.titleSmall.doodle(bold = true),
    bodyLarge = Material.bodyLarge.doodle(bold = false),
    bodyMedium = Material.bodyMedium.doodle(bold = false),
    bodySmall = Material.bodySmall.doodle(bold = false),
    labelLarge = Material.labelLarge.doodle(bold = true),
    labelMedium = Material.labelMedium.doodle(bold = true),
    labelSmall = Material.labelSmall.doodle(bold = true),
)

/**
 * The few text roles screens should pick from, instead of choosing among Material's 15 styles.
 * Each maps to one Material style, so Material components still look consistent with them.
 */
@Immutable
data class SnoozerType(
    /** Screen and section titles ("Monday · today"). */
    val title: TextStyle,
    /** The key number on a card: times, the next quiet time. */
    val headline: TextStyle,
    /** Normal reading text. */
    val body: TextStyle,
    /** Secondary details: duration, repeat days, hints. */
    val caption: TextStyle,
    /** Bold short labels on buttons and chips. */
    val label: TextStyle,
)

internal fun snoozerType(typography: Typography) = SnoozerType(
    title = typography.titleLarge,
    headline = typography.headlineMedium,
    body = typography.bodyLarge,
    caption = typography.bodyMedium,
    label = typography.labelLarge,
)
