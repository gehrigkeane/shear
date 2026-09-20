/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Flavor
import kotlin.math.cos
import kotlin.math.sin

/**
 * The scissors of the direct-share shortcut, in the same 108-unit square as the sheep.
 *
 * Two halves mirror about the pivot: an ink blade above it and a skin ring below, the blades opening less than the
 * handles as real scissors do. The whole implement leans right, blades to the upper right and rings to the lower left,
 * so the bottom-right corner, where the sharesheet badges the entry with the app icon, holds only background.
 */
object ScissorsGeometry {
    /** The side of the square the scissors are drawn in. */
    const val UNITS = 108f

    val pivot: Offset = Offset(54f, 54f)
    const val PIVOT_RADIUS = 2.8f

    /** Clockwise lean of the whole implement, in degrees. */
    const val TILT = 40f

    /** Half the opening angle of the blades and of the handles, in degrees. */
    const val BLADE_SPREAD = 15f
    const val HANDLE_SPREAD = 34f

    const val BLADE_LENGTH = 33f
    const val BLADE_WIDTH = 7.5f
    const val BLADE_TIP = 1.6f

    /** The shank from the pivot to the ring, then the ring's radii and stroke. */
    const val SHANK = 9f
    const val RING_RX = 7f
    const val RING_RY = 9.5f
    const val RING_STROKE = 4.4f

    /** Where the blade tips land once everything is rotated. */
    val tips: List<Offset> =
        listOf(1f, -1f).map { sign -> pivot + Offset(0f, -BLADE_LENGTH).rotated(TILT + sign * BLADE_SPREAD) }

    /** Where the ring centers land once everything is rotated; the rings are hollow there. */
    val ringCenters: List<Offset> =
        listOf(1f, -1f).map { sign -> pivot + Offset(0f, SHANK + RING_RY).rotated(TILT - sign * HANDLE_SPREAD) }

    private fun Offset.rotated(degrees: Float): Offset {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return Offset(x * c - y * s, x * s + y * c)
    }
}

/** Draws the scissors to fill [bounds]. */
fun DrawScope.drawScissors(bounds: Rect, flavor: Flavor) {
    val g = ScissorsGeometry
    val skin = Brand.skin(flavor)
    val ink =
        Brush.linearGradient(
            listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor)),
            Offset.Zero,
            Offset(0f, -g.BLADE_LENGTH),
        )
    withTransform({
        translate(bounds.left, bounds.top)
        scale(bounds.width / g.UNITS, bounds.height / g.UNITS, pivot = Offset.Zero)
        translate(g.pivot.x, g.pivot.y)
        rotate(g.TILT, pivot = Offset.Zero)
    }) {
        for (sign in listOf(1f, -1f)) {
            rotate(sign * g.BLADE_SPREAD, pivot = Offset.Zero) { drawPath(bladePath(), brush = ink) }
            rotate(-sign * g.HANDLE_SPREAD, pivot = Offset.Zero) {
                drawLine(color = skin, start = Offset.Zero, end = Offset(0f, g.SHANK), strokeWidth = g.RING_STROKE)
                drawOval(
                    color = skin,
                    topLeft = Offset(-g.RING_RX, g.SHANK),
                    size = Size(g.RING_RX * 2, g.RING_RY * 2),
                    style = Stroke(g.RING_STROKE),
                )
            }
        }
        drawCircle(color = Brand.face(flavor), radius = g.PIVOT_RADIUS, center = Offset.Zero)
    }
}

/** One blade from the pivot straight up: a taper to a rounded tip. */
private fun bladePath(): Path {
    val g = ScissorsGeometry
    return Path().apply {
        moveTo(-g.BLADE_WIDTH / 2, 0f)
        lineTo(-g.BLADE_TIP / 2, -g.BLADE_LENGTH)
        quadraticTo(0f, -g.BLADE_LENGTH - 3f, g.BLADE_TIP / 2, -g.BLADE_LENGTH)
        lineTo(g.BLADE_WIDTH / 2, 0f)
        close()
    }
}
