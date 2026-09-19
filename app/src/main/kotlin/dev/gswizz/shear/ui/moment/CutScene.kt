/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

/**
 * The timeline of The Cut over one measured line of [segments], in pixels and milliseconds, with no drawing.
 *
 * The line sits still for [holdMs], then a blade crosses it left to right in [sweepMs]. A removed run crumbles from its
 * left edge as the blade passes over it, and once the blade clears its right edge every run after it slides left by
 * that run's width over [slideMs], so the survivors close ranks into the final URL. Everything is a pure function of
 * time so a frame can be evaluated anywhere, including in tests.
 */
class CutScene(
    val segments: List<Segment>,
    val widths: List<Float>,
    val sweepMs: Int,
    val slideMs: Int,
    val holdMs: Int = 0,
) {
    private val lefts: List<Float> = widths.runningFold(0f) { acc, w -> acc + w }
    val totalWidth: Float = lefts.last()

    /** When the blade has finished crossing. */
    val sweepEndMs: Int = holdMs + sweepMs

    fun bladeX(tMs: Float): Float = ((tMs - holdMs) / sweepMs).coerceIn(0f, 1f) * totalWidth

    /** How much of removed run [i], from its left edge, has crumbled by [tMs]; always 0 for a kept run. */
    fun cutFraction(i: Int, tMs: Float): Float {
        if (!segments[i].removed || widths[i] == 0f) return 0f
        return ((bladeX(tMs) - lefts[i]) / widths[i]).coerceIn(0f, 1f)
    }

    /** Progress of the leftward slide that removed run [i] causes in the runs after it; 0 for a kept run. */
    fun slide(i: Int, tMs: Float): Float {
        if (!segments[i].removed) return 0f
        val exitMs = holdMs + if (totalWidth == 0f) 0f else sweepMs * (lefts[i] + widths[i]) / totalWidth
        return ((tMs - exitMs) / slideMs).coerceIn(0f, 1f)
    }

    /** The left edge of run [i] at [tMs]: its natural position less every earlier removed run that has slid away. */
    fun x(i: Int, tMs: Float): Float {
        var shift = 0f
        for (j in 0 until i) if (segments[j].removed) shift += widths[j] * slide(j, tMs)
        return lefts[i] - shift
    }

    fun finished(tMs: Float): Boolean = tMs >= sweepEndMs + slideMs
}
