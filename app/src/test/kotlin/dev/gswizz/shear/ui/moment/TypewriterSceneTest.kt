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
    private val scene = TypewriterScene(columns = 40, captionLength = 10)

    @Test
    fun `columns appear left to right over the typing stretch`() {
        assertEquals(0, scene.visibleColumns(0f))
        assertEquals(20, scene.visibleColumns(TypewriterScene.TYPE_MS / 2f))
        assertEquals(40, scene.visibleColumns(TypewriterScene.TYPE_MS.toFloat()))
        assertEquals(40, scene.visibleColumns(TypewriterScene.DURATION_MS - 1f))
    }

    @Test
    fun `the cursor rides the reveal edge and stops at the last column`() {
        assertEquals(0, scene.cursorColumn(0f))
        assertEquals(20, scene.cursorColumn(TypewriterScene.TYPE_MS / 2f))
        assertEquals(39, scene.cursorColumn(TypewriterScene.TYPE_MS.toFloat()))
        assertTrue(scene.cursorOn(0f))
        assertFalse(scene.cursorOn(TypewriterScene.BLINK_MS.toFloat()))
        assertTrue(scene.cursorOn(TypewriterScene.BLINK_MS * 2f))
    }

    @Test
    fun `the caption types in only after the wordmark is complete`() {
        assertEquals(0, scene.captionChars(TypewriterScene.TYPE_MS - 1f))
        assertEquals(5, scene.captionChars(TypewriterScene.TYPE_MS + TypewriterScene.CAPTION_MS / 2f))
        assertEquals(10, scene.captionChars(TypewriterScene.TYPE_MS + TypewriterScene.CAPTION_MS.toFloat()))
        // Then the finished wordmark and caption hold until the budget runs out.
        assertEquals(10, scene.captionChars(TypewriterScene.DURATION_MS.toFloat()))
        assertTrue(TypewriterScene.TYPE_MS + TypewriterScene.CAPTION_MS < TypewriterScene.DURATION_MS)
    }

    @Test
    fun `finished at the end of the budget`() {
        assertFalse(scene.finished(TypewriterScene.DURATION_MS - 1f))
        assertTrue(scene.finished(TypewriterScene.DURATION_MS.toFloat()))
    }
}
