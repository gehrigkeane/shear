/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.test.core.app.ApplicationProvider
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
    }

    @Test
    fun `the icon is full-bleed adaptive layers the system masks like the launcher icon`() {
        // Read back from the manager, shortcuts carry no icon, so inspect the one we publish.
        assertEquals(IconCompat.TYPE_ADAPTIVE_BITMAP, ShareShortcut.shortcut(context).icon.type)
    }

    @Test
    fun `publishing twice keeps a single shortcut and usage can be reported`() {
        ShareShortcut.publish(context)
        ShareShortcut.publish(context)
        assertEquals(1, ShortcutManagerCompat.getDynamicShortcuts(context).size)
        ShareShortcut.reportUsed(context)
    }
}
