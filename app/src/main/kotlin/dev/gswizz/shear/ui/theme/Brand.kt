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
 * Brand tokens that sit outside Material roles: the ink gradient the wordmark and launcher icon share, and the shadow
 * behind box-drawing glyphs. Expressed over a [Flavor] so they follow light and dark.
 */
object Brand {
    /** Where the ink gradient starts, on the left. */
    fun inkStart(flavor: Flavor): Color = flavor.rosewater

    /** Where the ink gradient ends, on the right. */
    fun inkEnd(flavor: Flavor): Color = flavor.red

    /** The box-drawing shadow beside ink cells. */
    fun shadow(flavor: Flavor): Color = flavor.overlay0
}

/** Durations and easing for everything Shear animates. Milliseconds. */
object Motion {
    /** The wordmark's diagonal reveal. */
    const val REVEAL_MS = 500

    /** The Cut share moment, blade to settled URL. */
    const val CUT_MS = 850

    /** The Confetti share moment, pop to last falling square. */
    const val CONFETTI_MS = 800

    /** The Typewriter share moment, first column to last caption character. */
    const val TYPEWRITER_MS = 700

    val Easing: Easing = FastOutSlowInEasing
}

/** The spacing scale. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
}
