/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinDemoTest {
    @Test
    fun `the demo is a plain text chooser that keeps Shear in the list`() {
        val chooser = PinDemo.chooser()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, target.action)
        assertEquals("text/plain", target.type)
        assertTrue(target.getStringExtra(Intent.EXTRA_TEXT)!!.startsWith("https://"))
        assertFalse(chooser.hasExtra(Intent.EXTRA_EXCLUDE_COMPONENTS))
    }
}
