package com.example.snoozer.EditAlarm

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.snoozer.alarm.CategoryExplanation
import com.example.snoozer.onboarding.drawable
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.rememberMotionEnabled
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing

/**
 * Right under the Work / Study cards: what the chosen category will do, in three lines with
 * icons, and a link to the full "How Snoozer works" pages. Tinted with the category's color and
 * cross-fades when the other card is picked, so the difference is seen at the moment of choosing.
 */
@Composable
fun WhatHappensPanel(explanation: CategoryExplanation, onHowItWorks: () -> Unit, modifier: Modifier = Modifier) {
    val fade = if (rememberMotionEnabled()) 250 else 0
    AnimatedContent(
        targetState = explanation,
        transitionSpec = { fadeIn(tween(fade)) togetherWith fadeOut(tween(fade)) },
        contentKey = { it.category },
        label = "whatHappens",
        modifier = modifier.fillMaxWidth(),
    ) { e ->
        val colors = SnoozerTheme.colors.forCategory(e.category)
        val ink = MaterialTheme.colorScheme.onSurface
        Column(
            Modifier
                .fillMaxWidth()
                // A soft wash of the category's color; full strength stays on the chosen card.
                .doodleFill(colors.container.copy(alpha = 0.45f), wobble = DoodleDefaults.WobbleLight, seed = 600)
                .sketchyBorder(MaterialTheme.colorScheme.outline, width = 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = 600)
                .padding(horizontal = Spacing.M, vertical = Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            Text(e.title, style = SnoozerTheme.type.label, color = ink, modifier = Modifier.semantics { heading() })
            e.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    // Decorative: the sentence says the same thing.
                    Icon(painterResource(line.icon.drawable()), contentDescription = null, tint = ink,
                        modifier = Modifier.padding(top = 2.dp).size(20.dp))
                    Text(line.text, style = SnoozerTheme.type.body, color = ink)
                }
            }
            Text(
                "How Work and Study differ",
                style = SnoozerTheme.type.body,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "Open how Snoozer works", role = Role.Button, onClick = onHowItWorks)
                    .padding(vertical = Spacing.S),
            )
        }
    }
}
