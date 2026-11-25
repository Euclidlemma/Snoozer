package com.example.snoozer

import androidx.compose.ui.geometry.Size
import com.example.snoozer.ui.doodle.sketchyOutlinePoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SketchyOutlineTest {

    private val size = Size(300f, 120f)

    @Test
    fun sameSeed_givesSameOutline() {
        // Stable wobble: recomposition must not make borders shimmer.
        val a = sketchyOutlinePoints(size, 30f, 3f, 50f, seed = 42)
        val b = sketchyOutlinePoints(size, 30f, 3f, 50f, seed = 42)
        assertEquals(a, b)
    }

    @Test
    fun differentSeeds_giveDifferentOutlines() {
        val a = sketchyOutlinePoints(size, 30f, 3f, 50f, seed = 1)
        val b = sketchyOutlinePoints(size, 30f, 3f, 50f, seed = 2)
        assertNotEquals(a, b)
    }

    @Test
    fun points_stayWithinWobbleOfTheBox() {
        val wobble = 3f
        val points = sketchyOutlinePoints(size, 30f, wobble, 50f, seed = 7)
        assertTrue(points.size >= 12)
        points.forEach {
            assertTrue("x=${it.x}", it.x >= -wobble && it.x <= size.width + wobble)
            assertTrue("y=${it.y}", it.y >= -wobble && it.y <= size.height + wobble)
        }
    }

    @Test
    fun zeroWobble_isAPlainRoundedRectangle() {
        val points = sketchyOutlinePoints(size, 30f, 0f, 50f, seed = 9)
        // First point is where the top edge starts, right after the top-left corner.
        assertEquals(30f, points.first().x, 0.001f)
        assertEquals(0f, points.first().y, 0.001f)
    }

    @Test
    fun hugeCornerRadius_isClampedAndEmptySizeHasNoPoints() {
        val points = sketchyOutlinePoints(Size(100f, 40f), 500f, 0f, 20f, seed = 0)
        points.forEach { assertTrue(it.y in -0.001f..40.001f) }
        assertTrue(sketchyOutlinePoints(Size(0f, 40f), 10f, 1f, 20f, seed = 0).isEmpty())
    }
}
