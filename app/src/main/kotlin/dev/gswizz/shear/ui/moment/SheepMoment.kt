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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.ui.SheepGeometry
import dev.gswizz.shear.ui.drawSheep
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.shearFlavor
import kotlin.math.atan2

/**
 * The Sheep: a fully coated sheep at the center, a blade like The Cut's appearing above its rear and descending the
 * icon's slanted cut, the fleece behind the line coming off as it passes, and the shorn sheep standing at the end.
 */
@Composable
fun SheepMoment(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val flavor = shearFlavor()
    val scene = remember { SheepScene() }
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(scene) {
        val start = withFrameNanos { it }
        while (true) {
            elapsed = (withFrameNanos { it } - start) / NANOS_PER_MILLI
            if (scene.finished(elapsed)) break
        }
        onFinished()
    }
    Canvas(modifier = modifier.fillMaxWidth().height(HEIGHT_DP.dp)) {
        val side = MARK_SIZE_DP.dp.toPx()
        val bounds = Rect(Offset((size.width - side) / 2, (size.height - side) / 2), Size(side, side))
        drawSheep(bounds = bounds, flavor = flavor, shear = 1f, depth = scene.depth(elapsed))
        if (scene.bladeShown(elapsed)) {
            // The blade rides the cut line: x follows the slant as it descends, and it leans by the same slope.
            val unit = side / SheepGeometry.UNITS
            val along = scene.blade(elapsed)
            val x = bounds.left + (SheepGeometry.CUT_TOP_X + SheepGeometry.CUT_RUN * along) * unit
            val y = bounds.top + along * side
            val width = BLADE_DP.dp.toPx()
            val length = BLADE_LENGTH_UNITS * unit
            val lean = Math.toDegrees(atan2(SheepGeometry.CUT_RUN, SheepGeometry.UNITS).toDouble()).toFloat()
            rotate(-lean, pivot = Offset(x, y)) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor))),
                    topLeft = Offset(x - width / 2, y - length),
                    size = Size(width, length),
                )
            }
        }
    }
}

private const val NANOS_PER_MILLI = 1_000_000f
private const val HEIGHT_DP = 140
private const val MARK_SIZE_DP = 120
private const val BLADE_DP = 3
private const val BLADE_LENGTH_UNITS = 16f
