package com.example.snoozer.ui.doodle

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Shared defaults so shapes, borders and fills built separately line up exactly. */
object DoodleDefaults {
    val CornerRadius: Dp = 14.dp
    val Wobble: Dp = 1.2.dp
    val BorderWidth: Dp = 2.dp
    /** Distance between wobble points; longer = gentler waves. */
    val SegmentLength: Dp = 22.dp
    /**
     * Use as a corner radius to get a wobbly circle (or pill): the radius is capped at
     * half the shorter side, so a square becomes a circle.
     */
    val Round: Dp = 1000.dp
}

/**
 * A rounded rectangle whose edges wobble slightly, like a shape drawn by hand.
 *
 * The wobble is generated from [seed], so the same item keeps the same outline across
 * recompositions (no shimmering). Give different items different seeds (e.g. an alarm
 * id) so a list doesn't look stamped out of one template.
 */
@Immutable
class SketchyShape(
    val cornerRadius: Dp = DoodleDefaults.CornerRadius,
    val wobble: Dp = DoodleDefaults.Wobble,
    val seed: Int = 0,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        with(density) {
            Outline.Generic(
                sketchyPath(size, cornerRadius.toPx(), wobble.toPx(), DoodleDefaults.SegmentLength.toPx(), seed)
            )
        }

    override fun equals(other: Any?): Boolean =
        other is SketchyShape && other.cornerRadius == cornerRadius && other.wobble == wobble && other.seed == seed

    override fun hashCode(): Int = (cornerRadius.hashCode() * 31 + wobble.hashCode()) * 31 + seed
}

/** Closed, smoothed path through the jittered outline points. */
internal fun sketchyPath(size: Size, cornerRadius: Float, wobble: Float, segment: Float, seed: Int): Path {
    val points = sketchyOutlinePoints(size, cornerRadius, wobble, segment, seed)
    val path = Path()
    if (points.size < 3) return path
    // Quadratic curves through midpoints: passes smoothly near every point with no kinks.
    val n = points.size
    val start = midpoint(points[n - 1], points[0])
    path.moveTo(start.x, start.y)
    for (i in 0 until n) {
        val p = points[i]
        val m = midpoint(p, points[(i + 1) % n])
        path.quadraticBezierTo(p.x, p.y, m.x, m.y)
    }
    path.close()
    return path
}

/**
 * Points around a rounded rectangle of [size], each nudged by up to [wobble] px in x
 * and y. Deterministic for the same inputs. Pure math, so it's unit-testable on the JVM.
 */
internal fun sketchyOutlinePoints(
    size: Size,
    cornerRadius: Float,
    wobble: Float,
    segment: Float,
    seed: Int,
): List<Offset> {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return emptyList()
    val r = cornerRadius.coerceIn(0f, min(w, h) / 2f)
    val step = max(segment, 1f)
    val base = ArrayList<Offset>()

    fun edge(from: Offset, to: Offset) {
        val n = max(1, ((to - from).getDistance() / step).roundToInt())
        for (i in 0 until n) base += lerp(from, to, i / n.toFloat())
    }

    // Three points per 90° corner (0°, 30°, 60°); the next edge supplies 90°.
    fun corner(cx: Float, cy: Float, startDegrees: Double) {
        for (i in 0 until 3) {
            val a = (startDegrees + i * 30.0) * PI / 180.0
            base += Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
    }

    edge(Offset(r, 0f), Offset(w - r, 0f))
    corner(w - r, r, -90.0)
    edge(Offset(w, r), Offset(w, h - r))
    corner(w - r, h - r, 0.0)
    edge(Offset(w - r, h), Offset(r, h))
    corner(r, h - r, 90.0)
    edge(Offset(0f, h - r), Offset(0f, r))
    corner(r, r, 180.0)

    val random = Random(seed)
    return base.map {
        Offset(
            it.x + (random.nextFloat() * 2f - 1f) * wobble,
            it.y + (random.nextFloat() * 2f - 1f) * wobble,
        )
    }
}

private fun midpoint(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
