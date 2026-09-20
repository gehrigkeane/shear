/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Brand tokens that sit outside Material roles: the ink gradient the wordmark and the sheep's fleece share, the shadow
 * behind box-drawing glyphs, and the sheep's skin and face. Expressed over a [Flavor] so they follow light and dark.
 */
object Brand {
    /** Where the ink gradient starts, on the left. */
    fun inkStart(flavor: Flavor): Color = flavor.rosewater

    /** Where the ink gradient ends, on the right. */
    fun inkEnd(flavor: Flavor): Color = flavor.red

    /** The box-drawing shadow beside ink cells. */
    fun shadow(flavor: Flavor): Color = flavor.overlay0

    /** The sheep where the fleece has come off. */
    fun skin(flavor: Flavor): Color = flavor.lavender

    /** The sheep's face: the deepest neutral the flavor has, so it reads against the fleece. */
    fun face(flavor: Flavor): Color = if (flavor.dark) flavor.crust else flavor.text
}

/** Durations and easing for everything Shear animates. Milliseconds. */
object Motion {
    /** The wordmark whole and at rest, so the eye lands on it before the cut moves. */
    const val WORDMARK_HOLD_MS = 250

    /** The wordmark's shear, after the hold: the cut sweeping in from the right to rest across the A. */
    const val SHEAR_MS = 350

    /** The Cut share moment, first frame to handoff: hold, sweep, reflow, settle. */
    const val CUT_MS = 1_800

    /** The Cut: how long the URL sits still so the eye can land before the blade moves. */
    const val CUT_HOLD_MS = 250

    /** The Cut: the blade's crossing. */
    const val CUT_SWEEP_MS = 800

    /** The Cut: survivors reflowing into the clean URL while the last crumbs fall. */
    const val CUT_REFLOW_MS = 350

    /** The Cut: the clean URL at rest before the sharesheet. */
    const val CUT_SETTLE_MS = 400

    /** The Confetti share moment, pop to last faded square. */
    const val CONFETTI_MS = 1_500

    /** The Typewriter share moment, first column to the end of the hold after the caption. */
    const val TYPEWRITER_MS = 1_600

    val Easing: Easing = FastOutSlowInEasing
}

/** The spacing scale. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
}
