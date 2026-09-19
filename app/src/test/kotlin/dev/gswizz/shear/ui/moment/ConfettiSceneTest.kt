/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfettiSceneTest {
    private fun scene() = ConfettiScene(random = Random(42), colorCount = 14, density = 2f)

    @Test
    fun `the mark pops from eighty percent to full size then holds`() {
        val scene = scene()
        assertEquals(0.8f, scene.markScale(0f), 0.001f)
        assertTrue(scene.markScale(75f) > 0.8f && scene.markScale(75f) < 1f)
        assertEquals(1f, scene.markScale(ConfettiScene.POP_MS.toFloat()), 0.001f)
        assertEquals(1f, scene.markScale(700f), 0.001f)
    }

    @Test
    fun `the burst fires once when the pop lands`() {
        val scene = scene()
        scene.frame(tMs = 100f, dtSeconds = 0.016f, originX = 50f, originY = 50f)
        assertEquals(0, scene.field.count)
        scene.frame(tMs = 160f, dtSeconds = 0.016f, originX = 50f, originY = 50f)
        assertEquals(ConfettiScene.SQUARES, scene.field.count)
        scene.frame(tMs = 180f, dtSeconds = 0.016f, originX = 50f, originY = 50f)
        assertEquals(ConfettiScene.SQUARES, scene.field.count)
        for (i in 0 until scene.field.count) assertTrue(scene.field.colorIndex[i] in 0 until 14)
    }

    @Test
    fun `squares fade out over the final stretch`() {
        val scene = scene()
        assertEquals(1f, scene.fade(400f), 0.001f)
        assertEquals(0.5f, scene.fade(ConfettiScene.DURATION_MS - ConfettiScene.FADE_MS / 2f), 0.001f)
        assertEquals(0f, scene.fade(ConfettiScene.DURATION_MS.toFloat()), 0.001f)
    }

    @Test
    fun `finished at the end of the budget`() {
        val scene = scene()
        assertFalse(scene.finished(799f))
        assertTrue(scene.finished(800f))
    }
}
