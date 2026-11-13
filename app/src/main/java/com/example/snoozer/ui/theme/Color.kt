package com.example.snoozer.ui.theme

import androidx.compose.ui.graphics.Color

// Material baseline (used when dynamic color is unavailable, i.e. below Android 12)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Snoozer brand palette. Every color the screens use comes from here through
// SnoozerTheme.colors, so light and dark mode stay in one place.

// Category: Work (blue)
val WorkContainerLight = Color(0xFF90D5FF)
val OnWorkContainerLight = Color(0xFF0B2A3D)
val WorkContainerDark = Color(0xFF1F4A63)
val OnWorkContainerDark = Color(0xFFCFEAFF)

// Category: Study (lilac)
val StudyContainerLight = Color(0xFFDAB1DA)
val OnStudyContainerLight = Color(0xFF3A1D3A)
val StudyContainerDark = Color(0xFF573A57)
val OnStudyContainerDark = Color(0xFFF5DDF5)

// The yellow circle behind the bell on the home screen
val BellLight = Color(0xFFFFE600)
val BellDark = Color(0xFFE6C700)

// Backing behind the black-line illustrations so they stay readable on dark cards
val IllustrationBackingLight = Color(0xFFFFFFFF)
val IllustrationBackingDark = Color(0xFFF2F2F2)

// Decorative bars on the home screen, left to right
val RainbowLight = listOf(
    Color(0xFFCC0000), // red
    Color(0xFFFFB266), // orange
    Color(0xFFCCFFFF), // light blue
    Color(0xFFFFFACD), // beige
    Color(0xFFD0BCFF), // purple
    Color(0xFF00FF00), // lime
    Color(0xFF6A5ACD), // slate blue
)

// Same hues, toned down so they don't glare against a dark background
val RainbowDark = listOf(
    Color(0xFFB33A3A),
    Color(0xFFD9904D),
    Color(0xFF7FBFBF),
    Color(0xFFCFC48A),
    Color(0xFF9A86CC),
    Color(0xFF4DB34D),
    Color(0xFF6F62C9),
)
