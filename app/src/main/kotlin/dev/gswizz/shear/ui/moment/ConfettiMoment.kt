/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.ui.WORDMARK
import dev.gswizz.shear.ui.WordmarkGrid
import dev.gswizz.shear.ui.drawGrid
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.shearFlavor
import kotlin.random.Random

/**
 * Confetti: the wordmark's S pops in at the center and throws a burst of pixel squares in every accent of the flavor,
 * which arc, fall, and fade. Seeded from the share so no two look alike but any one replays exactly.
 */
@Composable
fun ConfettiMoment(summary: MomentSummary, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val flavor = shearFlavor()
    val accents = flavor.accents
    val density = LocalDensity.current.density
    val mark = remember { WordmarkGrid.parse(WORDMARK).crop(MARK_COLUMNS) }
    val scene = remember(summary) { ConfettiScene(Random(summary.hashCode()), accents.size, density) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var center by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scene) {
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            elapsed = (now - start) / NANOS_PER_MILLI
            scene.frame(elapsed, (now - last) / NANOS_PER_SECOND, center.x, center.y)
            last = now
            if (scene.finished(elapsed)) break
        }
        onFinished()
    }
    Canvas(modifier = modifier.fillMaxWidth().height(HEIGHT_DP.dp)) {
        center = Offset(size.width / 2, size.height / 2)
        val cell = Size(MARK_WIDTH_DP.dp.toPx() / mark.columns, MARK_WIDTH_DP.dp.toPx() / mark.columns)
        val markSize = Size(cell.width * mark.columns, cell.height * mark.rows)
        val fade = scene.fade(elapsed)
        for (i in 0 until scene.field.count) {
            drawRect(
                color = accents[scene.field.colorIndex[i]].copy(alpha = scene.field.alpha(i) * fade),
                topLeft = Offset(scene.field.x[i], scene.field.y[i]),
                size = Size(scene.field.size[i], scene.field.size[i]),
            )
        }
        scale(scale = scene.markScale(elapsed), pivot = center) {
            translate(left = center.x - markSize.width / 2, top = center.y - markSize.height / 2) {
                drawGrid(
                    grid = mark,
                    cell = cell,
                    ink =
                        Brush.linearGradient(
                            listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor)),
                            start = Offset.Zero,
                            end = Offset(markSize.width, markSize.height),
                        ),
                    shadow = Brand.shadow(flavor),
                ) {
                    true
                }
            }
        }
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val NANOS_PER_MILLI = 1_000_000f
private const val HEIGHT_DP = 140
private const val MARK_COLUMNS = 8
private const val MARK_WIDTH_DP = 72
