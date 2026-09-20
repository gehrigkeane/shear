/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import dev.gswizz.shear.ui.theme.Motion
import kotlin.math.ceil

/**
 * The timeline of Typewriter, drawing aside: the whole wordmark stands solid for a hold, then a cursor enters from the
 * right edge and steps left column by column, hollowing every column it passes, until it rests where the header's cut
 * sits. Snapping to whole columns keeps the line on the grid while it moves.
 */
class TypewriterScene(private val columns: Int, private val restColumn: Int) {
    /** The first hollow column at [tMs]: [columns] while nothing is hollow, [restColumn] once the sweep is done. */
    fun cutColumn(tMs: Float): Int {
        val progress = Motion.Easing.transform(((tMs - HOLD_MS) / SWEEP_MS).coerceIn(0f, 1f))
        return ceil(columns + (restColumn - columns) * progress).toInt()
    }

    /** The column the cursor block sits on: the solid column about to go hollow. */
    fun cursorColumn(tMs: Float): Int = (cutColumn(tMs) - 1).coerceAtLeast(0)

    /** Whether the cursor is on screen at all: only while there is sweeping left to do. */
    fun cursorShown(tMs: Float): Boolean = tMs < HOLD_MS + SWEEP_MS

    /** Whether the cursor is lit in this blink phase. */
    fun cursorOn(tMs: Float): Boolean = (tMs / BLINK_MS).toInt() % 2 == 0

    fun finished(tMs: Float): Boolean = tMs >= DURATION_MS

    companion object {
        const val DURATION_MS = Motion.TYPEWRITER_MS
        const val HOLD_MS = 300
        const val SWEEP_MS = 900
        const val BLINK_MS = 120
    }
}
