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
        scene.frame(tMs = ConfettiScene.POP_MS - 50f, dtSeconds = 0.016f, originX = 50f, originY = 50f)
        assertEquals(0, scene.field.count)
        scene.frame(tMs = ConfettiScene.POP_MS + 10f, dtSeconds = 0.016f, originX = 50f, originY = 50f)
        assertEquals(ConfettiScene.SQUARES, scene.field.count)
        scene.frame(tMs = ConfettiScene.POP_MS + 30f, dtSeconds = 0.016f, originX = 50f, originY = 50f)
        assertEquals(ConfettiScene.SQUARES, scene.field.count)
        for (i in 0 until scene.field.count) assertTrue(scene.field.colorIndex[i] in 0 until 14)
    }

    @Test
    fun `squares outlive the fade so none vanish early`() {
        val scene = scene()
        scene.frame(tMs = ConfettiScene.POP_MS + 10f, dtSeconds = 0.016f, originX = 50f, originY = 50f)
        // Step to the end of the budget in frames; every square should still be alive when the fade completes.
        var t = ConfettiScene.POP_MS + 10f
        while (t < ConfettiScene.DURATION_MS) {
            scene.frame(tMs = t, dtSeconds = 0.016f, originX = 50f, originY = 50f)
            t += 16f
        }
        assertEquals(ConfettiScene.SQUARES, scene.field.count)
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
        assertFalse(scene.finished(ConfettiScene.DURATION_MS - 1f))
        assertTrue(scene.finished(ConfettiScene.DURATION_MS.toFloat()))
    }

    @Test
    fun `the fleece stays on until the pop lands, then shears off with the burst`() {
        val scene = scene()
        assertEquals(0f, scene.shear(0f), 0.001f)
        assertEquals(0f, scene.shear(ConfettiScene.POP_MS.toFloat()), 0.001f)
        val mid = scene.shear(ConfettiScene.POP_MS + ConfettiScene.SHEAR_MS / 2f)
        assertTrue("mid-shear $mid", mid > 0f && mid < 1f)
        assertEquals(1f, scene.shear((ConfettiScene.POP_MS + ConfettiScene.SHEAR_MS).toFloat()), 0.001f)
        assertEquals(1f, scene.shear(ConfettiScene.DURATION_MS.toFloat()), 0.001f)
    }
}
