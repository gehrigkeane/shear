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
    // "ab" | "cd&" | "e": a kept run, a removed run, a kept run; 20 + 30 + 10 wide, blade sweeps 60 in 600 ms.
    private val scene =
        CutScene(
            segments =
                listOf(Segment("ab", removed = false), Segment("cd&", removed = true), Segment("e", removed = false)),
            widths = listOf(20f, 30f, 10f),
            sweepMs = 600,
            slideMs = 100,
        )

    @Test
    fun `at rest nothing is cut and every run sits at its natural position`() {
        assertEquals(0f, scene.bladeX(0f), 0f)
        assertEquals(0f, scene.cutFraction(1, 0f), 0f)
        assertEquals(0f, scene.x(0, 0f), 0f)
        assertEquals(20f, scene.x(1, 0f), 0f)
        assertEquals(50f, scene.x(2, 0f), 0f)
        assertFalse(scene.finished(0f))
    }

    @Test
    fun `the blade cuts a removed run progressively and kept runs never cut`() {
        // 300 ms: blade at 30, ten pixels into the removed run.
        assertEquals(30f, scene.bladeX(300f), 0.001f)
        assertEquals(1f / 3f, scene.cutFraction(1, 300f), 0.001f)
        assertEquals(0f, scene.cutFraction(0, 300f), 0f)
        assertEquals(0f, scene.cutFraction(2, 300f), 0f)
        // The run after it has not started sliding: the blade has not cleared the removed run yet.
        assertEquals(50f, scene.x(2, 300f), 0.001f)
    }

    @Test
    fun `once the blade clears a removed run the runs after it slide left to close the gap`() {
        // Blade clears x=50 at 500 ms; the slide takes 100 ms.
        assertEquals(0f, scene.slide(1, 500f), 0.001f)
        assertEquals(0.5f, scene.slide(1, 550f), 0.001f)
        assertEquals(35f, scene.x(2, 550f), 0.001f)
        assertEquals(1f, scene.slide(1, 600f), 0.001f)
        assertEquals(20f, scene.x(2, 600f), 0.001f)
    }

    @Test
    fun `finished once the sweep and the last slide are done`() {
        assertFalse(scene.finished(650f))
        assertTrue(scene.finished(700f))
        assertEquals(1f, scene.cutFraction(1, 700f), 0f)
    }
}
