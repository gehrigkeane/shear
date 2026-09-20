/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invariants of the shear, not its numbers: where the cut rests and how it gets there can change with the art. */
class WordmarkCutTest {
    private val grid = WordmarkGrid.parse(WORDMARK)
    private val ink = grid.cells.filter { it.ink }.map { it.column to it.row }.toSet()

    @Test
    fun `before the shear nothing is hollow`() {
        val cut = WordmarkCut.at(grid, shear = 0f)
        assertFalse(grid.cells.any { cut.isHollow(it.column) })
    }

    @Test
    fun `the cut steps toward rest without ever moving back`() {
        val columns = (0..20).map { WordmarkCut.at(grid, shear = it / 20f).column }
        assertEquals(WordmarkCut.at(grid, shear = 1f).column, columns.last())
        for (i in 1 until columns.size) assertTrue("step $i", columns[i] <= columns[i - 1])
    }

    @Test
    fun `at rest the cut splits one letter instead of falling between two`() {
        val cut = WordmarkCut.at(grid, shear = 1f)
        val splitsInk = (0 until grid.rows).any { (cut.column - 1 to it) in ink && (cut.column to it) in ink }
        assertTrue("column ${cut.column} has ink on both sides in some row", splitsInk)
        assertTrue("something stays solid", grid.cells.any { it.ink && !cut.isHollow(it.column) })
        assertTrue("something goes hollow", grid.cells.any { it.ink && cut.isHollow(it.column) })
    }
}
