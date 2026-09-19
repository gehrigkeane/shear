/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WordmarkGridTest {
    @Test
    fun `the wordmark parses to six rows of forty columns`() {
        val grid = WordmarkGrid.parse(WORDMARK)
        assertEquals(6, grid.rows)
        assertEquals(40, grid.columns)
        assertTrue(grid.cells.any { it.ink })
        assertTrue(grid.cells.any { !it.ink })
    }

    @Test
    fun `full blocks are ink and box glyphs are shadow segments`() {
        val grid = WordmarkGrid.parse("█╗\n╚═")
        assertEquals(Cell(0, 0, setOf(Segment.FULL)), grid.cells[0])
        assertEquals(Cell(1, 0, setOf(Segment.LEFT, Segment.DOWN)), grid.cells[1])
        assertEquals(Cell(0, 1, setOf(Segment.UP, Segment.RIGHT)), grid.cells[2])
        assertEquals(Cell(1, 1, setOf(Segment.LEFT, Segment.RIGHT)), grid.cells[3])
        assertTrue(grid.cells[0].ink)
        assertFalse(grid.cells[1].ink)
    }

    @Test
    fun `spaces leave no cell and short rows pad to the widest`() {
        val grid = WordmarkGrid.parse("█\n █")
        assertEquals(2, grid.columns)
        assertEquals(2, grid.rows)
        assertEquals(listOf(Cell(0, 0, setOf(Segment.FULL)), Cell(1, 1, setOf(Segment.FULL))), grid.cells)
    }

    @Test
    fun `cropping keeps the first columns and drops the rest`() {
        val s = WordmarkGrid.parse(WORDMARK).crop(8)
        assertEquals(8, s.columns)
        assertEquals(6, s.rows)
        assertTrue(s.cells.all { it.column < 8 })
        assertEquals(WordmarkGrid.parse(WORDMARK).cells.count { it.column < 8 }, s.cells.size)
    }

    @Test
    fun `an unsupported glyph is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { WordmarkGrid.parse("█x") }
    }
}
