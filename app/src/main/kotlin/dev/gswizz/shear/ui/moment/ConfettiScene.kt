/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import dev.gswizz.shear.ui.theme.Motion
import kotlin.random.Random

/**
 * The timeline of Confetti, drawing aside: the mark pops in, a burst of squares flies out and falls, and everything
 * fades in the final stretch. Pixel quantities are scaled by [density] so the scene looks the same on every screen.
 */
class ConfettiScene(random: Random, private val colorCount: Int, private val density: Float) {
    val field = PixelField(SQUARES, random)
    private var burst = false

    /** Scale of the mark: eight tenths at the start, full size once the pop lands. */
    fun markScale(tMs: Float): Float {
        val progress = Motion.Easing.transform((tMs / POP_MS).coerceIn(0f, 1f))
        return START_SCALE + (1f - START_SCALE) * progress
    }

    /** Advances the squares by one frame, firing the burst from the mark's center the first time the pop lands. */
    fun frame(tMs: Float, dtSeconds: Float, originX: Float, originY: Float) {
        if (!burst && tMs >= POP_MS) {
            burst = true
            field.burst(
                originX = originX,
                originY = originY,
                count = SQUARES,
                speed = SPEED_DP * density,
                colorCount = colorCount,
                minSize = MIN_SIZE_DP * density,
                maxSize = MAX_SIZE_DP * density,
                lifeSeconds = LIFE_S,
            )
        }
        field.step(dtSeconds, gravity = GRAVITY_DP * density)
    }

    /** Global alpha for the squares: full until the last [FADE_MS], then down to nothing. */
    fun fade(tMs: Float): Float = ((DURATION_MS - tMs) / FADE_MS).coerceIn(0f, 1f)

    fun finished(tMs: Float): Boolean = tMs >= DURATION_MS

    companion object {
        const val DURATION_MS = Motion.CONFETTI_MS
        const val POP_MS = 250
        const val FADE_MS = 350
        const val SQUARES = 120
        private const val START_SCALE = 0.8f
        private const val SPEED_DP = 380f
        private const val GRAVITY_DP = 700f
        private const val MIN_SIZE_DP = 3f
        private const val MAX_SIZE_DP = 6f

        /** Longer than the budget after the pop, so the fade, not death, is what ends every square. */
        private const val LIFE_S = 1.3f
    }
}
