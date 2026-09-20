/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

/** One character of the original URL and whether the cut takes it. */
data class CutChar(val char: Char, val removed: Boolean)

/**
 * The timeline of The Cut over a URL wrapped into a monospace grid, in characters and milliseconds, with no drawing.
 *
 * The text sits still for [holdMs]. Then a blade visits every character in reading order at a steady pace, line by
 * line, finishing all of them in [sweepMs]; a removed character is cut the moment the blade passes it. Once the sweep
 * is over, the survivors reflow into the final URL over [reflowMs]. Everything is a pure function of time so a frame
 * can be evaluated anywhere, including in tests.
 */
class CutScene(
    val chars: List<CutChar>,
    val columns: Int,
    val holdMs: Int,
    val sweepMs: Int,
    val reflowMs: Int,
) {
    val lines: Int = ((chars.size + columns - 1) / columns).coerceAtLeast(1)

    /** When the blade has passed the last character. */
    val sweepEndMs: Int = holdMs + sweepMs

    fun lineOf(index: Int): Int = index / columns

    fun columnOf(index: Int): Int = index % columns

    /** How far along the text the blade is, in characters; 0 through the hold, [chars].size at the end of the sweep. */
    fun bladeIndex(tMs: Float): Float = ((tMs - holdMs) / sweepMs).coerceIn(0f, 1f) * chars.size

    fun bladeLine(tMs: Float): Int = lineOf(bladeIndex(tMs).toInt().coerceAtMost(chars.lastIndex.coerceAtLeast(0)))

    /** The blade's position within its line, in characters, fractional. */
    fun bladeColumn(tMs: Float): Float = bladeIndex(tMs) - bladeLine(tMs) * columns

    /** Whether character [index] has fallen: only removed characters do, once the blade is past them. */
    fun isCut(index: Int, tMs: Float): Boolean = chars[index].removed && bladeIndex(tMs) > index

    /** Progress of the survivors closing into the final URL, 0 until the sweep ends and 1 when settled. */
    fun reflow(tMs: Float): Float = ((tMs - sweepEndMs) / reflowMs).coerceIn(0f, 1f)

    fun finished(tMs: Float): Boolean = tMs >= sweepEndMs + reflowMs

    companion object {
        fun of(plan: CutPlan, columns: Int, holdMs: Int, sweepMs: Int, reflowMs: Int): CutScene =
            CutScene(
                chars = plan.segments.flatMap { run -> run.text.map { CutChar(it, run.removed) } },
                columns = columns,
                holdMs = holdMs,
                sweepMs = sweepMs,
                reflowMs = reflowMs,
            )
    }
}
