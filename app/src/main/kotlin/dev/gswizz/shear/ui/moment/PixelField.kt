/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui.moment

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A fixed-capacity field of falling squares, the pixels every share moment throws around.
 *
 * State lives in parallel arrays and [step] never allocates, so a frame loop can call it freely. Squares beyond
 * [capacity] are dropped rather than grown into. All randomness comes from [random], so a seeded field replays
 * identically.
 */
class PixelField(val capacity: Int, private val random: Random) {
    var count: Int = 0
        private set

    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)
    val size = FloatArray(capacity)
    val colorIndex = IntArray(capacity)
    private val life = FloatArray(capacity)
    private val lifeTotal = FloatArray(capacity)

    /** Remaining life of square [i] as a fraction, 1 at birth falling to 0 at death; use as its alpha. */
    fun alpha(i: Int): Float = life[i] / lifeTotal[i]

    /** Adds one square. Ignored when the field is full. */
    fun spawn(x: Float, y: Float, vx: Float, vy: Float, size: Float, colorIndex: Int, lifeSeconds: Float) {
        if (count == capacity) return
        val i = count++
        this.x[i] = x
        this.y[i] = y
        this.vx[i] = vx
        this.vy[i] = vy
        this.size[i] = size
        this.colorIndex[i] = colorIndex
        life[i] = lifeSeconds
        lifeTotal[i] = lifeSeconds
    }

    /** Adds up to [count] squares at one point, flying outward in random directions at up to [speed]. */
    fun burst(
        originX: Float,
        originY: Float,
        count: Int,
        speed: Float,
        colorCount: Int,
        minSize: Float,
        maxSize: Float,
        lifeSeconds: Float,
    ) {
        repeat(count) {
            val angle = random.nextFloat() * TWO_PI
            val magnitude = speed * (HALF + random.nextFloat() * HALF)
            spawn(
                x = originX,
                y = originY,
                vx = cos(angle) * magnitude,
                vy = sin(angle) * magnitude,
                size = minSize + random.nextFloat() * (maxSize - minSize),
                colorIndex = random.nextInt(colorCount),
                lifeSeconds = lifeSeconds,
            )
        }
    }

    /** Advances every square by [dtSeconds] under [gravity] (pixels per second squared) and drops the dead. */
    fun step(dtSeconds: Float, gravity: Float) {
        var i = 0
        while (i < count) {
            vy[i] += gravity * dtSeconds
            x[i] += vx[i] * dtSeconds
            y[i] += vy[i] * dtSeconds
            life[i] -= dtSeconds
            if (life[i] <= 0f) remove(i) else i++
        }
    }

    private fun remove(i: Int) {
        val last = --count
        x[i] = x[last]
        y[i] = y[last]
        vx[i] = vx[last]
        vy[i] = vy[last]
        size[i] = size[last]
        colorIndex[i] = colorIndex[last]
        life[i] = life[last]
        lifeTotal[i] = lifeTotal[last]
    }

    private companion object {
        const val TWO_PI = (2 * Math.PI).toFloat()
        const val HALF = 0.5f
    }
}
