/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The header leaves and returns the way it does behind a detail screen: hosted in a saveable state holder, disposed
 * while away, restored on the way back. The clock is paused across the return so a replay would still read 0.
 */
@RunWith(RobolectricTestRunner::class)
class WordmarkOnceTest {
    @get:Rule val compose = createComposeRule()

    private var shown by mutableStateOf(true)
    private var shear = -1f
    private var compositions = 0

    private fun host() {
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            if (shown) {
                holder.SaveableStateProvider("history") {
                    compositions++
                    shear = rememberWordmarkShear()
                }
            }
        }
    }

    /** Leaves and comes back under a paused clock, a few frames each way, well inside the hold. */
    private fun leaveAndReturn() {
        compose.mainClock.autoAdvance = false
        shown = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        shear = -1f
        val before = compositions
        shown = true
        compose.waitForIdle()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("the return recomposed the header", compositions > before)
    }

    @Test
    fun `the shear plays once, so the header comes back at rest instead of replaying`() {
        host()
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(1f, shear)

        leaveAndReturn()
        assertEquals(1f, shear)
    }

    @Test
    fun `leaving mid-hold counts as played, so coming back does not restart it`() {
        compose.mainClock.autoAdvance = false
        host()
        compose.mainClock.advanceTimeBy(100)
        assertEquals("still holding before the cut", 0f, shear)

        leaveAndReturn()
        assertEquals(1f, shear)
    }
}
