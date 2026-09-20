/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import dev.gswizz.shear.ui.WORDMARK
import dev.gswizz.shear.ui.WordmarkCut
import dev.gswizz.shear.ui.WordmarkGrid
import dev.gswizz.shear.ui.drawGrid
import dev.gswizz.shear.ui.italic
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.shearFlavor
import dev.gswizz.shear.ui.wordmarkAspect
import dev.gswizz.shear.ui.wordmarkCell

/**
 * Typewriter: the wordmark stands whole, then a cursor block enters from the right and steps left, hollowing the
 * letters it passes until it rests where the header's cut sits. Nothing is typed beneath it.
 */
@Composable
fun TypewriterMoment(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val flavor = shearFlavor()
    val grid = remember { WordmarkGrid.parse(WORDMARK) }
    val scene = remember { TypewriterScene(grid.columns, WordmarkCut.REST.column) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(scene) {
        val start = withFrameNanos { it }
        while (true) {
            elapsed = (withFrameNanos { it } - start) / NANOS_PER_MILLI
            if (scene.finished(elapsed)) break
        }
        onFinished()
    }
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(wordmarkAspect(grid))) {
        val cell = wordmarkCell(grid, size)
        italic(grid, cell) {
            drawGrid(
                grid = grid,
                cell = cell,
                ink =
                    Brush.horizontalGradient(
                        listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor)),
                        0f,
                        grid.columns * cell.width,
                    ),
                shadow = Brand.shadow(flavor),
                skin = Brand.skin(flavor),
                cut = WordmarkCut(scene.cutColumn(elapsed)),
            )
            if (scene.cursorShown(elapsed) && scene.cursorOn(elapsed)) {
                drawRect(
                    color = Brand.inkEnd(flavor),
                    topLeft = Offset(scene.cursorColumn(elapsed) * cell.width, 0f),
                    size = Size(cell.width, grid.rows * cell.height),
                )
            }
        }
    }
}

private const val NANOS_PER_MILLI = 1_000_000f
