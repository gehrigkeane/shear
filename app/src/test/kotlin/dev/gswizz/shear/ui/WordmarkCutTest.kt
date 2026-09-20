/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** Invariants of the shear, not its numbers: where the cut rests and how it gets there can change with the art. */
class WordmarkCutTest {
    private val grid = WordmarkGrid.parse(WORDMARK)
    private val ink = grid.cells.filter { it.ink }.map { it.column to it.row }.toSet()

    @Test
    fun `the cut splits one letter instead of falling between two`() {
        val cut = WordmarkCut.REST
        val splitsInk = (0 until grid.rows).any { (cut.column - 1 to it) in ink && (cut.column to it) in ink }
        assertTrue("column ${cut.column} has ink on both sides in some row", splitsInk)
        assertTrue("something stays solid", grid.cells.any { it.ink && !cut.isHollow(it.column) })
        assertTrue("something goes hollow", grid.cells.any { it.ink && cut.isHollow(it.column) })
    }
}
