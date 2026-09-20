/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Flavor

/**
 * The sheep of the launcher icon, in the icon's own 108-unit coordinates so the two never drift apart.
 *
 * It faces left. Fleece is a cluster of circles, the shaved body a capsule behind it, and the face sits on a tuft so it
 * is always dark on fleece rather than dark on background. The shear is a slanted line: everything of the fleece on its
 * right is gone.
 */
object SheepGeometry {
    /** The side of the square the sheep is drawn in. */
    const val UNITS = 108f

    /** Fleece circles as center x, center y, radius. */
    val fleece: List<Triple<Float, Float, Float>> =
        listOf(
            Triple(41f, 54f, 11f),
            Triple(53f, 48f, 13f),
            Triple(65f, 54f, 11f),
            Triple(69f, 64f, 9f),
            Triple(37f, 64f, 9f),
            Triple(47f, 68f, 11f),
            Triple(59f, 68f, 11f),
            Triple(40f, 48f, 11f),
            Triple(31f, 44f, 7f),
            Triple(39f, 35f, 8f),
            Triple(47f, 42f, 6f),
            Triple(34f, 54f, 7f),
        )

    val body: Rect = Rect(Offset(33f, 48f), Size(48f, 24f))
    val bodyRadius: CornerRadius = CornerRadius(12f)
    val legs: List<Rect> = listOf(Rect(Offset(43f, 70f), Size(6f, 8f)), Rect(Offset(67f, 70f), Size(6f, 8f)))
    val legRadius: CornerRadius = CornerRadius(2f)
    val head: Rect = Rect(Offset(32f, 41f), Size(13f, 18f))
    val headRadius: CornerRadius = CornerRadius(6f)

    /** Ears as center and rotation; each is an ellipse of [EAR_RX] by [EAR_RY]. */
    val ears: List<Pair<Offset, Float>> = listOf(Offset(32f, 43f) to -25f, Offset(45f, 43f) to 25f)
    const val EAR_RX = 4.5f
    const val EAR_RY = 2.6f
    val eyes: List<Offset> = listOf(Offset(35.77f, 48.56f), Offset(41.23f, 48.56f))
    const val EYE_RADIUS = 1.4f

    /** Where the fleece gradient runs, corner to corner of the fleece. */
    val inkStart: Offset = Offset(24f, 27f)
    val inkEnd: Offset = Offset(72f, 79f)

    /** The shear line at full shear: x at the top edge, and how far right it lands by the bottom edge. */
    const val CUT_TOP_X = 46.78f
    const val CUT_RUN = 24f

    /** How far right of the finished cut the line starts, which is past the rear so nothing is missing. */
    const val CUT_TRAVEL = 40f

    /** The rear of the shaved body, where fleece leaves from. */
    val rear: Offset = Offset(70f, 58f)
}

/**
 * Draws the sheep to fill [bounds], sheared by [shear]: 0 keeps every curl, 1 is the launcher icon's cut, and values
 * between move the shear line from the rear toward the head. [depth] is how far down the sheep the cut has been made, 0
 * for none and 1 for all the way through; fleece behind the line below that point is still on.
 */
fun DrawScope.drawSheep(bounds: Rect, flavor: Flavor, shear: Float = 1f, depth: Float = 1f) {
    val g = SheepGeometry
    val skin = Brand.skin(flavor)
    val face = Brand.face(flavor)
    withTransform({
        translate(bounds.left, bounds.top)
        scale(bounds.width / g.UNITS, bounds.height / g.UNITS, pivot = Offset.Zero)
    }) {
        drawRoundRect(color = skin, topLeft = g.body.topLeft, size = g.body.size, cornerRadius = g.bodyRadius)
        for (leg in g.legs) drawRoundRect(
            color = skin,
            topLeft = leg.topLeft,
            size = leg.size,
            cornerRadius = g.legRadius,
        )
        clipPath(cutPath(shear, depth)) {
            drawPath(
                path = fleecePath(),
                brush =
                    Brush.linearGradient(listOf(Brand.inkStart(flavor), Brand.inkEnd(flavor)), g.inkStart, g.inkEnd),
            )
        }
        for ((center, degrees) in g.ears) {
            rotate(degrees, pivot = center) {
                drawOval(
                    color = face,
                    topLeft = Offset(center.x - g.EAR_RX, center.y - g.EAR_RY),
                    size = Size(g.EAR_RX * 2, g.EAR_RY * 2),
                )
            }
        }
        drawRoundRect(color = face, topLeft = g.head.topLeft, size = g.head.size, cornerRadius = g.headRadius)
        for (eye in g.eyes) drawCircle(color = Brand.inkStart(flavor), radius = g.EYE_RADIUS, center = eye)
    }
}

/** Every fleece circle in one path, so overlaps fill once and adjacent edges leave no seams. */
private fun fleecePath(): Path {
    val path = Path()
    for ((cx, cy, r) in SheepGeometry.fleece) path.addOval(Rect(Offset(cx - r, cy - r), Size(r * 2, r * 2)))
    return path
}

/**
 * The region of fleece that survives [shear] down to [depth]: everything left of a slanted line, plus everything below
 * the point the blade has reached on it.
 */
private fun cutPath(shear: Float, depth: Float): Path {
    val g = SheepGeometry
    val top = g.CUT_TOP_X + (1f - shear.coerceIn(0f, 1f)) * g.CUT_TRAVEL
    val reach = depth.coerceIn(0f, 1f) * g.UNITS
    return Path().apply {
        moveTo(0f, 0f)
        lineTo(top, 0f)
        lineTo(top + g.CUT_RUN * (reach / g.UNITS), reach)
        lineTo(g.UNITS, reach)
        lineTo(g.UNITS, g.UNITS)
        lineTo(0f, g.UNITS)
        close()
    }
}
