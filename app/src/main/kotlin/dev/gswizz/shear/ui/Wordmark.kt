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
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.gswizz.shear.R
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Motion
import dev.gswizz.shear.ui.theme.shearFlavor
import kotlin.math.ceil

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
 * The shear line across the wordmark: a column boundary, so once the wordmark leans it runs parallel to the stems, and
 * at rest it splits the A between its third and fourth ink columns. Ink left of it is solid; ink right of it is a
 * hollow outline in skin over a faint shadow.
 *
 * The A is the letter just past the golden-ratio point of the word, so the cut reads as an action caught mid-letter
 * rather than a two-tone split between letters. Snapping to whole columns keeps the line on the grid while it moves.
 */
class WordmarkCut(val column: Int) {
    /** Whether a cell in [column] lies right of the line and so is hollow. */
    fun isHollow(column: Int): Boolean = column >= this.column

    companion object {
        /** The column boundary the resting cut sits on, between the A's third and fourth ink columns. */
        const val REST_COLUMN = 28

        /** The cut for [grid] at [shear]: 0 sits just past the last column, 1 is at rest across the A. */
        fun at(grid: WordmarkGrid, shear: Float): WordmarkCut {
            val start = grid.columns.toFloat()
            return WordmarkCut(ceil(start + (REST_COLUMN - start) * shear.coerceIn(0f, 1f)).toInt())
        }
    }
}

/**
 * The wordmark's italic lean as a slope, x run per y rise: the launcher icon's cut, so the two share one diagonal and
 * the shear line down a column boundary is parallel to the stems.
 */
const val WORDMARK_LEAN = SheepGeometry.CUT_RUN / SheepGeometry.UNITS

/** Width over height of the leaning wordmark, the extra being what the lean pushes the top row right by. */
fun wordmarkAspect(grid: WordmarkGrid): Float = (grid.columns + grid.rows * WORDMARK_LEAN) / grid.rows

/** The square cell that fits [grid] into a canvas of [size] shaped by [wordmarkAspect]. */
fun wordmarkCell(grid: WordmarkGrid, size: Size): Size {
    val side = size.height / grid.rows
    return Size(side, side)
}

/**
 * Draws [WORDMARK] to fill the available width, keeping its aspect ratio.
 *
 * Ink runs a horizontal gradient from primary to tertiary and shadow is a translucent outline, so the mark follows the
 * dynamic palette. On first display the cells appear along a diagonal wipe, then the shear line steps in from the right
 * column by column and hollows what it passes, coming to rest across the A; the system's animator scale governs the
 * duration, so users who turn animations off see the mark at once. Semantically it is just the app name.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val grid = remember { WordmarkGrid.parse(WORDMARK) }
    val flavor = shearFlavor()
    val inkColors = listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor))
    val shadow = Brand.shadow(flavor).copy(alpha = SHADOW_ALPHA)
    val reveal = remember { Animatable(0f) }
    val shear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(Motion.REVEAL_MS, easing = Motion.Easing))
        shear.animateTo(1f, tween(Motion.SHEAR_MS, easing = Motion.Easing))
    }
    val name = stringResource(R.string.app_name)
    Canvas(modifier = modifier.aspectRatio(wordmarkAspect(grid)).clearAndSetSemantics { contentDescription = name }) {
        val cell = wordmarkCell(grid, size)
        val limit = reveal.value * (grid.columns + grid.rows)
        italic(grid, cell) {
            drawGrid(
                grid = grid,
                cell = cell,
                ink = Brush.horizontalGradient(inkColors, startX = 0f, endX = grid.columns * cell.width),
                shadow = shadow,
                skin = Brand.skin(flavor),
                cut = WordmarkCut.at(grid, shear.value),
            ) {
                it.column + it.row < limit
            }
        }
    }
}

/**
 * Runs [block] with the wordmark's lean applied: the bottom row stays put and each row above shifts right, so grid
 * coordinates inside the block are upright and [cell]-sized while what is drawn leans.
 */
fun DrawScope.italic(grid: WordmarkGrid, cell: Size, block: DrawScope.() -> Unit) {
    val height = grid.rows * cell.height
    val lean = Matrix()
    lean.values[Matrix.SkewX] = -WORDMARK_LEAN
    withTransform({
        translate(left = height * WORDMARK_LEAN)
        transform(lean)
    }) {
        block()
    }
}

/**
 * Paints the [visible] cells of [grid] at [cell] size from the current origin: solid cells with [ink], box-drawing
 * cells as [shadow] bars, and, right of [cut], solid cells as hollow letters outlined in [skin] over a faint [skin]
 * shadow.
 *
 * One path per group, because adjacent rectangles filled separately leave anti-aliased seams at fractional edges. The
 * hollow outline is every edge between a visible ink cell and anything else, stroked with square caps and clipped to
 * the hollow cells, which leaves exactly the inset outline of each letter with clean corners.
 */
fun DrawScope.drawGrid(
    grid: WordmarkGrid,
    cell: Size,
    ink: Brush,
    shadow: Color,
    skin: Color,
    cut: WordmarkCut,
    visible: (Cell) -> Boolean,
) {
    val inkPath = Path()
    val shadowBars = Path()
    val hollowShadow = Path()
    val hollowCells = Path()
    val shown = BooleanArray(grid.columns * grid.rows)
    for (c in grid.cells) if (visible(c) && c.ink) shown[c.row * grid.columns + c.column] = true
    fun inkAt(column: Int, row: Int): Boolean =
        column in 0 until grid.columns && row in 0 until grid.rows && shown[row * grid.columns + column]
    val edges = Path()
    for (c in grid.cells) {
        if (!visible(c)) continue
        val origin = Offset(c.column * cell.width, c.row * cell.height)
        val hollow = cut.isHollow(c.column)
        when {
            !c.ink && hollow -> hollowShadow.addShadow(c.segments, origin, cell)
            !c.ink -> shadowBars.addShadow(c.segments, origin, cell)
            !hollow -> inkPath.addRect(Rect(origin, cell))
            else -> {
                hollowCells.addRect(Rect(origin, cell))
                val right = origin.x + cell.width
                val bottom = origin.y + cell.height
                if (!inkAt(c.column, c.row - 1)) edges.edge(origin.x, origin.y, right, origin.y)
                if (!inkAt(c.column, c.row + 1)) edges.edge(origin.x, bottom, right, bottom)
                if (!inkAt(c.column - 1, c.row)) edges.edge(origin.x, origin.y, origin.x, bottom)
                if (!inkAt(c.column + 1, c.row)) edges.edge(right, origin.y, right, bottom)
            }
        }
    }
    drawPath(inkPath, ink)
    drawPath(shadowBars, shadow)
    drawPath(hollowShadow, skin.copy(alpha = HOLLOW_SHADOW_ALPHA))
    clipPath(hollowCells) {
        drawPath(edges, skin, style = Stroke(width = cell.minDimension * HOLLOW_WEIGHT, cap = StrokeCap.Square))
    }
}

private fun Path.edge(x1: Float, y1: Float, x2: Float, y2: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
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
/** Hollow outline thickness as a fraction of the cell. */
private const val HOLLOW_WEIGHT = 0.18f
/** How faint the shadow behind hollow letters is. */
private const val HOLLOW_SHADOW_ALPHA = 0.33f
