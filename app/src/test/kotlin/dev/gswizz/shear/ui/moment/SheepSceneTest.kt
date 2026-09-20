/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheepSceneTest {
    private val scene = SheepScene()
    private val descendEnd = SheepScene.HOLD_MS + SheepScene.DESCEND_MS

    @Test
    fun `through the hold the sheep keeps every curl and the blade waits above it`() {
        assertEquals(0f, scene.depth(0f), 0f)
        assertEquals(0f, scene.depth(SheepScene.HOLD_MS - 1f), 0f)
        assertTrue("blade starts above the sheep", scene.blade(0f) < 0f)
        assertTrue(scene.bladeShown(0f))
    }

    @Test
    fun `the blade descends the cut without ever rising and the fleece comes off as far as it has gone`() {
        val steps = (0..20).map { scene.blade(SheepScene.HOLD_MS + SheepScene.DESCEND_MS * it / 20f) }
        for (i in 1 until steps.size) assertTrue("step $i", steps[i] >= steps[i - 1])
        assertEquals(1f, steps.last(), 0.001f)
        val mid = SheepScene.HOLD_MS + SheepScene.DESCEND_MS / 2f
        assertEquals(scene.blade(mid).coerceIn(0f, 1f), scene.depth(mid), 0f)
        assertEquals(1f, scene.depth(SheepScene.DURATION_MS - 1f), 0f)
    }

    @Test
    fun `the blade leaves once the cut is through and the scene ends on the budget`() {
        assertTrue(scene.bladeShown(descendEnd - 1f))
        assertFalse(scene.bladeShown(descendEnd.toFloat()))
        assertTrue(descendEnd < SheepScene.DURATION_MS)
        assertFalse(scene.finished(SheepScene.DURATION_MS - 1f))
        assertTrue(scene.finished(SheepScene.DURATION_MS.toFloat()))
    }
}
