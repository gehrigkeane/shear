/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelFieldTest {
    private fun burst(random: Random, capacity: Int = 64, count: Int = 20): PixelField =
        PixelField(capacity, random).apply {
            burst(
                originX = 10f,
                originY = 20f,
                count = count,
                speed = 100f,
                colorCount = 3,
                minSize = 2f,
                maxSize = 4f,
                lifeSeconds = 1f,
            )
        }

    @Test
    fun `a burst never exceeds capacity`() {
        val field = burst(Random(1), capacity = 8, count = 20)
        assertEquals(8, field.count)
    }

    @Test
    fun `the same seed produces the same field`() {
        val a = burst(Random(7)).apply { step(0.016f, gravity = 900f) }
        val b = burst(Random(7)).apply { step(0.016f, gravity = 900f) }
        assertEquals(a.count, b.count)
        assertArrayEquals(a.x, b.x, 0f)
        assertArrayEquals(a.y, b.y, 0f)
        assertArrayEquals(a.colorIndex, b.colorIndex)
    }

    @Test
    fun `squares start at the origin with sizes and colors in range`() {
        val field = burst(Random(3))
        for (i in 0 until field.count) {
            assertEquals(10f, field.x[i], 0f)
            assertEquals(20f, field.y[i], 0f)
            assertTrue(field.size[i] in 2f..4f)
            assertTrue(field.colorIndex[i] in 0 until 3)
            assertEquals(1f, field.alpha(i), 0f)
        }
    }

    @Test
    fun `gravity pulls squares down over a step`() {
        val field = burst(Random(5))
        val vyBefore = field.vy.copyOf(field.count)
        field.step(0.5f, gravity = 100f)
        for (i in 0 until field.count) {
            assertEquals(vyBefore[i] + 50f, field.vy[i], 0.001f)
            assertEquals(0.5f, field.alpha(i), 0.001f)
        }
    }

    @Test
    fun `squares die when their life runs out`() {
        val field = burst(Random(9))
        field.step(0.6f, gravity = 0f)
        assertEquals(20, field.count)
        field.step(0.6f, gravity = 0f)
        assertEquals(0, field.count)
    }

    @Test
    fun `single squares can be placed with their own velocity`() {
        val field = PixelField(4, Random(0))
        field.spawn(x = 1f, y = 2f, vx = 3f, vy = 4f, size = 5f, colorIndex = 1, lifeSeconds = 2f)
        assertEquals(1, field.count)
        field.step(1f, gravity = 0f)
        assertEquals(4f, field.x[0], 0f)
        assertEquals(6f, field.y[0], 0f)
        assertEquals(0.5f, field.alpha(0), 0.001f)
    }
}
