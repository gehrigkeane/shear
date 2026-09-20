/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import dev.gswizz.shear.ui.theme.Motion

/**
 * The timeline of The Sheep, drawing aside: a fully coated sheep holds still, a blade appears above its rear and
 * descends the launcher icon's slanted cut at a steady pace, and the fleece behind the line comes off as far down as
 * the blade has gone. Once through, the shorn sheep stands until the budget runs out.
 */
class SheepScene {
    /**
     * Where the blade is along the cut, as a fraction of the sheep's height: negative while it waits above the sheep, 0
     * at the top edge, 1 at the bottom edge once the cut is through.
     */
    fun blade(tMs: Float): Float {
        val progress = Motion.Easing.transform(((tMs - HOLD_MS) / DESCEND_MS).coerceIn(0f, 1f))
        return BLADE_START + (1f - BLADE_START) * progress
    }

    /** How far down the sheep the fleece right of the cut is gone: the blade's position clamped to the sheep. */
    fun depth(tMs: Float): Float = blade(tMs).coerceIn(0f, 1f)

    /** Whether the blade is drawn: only until the cut is through. */
    fun bladeShown(tMs: Float): Boolean = tMs < HOLD_MS + DESCEND_MS

    fun finished(tMs: Float): Boolean = tMs >= DURATION_MS

    companion object {
        const val DURATION_MS = Motion.SHEEP_MS
        const val HOLD_MS = 300
        const val DESCEND_MS = 800

        /** Where the blade waits, above the sheep by a fifth of its height. */
        const val BLADE_START = -0.2f
    }
}
