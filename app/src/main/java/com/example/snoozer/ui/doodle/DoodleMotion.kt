package com.example.snoozer.ui.doodle

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

// Small looping motions for the explainer pages. Each reads its animated value inside
// graphicsLayer {}, so the loop redraws without recomposing. All of them stand still when
// the phone's "Remove animations" setting is on (animator duration scale 0).

/**
 * The motion setting provided by an enclosing [ProvideMotionSetting], so a screen full of moving
 * parts shares one observer. Null when none is provided.
 */
val LocalMotionEnabled = compositionLocalOf<Boolean?> { null }

/** Observes the motion setting once for everything inside [content]. */
@Composable
fun ProvideMotionSetting(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMotionEnabled provides observeMotionEnabled(), content = content)
}

/**
 * Whether decorative motion should play: false when the user turned animations off in
 * Android's accessibility or developer settings. Previews always animate-off so they show
 * the resting pose. Uses the enclosing [ProvideMotionSetting] when there is one.
 */
@Composable
fun rememberMotionEnabled(): Boolean = LocalMotionEnabled.current ?: observeMotionEnabled()

@Composable
private fun observeMotionEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    val enabled = remember(resolver) { mutableStateOf(read()) }
    // Follow the setting while the screen is up: turning on "Remove animations" from quick
    // settings stops the motion right away, not only after leaving and coming back.
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled.value = read()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled.value
}

/** A phone buzzing on a table: still for most of the loop, then a short shake. */
@Composable
fun Modifier.buzz(enabled: Boolean = rememberMotionEnabled()): Modifier {
    if (!enabled) return this
    val shake = rememberInfiniteTransition(label = "buzz").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1600
                0f at 880
                -1f at 960
                1f at 1040
                -1f at 1120
                1f at 1200
                0f at 1280
            }
        ),
        label = "buzz",
    )
    return graphicsLayer {
        translationX = shake.value * 3.dp.toPx()
        rotationZ = shake.value * 3f
    }
}

/** A gentle float up and down with a slight tilt, for the mascot. */
@Composable
fun Modifier.bob(enabled: Boolean = rememberMotionEnabled()): Modifier {
    if (!enabled) return this
    val t = rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    return graphicsLayer {
        translationY = -t.value * 4.dp.toPx()
        rotationZ = t.value * 2f
    }
}

/** Grows and fades a little, then back: draws the eye to a spotlight ring. */
@Composable
fun Modifier.pulse(enabled: Boolean = rememberMotionEnabled()): Modifier {
    if (!enabled) return this
    val t = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    return graphicsLayer {
        val scale = 1f + 0.12f * t.value
        scaleX = scale
        scaleY = scale
        alpha = 1f - 0.4f * t.value
    }
}
