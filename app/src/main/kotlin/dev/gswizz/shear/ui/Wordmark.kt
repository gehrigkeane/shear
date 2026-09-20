/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.gswizz.shear.R
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Motion
import dev.gswizz.shear.ui.theme.shearFlavor

/**
 * The SHEAR wordmark as ANSI-Shadow box-drawing art.
 *
 * This string is the single source of the wordmark: [Wordmark] draws it on screen and the moments animate it. Every
 * glyph must have a mapping in [WordmarkGrid.parse].
 */
const val WORDMARK =
    """
███████╗██╗  ██╗███████╗ █████╗ ██████╗
██╔════╝██║  ██║██╔════╝██╔══██╗██╔══██╗
███████╗███████║█████╗  ███████║██████╔╝
╚════██║██╔══██║██╔══╝  ██╔══██║██╔══██╗
███████║██║  ██║███████╗██║  ██║██║  ██║
╚══════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝
"""

/**
 * The strokes a box-drawing glyph puts in its cell; [FULL] is a solid block, the rest are thin bars from the center.
 */
enum class Segment {
    FULL,
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

/** One non-blank glyph of the wordmark. Solid blocks are [ink]; everything else is shadow. */
data class Cell(val column: Int, val row: Int, val segments: Set<Segment>) {
    val ink: Boolean
        get() = Segment.FULL in segments
}

/**
 * Box-drawing art resolved to a grid of [Cell]s so it can be drawn with rectangles instead of a font.
 *
 * Text rendering would need a bundled monospace face with Box Drawing coverage; rectangles are exact at every size and
 * can be painted with any brush. Rows shorter than the widest are treated as padded with spaces.
 */
class WordmarkGrid(val columns: Int, val rows: Int, val cells: List<Cell>) {
    /** The leftmost [columns] columns, for example the first letter. */
    fun crop(columns: Int): WordmarkGrid = WordmarkGrid(columns, rows, cells.filter { it.column < columns })

    companion object {
        private val SHAPES: Map<Char, Set<Segment>> =
            mapOf(
                '█' to setOf(Segment.FULL),
                '═' to setOf(Segment.LEFT, Segment.RIGHT),
                '║' to setOf(Segment.UP, Segment.DOWN),
                '╗' to setOf(Segment.LEFT, Segment.DOWN),
                '╔' to setOf(Segment.RIGHT, Segment.DOWN),
                '╝' to setOf(Segment.LEFT, Segment.UP),
                '╚' to setOf(Segment.RIGHT, Segment.UP),
            )

        /**
         * Parses [art], ignoring leading and trailing blank lines.
         *
         * @throws IllegalArgumentException when a glyph other than a space has no shape.
         */
        fun parse(art: String): WordmarkGrid {
            val lines = art.lines().dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            val cells = lines.flatMapIndexed { row, line ->
                line.mapIndexedNotNull { column, glyph ->
                    if (glyph == ' ') return@mapIndexedNotNull null
                    val segments = requireNotNull(SHAPES[glyph]) { "No shape for '$glyph' at $row:$column" }
                    Cell(column, row, segments)
                }
            }
            return WordmarkGrid(columns = lines.maxOf { it.length }, rows = lines.size, cells = cells)
        }
    }
}

/**
 * Draws [WORDMARK] to fill the available width, keeping its aspect ratio.
 *
 * Ink runs a horizontal gradient from primary to tertiary and shadow is a translucent outline, so the mark follows the
 * dynamic palette. On first display the cells appear along a diagonal wipe, a nod to the blade; the system's animator
 * scale governs the duration, so users who turn animations off see the mark at once. Semantically it is just the app
 * name.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val grid = remember { WordmarkGrid.parse(WORDMARK) }
    val flavor = shearFlavor()
    val inkColors = listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor))
    val shadow = Brand.shadow(flavor).copy(alpha = SHADOW_ALPHA)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(Motion.REVEAL_MS, easing = Motion.Easing)) }
    val name = stringResource(R.string.app_name)
    Canvas(
        modifier =
            modifier.aspectRatio(grid.columns.toFloat() / grid.rows).clearAndSetSemantics {
                contentDescription = name
            }
    ) {
        val cell = Size(size.width / grid.columns, size.height / grid.rows)
        val limit = reveal.value * (grid.columns + grid.rows)
        drawGrid(
            grid = grid,
            cell = cell,
            ink = Brush.horizontalGradient(inkColors, startX = 0f, endX = size.width),
            shadow = shadow,
        ) {
            it.column + it.row < limit
        }
    }
}

/**
 * Paints the [visible] cells of [grid] at [cell] size from the current origin: solid cells with [ink], box-drawing
 * cells as [shadow] bars.
 *
 * One path per group, because adjacent rectangles filled separately leave anti-aliased seams at fractional edges.
 */
fun DrawScope.drawGrid(grid: WordmarkGrid, cell: Size, ink: Brush, shadow: Color, visible: (Cell) -> Boolean) {
    val inkPath = Path()
    val shadowBars = Path()
    for (c in grid.cells) {
        if (!visible(c)) continue
        val origin = Offset(c.column * cell.width, c.row * cell.height)
        if (c.ink) inkPath.addRect(Rect(origin, cell)) else shadowBars.addShadow(c.segments, origin, cell)
    }
    drawPath(inkPath, ink)
    drawPath(shadowBars, shadow)
}

/** Adds thin bars from the cell center to the edges named by [segments]. */
private fun Path.addShadow(segments: Set<Segment>, origin: Offset, cell: Size) {
    val half = cell.minDimension * SHADOW_WEIGHT / 2
    val center = Offset(origin.x + cell.width / 2, origin.y + cell.height / 2)
    for (segment in segments) {
        val (topLeft, size) =
            when (segment) {
                Segment.LEFT -> Offset(origin.x, center.y - half) to Size(center.x + half - origin.x, half * 2)
                Segment.RIGHT ->
                    Offset(center.x - half, center.y - half) to Size(origin.x + cell.width - center.x + half, half * 2)
                Segment.UP -> Offset(center.x - half, origin.y) to Size(half * 2, center.y + half - origin.y)
                Segment.DOWN ->
                    Offset(center.x - half, center.y - half) to Size(half * 2, origin.y + cell.height - center.y + half)
                Segment.FULL -> continue
            }
        addRect(Rect(topLeft, size))
    }
}

private const val SHADOW_ALPHA = 0.55f
/** Shadow bar thickness as a fraction of the cell. */
private const val SHADOW_WEIGHT = 0.3f
