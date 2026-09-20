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

class TypewriterSceneTest {
    private val scene = TypewriterScene(columns = 40, restColumn = 28)

    @Test
    fun `through the hold the whole word is solid and the cursor waits at the right edge`() {
        assertEquals(40, scene.cutColumn(0f))
        assertEquals(40, scene.cutColumn(TypewriterScene.HOLD_MS - 1f))
        assertEquals(39, scene.cursorColumn(0f))
    }

    @Test
    fun `the cut steps left from the edge to rest without ever moving back`() {
        val end = TypewriterScene.HOLD_MS + TypewriterScene.SWEEP_MS
        val columns = (0..20).map { scene.cutColumn(TypewriterScene.HOLD_MS + TypewriterScene.SWEEP_MS * it / 20f) }
        assertEquals(40, columns.first())
        assertEquals(28, columns.last())
        for (i in 1 until columns.size) assertTrue("step $i", columns[i] <= columns[i - 1])
        assertEquals(28, scene.cutColumn(TypewriterScene.DURATION_MS - 1f))
        assertTrue(end < TypewriterScene.DURATION_MS)
    }

    @Test
    fun `the cursor rides the column about to go hollow and leaves once the sweep is over`() {
        val mid = TypewriterScene.HOLD_MS + TypewriterScene.SWEEP_MS / 2f
        assertEquals(scene.cutColumn(mid) - 1, scene.cursorColumn(mid))
        assertTrue(scene.cursorShown(mid))
        assertFalse(scene.cursorShown(TypewriterScene.HOLD_MS + TypewriterScene.SWEEP_MS.toFloat()))
        assertTrue(scene.cursorOn(0f))
        assertFalse(scene.cursorOn(TypewriterScene.BLINK_MS.toFloat()))
    }

    @Test
    fun `finished at the end of the budget`() {
        assertFalse(scene.finished(TypewriterScene.DURATION_MS - 1f))
        assertTrue(scene.finished(TypewriterScene.DURATION_MS.toFloat()))
    }
}
