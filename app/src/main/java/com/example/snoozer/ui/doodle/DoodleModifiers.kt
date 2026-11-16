package com.example.snoozer.ui.doodle

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pencil outline: a firm stroke plus a fainter second pass with a different wobble,
 * like going over a line twice. Drawn on top of the content's edges.
 *
 * Use the same [cornerRadius], [wobble] and [seed] as the element's [SketchyShape]
 * so the outline matches the clipped area.
 */
fun Modifier.sketchyBorder(
    color: Color,
    width: Dp = DoodleDefaults.BorderWidth,
    cornerRadius: Dp = DoodleDefaults.CornerRadius,
    wobble: Dp = DoodleDefaults.Wobble,
    seed: Int = 0,
): Modifier = drawWithCache {
    val segment = DoodleDefaults.SegmentLength.toPx()
    val firm = sketchyPath(size, cornerRadius.toPx(), wobble.toPx(), segment, seed)
    // Different seed and a bit more wobble, so the second pass doesn't sit exactly on the first.
    val faint = sketchyPath(size, cornerRadius.toPx(), wobble.toPx() * 1.4f, segment, seed + 7919)
    val firmStroke = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val faintStroke = Stroke(width.toPx() * 0.55f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val faintColor = color.copy(alpha = color.alpha * 0.45f)
    onDrawWithContent {
        drawContent()
        drawPath(firm, color, style = firmStroke)
        drawPath(faint, faintColor, style = faintStroke)
    }
}

/**
 * Fills the sketchy shape with [base], then (if [hatch] is set) diagonal colored-pencil
 * strokes on top. Line spacing varies slightly per [seed] so it doesn't look machine-made.
 * Drawn behind the content.
 */
fun Modifier.doodleFill(
    base: Color,
    hatch: Color? = null,
    cornerRadius: Dp = DoodleDefaults.CornerRadius,
    wobble: Dp = DoodleDefaults.Wobble,
    seed: Int = 0,
    spacing: Dp = 7.dp,
    angleDegrees: Float = -35f,
    lineWidth: Dp = 2.dp,
    hatchAlpha: Float = 0.35f,
): Modifier = drawWithCache {
    val outline = sketchyPath(
        size, cornerRadius.toPx(), wobble.toPx(), DoodleDefaults.SegmentLength.toPx(), seed
    )
    // Lines are laid out across the diagonal so they cover the box at any rotation.
    val diagonal = sqrt(size.width * size.width + size.height * size.height)
    val step = spacing.toPx()
    val lines = if (hatch == null || step <= 0f) emptyList() else {
        val random = Random(seed + 31)
        generateSequence(-diagonal / 2f) { it + step * (0.85f + random.nextFloat() * 0.3f) }
            .takeWhile { it <= diagonal / 2f }
            .toList()
    }
    val lineColor = hatch?.copy(alpha = hatchAlpha)
    val stroke = lineWidth.toPx()
    onDrawBehind {
        clipPath(outline) {
            drawRect(base)
            if (lineColor != null) {
                rotate(angleDegrees, pivot = center) {
                    for (x in lines) {
                        drawLine(
                            color = lineColor,
                            start = Offset(center.x + x, center.y - diagonal / 2f),
                            end = Offset(center.x + x, center.y + diagonal / 2f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Page background: plain [paper], or ruled like a notebook when [lineColor] is set.
 */
fun Modifier.paperBackground(
    paper: Color,
    lineColor: Color? = null,
    lineSpacing: Dp = 30.dp,
    lineWidth: Dp = 1.dp,
): Modifier = drawBehind {
    drawRect(paper)
    if (lineColor != null) {
        val step = lineSpacing.toPx()
        var y = step
        while (y < size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), lineWidth.toPx())
            y += step
        }
    }
}

/**
 * A highlighter-marker swipe behind the lower part of the content, e.g. under a
 * selected tab label. Slightly tilted and rounded so it looks hand-applied.
 */
fun Modifier.highlighter(
    color: Color,
    alpha: Float = 0.7f,
    thickness: Float = 0.32f,
): Modifier = drawWithCache {
    val stroke = size.height * thickness
    val y = size.height * 0.72f
    val path = Path().apply {
        moveTo(size.width * 0.06f, y + stroke * 0.12f)
        quadraticBezierTo(size.width * 0.5f, y - stroke * 0.25f, size.width * 0.94f, y - stroke * 0.05f)
    }
    val style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val marker = color.copy(alpha = color.alpha * alpha)
    onDrawBehind { drawPath(path, marker, style = style) }
}
