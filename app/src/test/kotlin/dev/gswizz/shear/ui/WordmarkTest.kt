/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.ui.theme.ShearTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WordmarkTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `adjacent ink cells fill without seams at fractional cell sizes`() {
        // 300dp over 40 columns is 7.5px per cell on Robolectric's mdpi screen, so every other cell edge splits a
        // pixel,
        // which is where per-rectangle anti-aliasing shows seams.
        compose.setContent { ShearTheme { Wordmark(modifier = Modifier.width(300.dp)) } }
        compose.mainClock.advanceTimeBy(2_000)
        val pixels = compose.onNodeWithContentDescription("Shear").captureToImage().toPixelMap()
        val cell = pixels.width / 40f
        // The S's top row is seven solid cells; sample its vertical middle from the first cell into the seventh.
        val y = (cell / 2).toInt()
        val from = (cell * 0.5f).toInt()
        val to = (cell * 6.5f).toInt()
        var previous = pixels[from, y]
        for (x in from..to) {
            val pixel = pixels[x, y]
            assertEquals("alpha at x=$x", 1f, pixel.alpha, 0.01f)
            val jump =
                abs(pixel.red - previous.red) + abs(pixel.green - previous.green) + abs(pixel.blue - previous.blue)
            assertTrue("seam at x=$x (jump $jump)", jump < 0.05f)
            previous = pixel
        }
    }
}
