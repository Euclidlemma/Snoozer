package com.example.snoozer.ui.theme

import androidx.compose.ui.graphics.Color

// Snoozer's doodle palette. Light mode is graphite pencil on paper; dark mode is
// chalk on a slate board. Screens never use these directly: they read
// MaterialTheme.colorScheme or SnoozerTheme.colors, so both modes stay in one place.

// ---- Light: pencil on paper ----
val Paper = Color(0xFFFAF6EC)                 // page background
val PaperCard = Color(0xFFFFFDF7)             // raised surfaces
val PaperShade1 = Color(0xFFFCF8EF)
val PaperShade2 = Color(0xFFF6F0E3)
val PaperShade3 = Color(0xFFF1EADB)
val PaperShade4 = Color(0xFFEBE3D2)
val Graphite = Color(0xFF2E2B26)              // main ink
val GraphiteSoft = Color(0xFF5A5449)          // secondary ink
val GraphiteLine = Color(0xFF6B6457)          // outlines
val PencilBlue = Color(0xFF3E6E99)
val PencilLilac = Color(0xFF7E5A8C)
val PencilYellow = Color(0xFFF2C94C)
val PaperButton = Color(0xFFEDE4D0)           // soft (tonal) buttons

// ---- Dark: chalk on board ----
val Board = Color(0xFF2A2D2F)
val BoardCard = Color(0xFF303437)
val BoardShade0 = Color(0xFF24272A)
val BoardShade1 = Color(0xFF2E3134)
val BoardShade2 = Color(0xFF33373A)
val BoardShade3 = Color(0xFF3A3E41)
val BoardShade4 = Color(0xFF42464A)
val Chalk = Color(0xFFEDEAE2)
val ChalkSoft = Color(0xFFC9C5BB)
val ChalkLine = Color(0xFFA8A398)
val ChalkBlue = Color(0xFF8FBCE6)
val ChalkLilac = Color(0xFFD3A9DE)
val ChalkYellow = Color(0xFFE8C55A)
val BoardButton = Color(0xFF444A4E)

// ---- Categories (colored-pencil fills) ----
val WorkContainerLight = Color(0xFFC9DDF0)
val OnWorkContainerLight = Color(0xFF1F3346)
val WorkContainerDark = Color(0xFF2F4B63)
val OnWorkContainerDark = Color(0xFFDCEBF8)

val StudyContainerLight = Color(0xFFE3D0EA)
val OnStudyContainerLight = Color(0xFF3A2244)
val StudyContainerDark = Color(0xFF4E3B57)
val OnStudyContainerDark = Color(0xFFF0E0F5)

// Pencil hatching drawn over the category fills (applied semi-transparent)
val WorkHatchLight = Color(0xFF5B8DB8)
val StudyHatchLight = Color(0xFFA57BB0)
val WorkHatchDark = Color(0xFF8FBCE6)
val StudyHatchDark = Color(0xFFD3A9DE)

// Ruled notebook lines on the page background
val PaperLineLight = Color(0xFFE9E1CC)
val PaperLineDark = Color(0xFF363A3D)

// The yellow circle behind the bell on the home screen
val BellLight = PencilYellow
val BellDark = ChalkYellow

// Backing behind the black-line illustrations so they stay readable on any card
val IllustrationBackingLight = Color(0xFFFFFFFF)
val IllustrationBackingDark = Color(0xFFF2F0EA)

// Decorative bars on the home screen, left to right, in colored-pencil tones
val RainbowLight = listOf(
    Color(0xFFD9534F), // red
    Color(0xFFF0A35E), // orange
    Color(0xFF8CC7D9), // sky
    Color(0xFFE6D58F), // sand
    Color(0xFFB39DDB), // lilac
    Color(0xFF8BC17A), // leaf
    Color(0xFF6F6AC0), // slate blue
)

// Same hues, toned down so they don't glare on the dark board
val RainbowDark = listOf(
    Color(0xFFB33A3A),
    Color(0xFFD9904D),
    Color(0xFF7FBFBF),
    Color(0xFFCFC48A),
    Color(0xFF9A86CC),
    Color(0xFF4DB34D),
    Color(0xFF6F62C9),
)
