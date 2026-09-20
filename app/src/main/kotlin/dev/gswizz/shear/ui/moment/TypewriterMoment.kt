/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.ui.WORDMARK
import dev.gswizz.shear.ui.WordmarkCut
import dev.gswizz.shear.ui.WordmarkGrid
import dev.gswizz.shear.ui.drawGrid
import dev.gswizz.shear.ui.italic
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Spacing
import dev.gswizz.shear.ui.theme.shearFlavor
import dev.gswizz.shear.ui.wordmarkAspect
import dev.gswizz.shear.ui.wordmarkCell

/**
 * Typewriter: the wordmark's columns appear left to right behind a blinking cursor block, then [caption] types in
 * beneath it in monospace. The caption is exposed whole to accessibility services from the first frame.
 */
@Composable
fun TypewriterMoment(caption: String, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val flavor = shearFlavor()
    val grid = remember { WordmarkGrid.parse(WORDMARK) }
    val cut = WordmarkCut.REST
    val scene = remember(caption) { TypewriterScene(grid.columns, caption.length) }
    val measurer = rememberTextMeasurer()
    val captionStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(scene) {
        val start = withFrameNanos { it }
        while (true) {
            elapsed = (withFrameNanos { it } - start) / NANOS_PER_MILLI
            if (scene.finished(elapsed)) break
        }
        onFinished()
    }
    Column(modifier = modifier.fillMaxWidth().semantics { contentDescription = caption }) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(wordmarkAspect(grid))) {
            val cell = wordmarkCell(grid, size)
            val shown = scene.visibleColumns(elapsed)
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
                    cut = cut,
                ) {
                    it.column < shown
                }
                if (shown < grid.columns && scene.cursorOn(elapsed)) {
                    drawRect(
                        color = Brand.inkEnd(flavor),
                        topLeft = Offset(scene.cursorColumn(elapsed) * cell.width, 0f),
                        size = Size(cell.width, grid.rows * cell.height),
                    )
                }
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(CAPTION_HEIGHT_DP.dp)) {
            val typed = caption.take(scene.captionChars(elapsed))
            val layout = measurer.measure(typed, captionStyle)
            drawText(
                layout,
                color = flavor.subtext0,
                topLeft = Offset((size.width - layout.size.width) / 2, Spacing.m.toPx()),
            )
        }
    }
}

private const val NANOS_PER_MILLI = 1_000_000f
private const val CAPTION_HEIGHT_DP = 40
