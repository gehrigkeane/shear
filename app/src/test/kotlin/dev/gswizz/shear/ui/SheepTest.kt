/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Catppuccin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SheepTest {
    private val flavor = Catppuccin.Mocha

    /** The sheep at its own 108 units, one unit per pixel, sheared as [shear] says down to [depth] of its height. */
    private fun sheep(shear: Float, depth: Float = 1f): PixelMap =
        raster(SheepGeometry.UNITS.toInt(), SheepGeometry.UNITS.toInt()) {
            drawSheep(bounds = Rect(Offset.Zero, size), flavor = flavor, shear = shear, depth = depth)
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
        val whole = sheep(shear = 0f)
        assertFleece(whole[75, 60], "unsheared rear")
        assertFleece(whole[30, 62], "unsheared front")

        val sheared = sheep(shear = 1f)
        assertColor(Brand.skin(flavor), sheared[75, 60], "sheared rear")
        assertFleece(sheared[30, 62], "sheared front")
        assertColor(Brand.face(flavor), sheared[38, 50], "face")
        assertFleece(sheared[38, 33], "tuft above the head")
    }

    @Test
    fun `a partial depth takes the fleece right of the cut only as far down as the blade has gone`() {
        // Two fleece points right of the cut, one above the body and one on it; the blade is halfway down.
        val half = sheep(shear = 1f, depth = 0.5f)
        assertEquals("above the blade the fleece is gone", 0f, half[70, 46].alpha, 0.01f)
        assertFleece(half[75, 66], "below the blade")
        assertFleece(sheep(shear = 1f, depth = 0f)[70, 46], "nothing cut at depth zero")
        assertColor(Brand.skin(flavor), sheep(shear = 1f, depth = 1f)[75, 66], "cut through at full depth")
    }
}
