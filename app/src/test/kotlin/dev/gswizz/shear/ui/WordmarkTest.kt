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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.ui.theme.ShearTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WordmarkTest {
    @get:Rule val compose = createComposeRule()

    private val grid = WordmarkGrid.parse(WORDMARK)
    private val ink = grid.cells.filter { it.ink }.map { it.column to it.row }.toSet()

    /** The pixel under grid point ([column], [row]) once the wordmark leans; rows above the bottom shift right. */
    private fun PixelMap.at(column: Float, row: Float): Color {
        val cell = height / grid.rows.toFloat()
        val x = (column * cell + (grid.rows - row) * cell * WORDMARK_LEAN).toInt()
        return this[x, (row * cell).toInt()]
    }

    private fun render(width: Dp): PixelMap {
        compose.setContent { ShearTheme { Wordmark(modifier = Modifier.width(width)) } }
        compose.mainClock.advanceTimeBy(3_000)
        return compose.onNodeWithContentDescription("Shear").captureToImage().toPixelMap()
    }

    @Test
    fun `adjacent ink cells fill without seams at fractional cell sizes`() {
        // 300dp over forty-odd columns lands on a fractional cell size on Robolectric's mdpi screen, so cell edges
        // split pixels, which is where per-rectangle anti-aliasing shows seams.
        val pixels = render(300.dp)
        // The first letter's top row is a run of solid cells from column 0; sample along its vertical middle.
        val run = generateSequence(0) { it + 1 }.takeWhile { (it to 0) in ink }.count()
        assertTrue("first letter has a top bar", run >= 3)
        var previous = pixels.at(0.5f, 0.5f)
        for (step in 0..(run - 1) * 10) {
            val pixel = pixels.at(0.5f + step * 0.1f, 0.5f)
            assertEquals("alpha at step $step", 1f, pixel.alpha, 0.01f)
            val jump =
                abs(pixel.red - previous.red) + abs(pixel.green - previous.green) + abs(pixel.blue - previous.blue)
            assertTrue("seam at step $step (jump $jump)", jump < 0.05f)
            previous = pixel
        }
    }

    @Test
    fun `at rest the letters right of the cut are hollow outlines in skin over what lies behind`() {
        val pixels = render(400.dp)
        val cut = WordmarkCut.at(grid, shear = 1f)
        val splitRow = (0 until grid.rows).first { (cut.column - 1 to it) in ink && (cut.column to it) in ink }
        val topCell = grid.cells.first { it.ink && cut.isHollow(it.column) && (it.column to it.row - 1) !in ink }
        val rightCell = grid.cells.first { it.ink && cut.isHollow(it.column) && (it.column + 1 to it.row) !in ink }
        val occupied = grid.cells.map { it.column to it.row }.toSet()
        val gap = (0 until grid.columns).flatMap { c -> (0 until grid.rows).map { c to it } }.first { it !in occupied }

        val behind = pixels.at(gap.first + 0.5f, gap.second + 0.5f)
        val solid = pixels.at(cut.column - 0.5f, splitRow + 0.5f)
        val hollow = pixels.at(cut.column + 0.5f, splitRow + 0.5f)
        val center = pixels.at(topCell.column + 0.5f, topCell.row + 0.5f)
        val topEdge = pixels.at(topCell.column + 0.5f, topCell.row + 0.08f)
        val rightEdge = pixels.at(rightCell.column + 0.92f, rightCell.row + 0.5f)

        assertTrue("left of the cut is ink, got $solid", solid.alpha > 0.99f && solid.red > solid.blue)
        assertEquals("right of the cut shows what is behind", behind, hollow)
        assertEquals("a hollow cell's middle shows what is behind", behind, center)
        assertTrue("its outer top edge is skin, got $topEdge", topEdge.alpha > 0.99f && topEdge.blue > topEdge.red)
        assertTrue("its outer right edge is skin, got $rightEdge", rightEdge.blue > rightEdge.red)
    }
}
