/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.ui.theme.ShearTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The wordmark's very first frame, in a class of its own: a paused-clock capture that follows another capture in the
 * same class reads back a blank surface on Robolectric, so this one must go first and alone.
 */
@RunWith(RobolectricTestRunner::class)
class WordmarkFirstFrameTest {
    @get:Rule val compose = createComposeRule()

    private val grid = WordmarkGrid.parse(WORDMARK)
    private val ink = grid.cells.filter { it.ink }.map { it.column to it.row }.toSet()

    /** The pixel under grid point ([column], [row]) once the wordmark leans; rows above the bottom shift right. */
    private fun PixelMap.at(column: Float, row: Float): Color {
        val cell = height / grid.rows.toFloat()
        val x = (column * cell + (grid.rows - row) * cell * WORDMARK_LEAN).toInt()
        return this[x, (row * cell).toInt()]
    }

    @Test
    fun `on the first frame the whole word is solid ink and the cut has not moved`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ShearTheme { Wordmark(modifier = Modifier.width(400.dp)) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        val pixels = compose.onNodeWithContentDescription("Shear").captureToImage().toPixelMap()
        val first = ink.minBy { (column, row) -> column * grid.rows + row }
        val last = ink.maxBy { (column, row) -> column * grid.rows + row }
        for ((name, cell) in listOf("first" to first, "last" to last)) {
            val pixel = pixels.at(cell.first + 0.5f, cell.second + 0.5f)
            assertTrue(
                "$name ink cell is solid ink from the start, got $pixel",
                pixel.alpha > 0.99f && pixel.red > pixel.blue,
            )
        }
    }
}
