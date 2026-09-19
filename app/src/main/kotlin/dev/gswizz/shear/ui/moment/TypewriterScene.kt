/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import dev.gswizz.shear.ui.theme.Motion

/**
 * The timeline of Typewriter, drawing aside: the wordmark's columns appear left to right behind a blinking cursor, then
 * the caption types in beneath it.
 */
class TypewriterScene(private val columns: Int, private val captionLength: Int) {
    /** Columns of the wordmark shown at [tMs]; all of them once the typing stretch is over. */
    fun visibleColumns(tMs: Float): Int = ((tMs / TYPE_MS).coerceIn(0f, 1f) * columns).toInt()

    /** The column the cursor block sits on: the reveal edge, held on the last column at the end. */
    fun cursorColumn(tMs: Float): Int = visibleColumns(tMs).coerceAtMost(columns - 1)

    /** Whether the cursor is lit in this blink phase. */
    fun cursorOn(tMs: Float): Boolean = (tMs / BLINK_MS).toInt() % 2 == 0

    /** Characters of the caption shown at [tMs]; none until the wordmark is complete. */
    fun captionChars(tMs: Float): Int {
        val progress = ((tMs - TYPE_MS) / (DURATION_MS - TYPE_MS)).coerceIn(0f, 1f)
        return (progress * captionLength).toInt()
    }

    fun finished(tMs: Float): Boolean = tMs >= DURATION_MS

    companion object {
        const val DURATION_MS = Motion.TYPEWRITER_MS
        const val TYPE_MS = 450
        const val BLINK_MS = 120
    }
}
