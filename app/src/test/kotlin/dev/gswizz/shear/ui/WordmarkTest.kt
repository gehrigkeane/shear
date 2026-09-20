/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Catppuccin
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WordmarkTest {
    private val grid = WordmarkGrid.parse(WORDMARK)
    private val ink = grid.cells.filter { it.ink }.map { it.column to it.row }.toSet()
    private val flavor = Catppuccin.Mocha

    /** The pixel under grid point ([column], [row]) once the wordmark leans; rows above the bottom shift right. */
    private fun PixelMap.at(column: Float, row: Float): Color {
        val cell = height / grid.rows.toFloat()
        val x = (column * cell + (grid.rows - row) * cell * WORDMARK_LEAN).toInt()
        return this[x, (row * cell).toInt()]
    }

    /** The wordmark as [Wordmark] draws it, [width] pixels wide at its own aspect ratio, over the flavor's base. */
    private fun render(width: Int): PixelMap =
        raster(width, (width / wordmarkAspect(grid)).roundToInt()) {
            drawRect(flavor.base)
            val cell = wordmarkCell(grid, size)
            italic(grid, cell) {
                drawGrid(
                    grid = grid,
                    cell = cell,
                    ink =
                        Brush.horizontalGradient(
                            listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor)),
                            startX = 0f,
                            endX = grid.columns * cell.width,
                        ),
                    shadow = Brand.shadow(flavor).copy(alpha = 0.5f),
                    skin = Brand.skin(flavor),
                    cut = WordmarkCut.REST,
                )
            }
        }

    @Test
    fun `adjacent ink cells fill without seams at fractional cell sizes`() {
        // 300 pixels over forty-odd columns lands on a fractional cell size, so cell edges split pixels, which is where
        // per-rectangle anti-aliasing shows seams.
        val pixels = render(300)
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
    fun `right of the cut the letters are hollow outlines in skin over what lies behind`() {
        val pixels = render(400)
        val cut = WordmarkCut.REST
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
