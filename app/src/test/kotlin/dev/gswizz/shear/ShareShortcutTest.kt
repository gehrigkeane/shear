/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.ui.ScissorsGeometry
import dev.gswizz.shear.ui.theme.Brand
import dev.gswizz.shear.ui.theme.Catppuccin
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareShortcutTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `publishing installs one sharing shortcut in the share category at rank zero`() {
        ShareShortcut.publish(context)
        val shortcut = ShortcutManagerCompat.getDynamicShortcuts(context).single()
        assertEquals(ShareShortcut.ID, shortcut.id)
        assertTrue(ShareShortcut.CATEGORY in shortcut.categories.orEmpty())
        assertEquals(0, shortcut.rank)
        assertEquals("Shear", shortcut.shortLabel.toString())
        assertEquals("Shear link", shortcut.longLabel.toString())
    }

    @Test
    fun `the icon is full-bleed adaptive layers the system masks like the launcher icon`() {
        // Read back from the manager, shortcuts carry no icon, so inspect the one we publish.
        assertEquals(IconCompat.TYPE_ADAPTIVE_BITMAP, ShareShortcut.shortcut(context).icon.type)
    }

    @Test
    fun `the shortcut is the scissors, pivot and blades and hollow rings where the geometry says`() {
        // Robolectric is mdpi, so one icon unit is one pixel; sample points come from the geometry, not from the art.
        val icon = ShareShortcut.icon(context)
        val background = context.getColor(R.color.ic_launcher_background)
        fun at(p: Offset) = icon.getPixel(p.x.roundToInt(), p.y.roundToInt())
        val pivot = at(ScissorsGeometry.pivot)
        assertEquals("pivot is the face color", Brand.face(Catppuccin.Mocha).toArgb(), pivot)
        for (tip in ScissorsGeometry.tips) {
            val mid = at((ScissorsGeometry.pivot + tip) / 2f)
            assertEquals(255, Color.alpha(mid))
            assertTrue("blade is ink, got #${Integer.toHexString(mid)}", Color.red(mid) > Color.blue(mid))
        }
        for (ring in ScissorsGeometry.ringCenters) assertEquals("rings are hollow", background, at(ring))
    }

    @Test
    fun `the implement leans right, so the corner the sharesheet badges stays clear`() {
        val icon = ShareShortcut.icon(context)
        val background = context.getColor(R.color.ic_launcher_background)
        // The system shows the central 72 of 108 units; the badge sits over the bottom-right of that.
        for (x in 78..88 step 5) for (y in 78..88 step 5) assertEquals("($x,$y)", background, icon.getPixel(x, y))
    }

    @Test
    fun `publishing twice keeps a single shortcut and usage can be reported`() {
        ShareShortcut.publish(context)
        ShareShortcut.publish(context)
        assertEquals(1, ShortcutManagerCompat.getDynamicShortcuts(context).size)
        ShareShortcut.reportUsed(context)
    }
}
