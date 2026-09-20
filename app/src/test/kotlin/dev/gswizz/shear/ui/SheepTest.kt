/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Catppuccin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheepTest {
    @get:Rule val compose = createComposeRule()

    private val flavor = Catppuccin.Mocha

    /**
     * The sheep at 108dp on Robolectric's mdpi screen, so one unit of its canvas is one pixel, sheared as [shear] says.
     */
    private var shear by mutableFloatStateOf(0f)

    private fun capture(): PixelMap {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        return compose.onNodeWithTag("sheep").captureToImage().toPixelMap()
    }

    private fun assertColor(expected: Color, actual: Color, where: String) {
        assertEquals("alpha $where", 1f, actual.alpha, 0.01f)
        assertEquals("red $where", expected.red, actual.red, 0.02f)
        assertEquals("green $where", expected.green, actual.green, 0.02f)
        assertEquals("blue $where", expected.blue, actual.blue, 0.02f)
    }

    private fun assertFleece(actual: Color, where: String) {
        assertEquals("alpha $where", 1f, actual.alpha, 0.01f)
        assertTrue("$where is warm fleece, got $actual", actual.red > actual.blue)
    }

    @Test
    fun `the shear takes the fleece off the rear and leaves the face on its tuft`() {
        compose.setContent {
            Canvas(modifier = Modifier.size(SheepGeometry.UNITS.dp).testTag("sheep")) {
                drawSheep(bounds = Rect(Offset.Zero, size), flavor = flavor, shear = shear)
            }
        }
        val whole = capture()
        assertFleece(whole[75, 60], "unsheared rear")
        assertFleece(whole[30, 62], "unsheared front")

        shear = 1f
        val sheared = capture()
        assertColor(Brand.skin(flavor), sheared[75, 60], "sheared rear")
        assertFleece(sheared[30, 62], "sheared front")
        assertColor(Brand.face(flavor), sheared[38, 50], "face")
        assertFleece(sheared[38, 33], "tuft above the head")
    }
}
