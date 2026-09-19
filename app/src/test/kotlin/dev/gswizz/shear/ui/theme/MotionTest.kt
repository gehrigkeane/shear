/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    /** Long enough to watch, short enough not to feel like a delay before the sharesheet. */
    private val observable = 1_400..2_000

    @Test
    fun `every share moment fits the observable window`() {
        for (budget in listOf(Motion.CUT_MS, Motion.CONFETTI_MS, Motion.TYPEWRITER_MS)) {
            assertTrue("$budget ms", budget in observable)
        }
    }

    @Test
    fun `the cut lets the eye land on the URL before the blade moves`() {
        assertTrue(Motion.CUT_HOLD_MS >= 200)
        assertTrue(
            Motion.CUT_HOLD_MS + Motion.CUT_SWEEP_MS + Motion.CUT_SLIDE_MS + Motion.CUT_SETTLE_MS <= Motion.CUT_MS
        )
    }
}
