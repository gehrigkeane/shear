/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Draws [block] into an offscreen bitmap of [width] by [height] pixels at mdpi and reads it back.
 *
 * Art is tested here rather than through a composed screen: a window capture on Robolectric can hand back the bare
 * window when another class captured before it, and the drawing functions do not need a composition to be exercised.
 */
fun raster(width: Int, height: Int, block: DrawScope.() -> Unit): PixelMap {
    val image = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), size, block)
    return image.toPixelMap()
}
