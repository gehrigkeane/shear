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

class CutSceneTest {
    // "ab" kept, "cd&" removed, "e" kept: six characters wrapped at four columns, so two lines. The blade waits
    // 200 ms, then crosses all six characters in 600 ms, then the survivors reflow over 350 ms.
    private val plan =
        CutPlan(
            original = "abcd&e",
            final = "abe",
            segments =
                listOf(Segment("ab", removed = false), Segment("cd&", removed = true), Segment("e", removed = false)),
            wholesale = false,
        )
    private val scene = CutScene.of(plan, columns = 4, holdMs = 200, sweepMs = 600, reflowMs = 350)

    @Test
    fun `characters carry their run's fate and wrap into lines`() {
        assertEquals("abcd&e", scene.chars.joinToString("") { it.char.toString() })
        assertEquals(listOf(false, false, true, true, true, false), scene.chars.map { it.removed })
        assertEquals(2, scene.lines)
        assertEquals(0, scene.lineOf(3))
        assertEquals(1, scene.lineOf(4))
        assertEquals(1, scene.columnOf(5))
    }

    @Test
    fun `the blade waits, then crosses every character at a steady pace`() {
        assertEquals(0f, scene.bladeIndex(0f), 0f)
        assertEquals(0f, scene.bladeIndex(200f), 0f)
        assertEquals(3f, scene.bladeIndex(500f), 0.001f)
        assertEquals(0, scene.bladeLine(500f))
        assertEquals(3f, scene.bladeColumn(500f), 0.001f)
        // 700 ms: five characters in, so on the second line, one column along.
        assertEquals(1, scene.bladeLine(700f))
        assertEquals(1f, scene.bladeColumn(700f), 0.001f)
        assertEquals(6f, scene.bladeIndex(800f), 0.001f)
    }

    @Test
    fun `a removed character is cut once the blade has passed it and kept ones never are`() {
        assertFalse(scene.isCut(2, 400f))
        assertTrue(scene.isCut(2, 500f))
        assertFalse(scene.isCut(3, 500f))
        assertTrue(scene.isCut(4, 800f))
        assertFalse(scene.isCut(0, 900f))
        assertFalse(scene.isCut(5, 900f))
    }

    @Test
    fun `after the sweep the survivors reflow into the final URL then the scene is done`() {
        assertEquals(0f, scene.reflow(800f), 0f)
        assertEquals(0.5f, scene.reflow(975f), 0.001f)
        assertEquals(1f, scene.reflow(1150f), 0f)
        assertFalse(scene.finished(1149f))
        assertTrue(scene.finished(1150f))
    }

    @Test
    fun `a wholesale plan is every character removed`() {
        val wholesale =
            CutPlan(
                "https://go.example/r",
                "https://dest.example/",
                listOf(Segment("https://go.example/r", true)),
                true,
            )
        val scene = CutScene.of(wholesale, columns = 10, holdMs = 0, sweepMs = 100, reflowMs = 100)
        assertTrue(scene.chars.all { it.removed })
        assertEquals(2, scene.lines)
    }
}
