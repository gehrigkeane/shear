/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.core.Shear
import dev.gswizz.shear.core.net.RedirectTransport
import dev.gswizz.shear.core.net.TransportResponse
import dev.gswizz.shear.core.url.UrlParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinDemoTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the demo shares the sample untouched and keeps Shear in the chooser`() {
        val chooser = PinDemo.share(context)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, target.action)
        assertEquals("text/plain", target.type)
        // Dirty on purpose: picking Shear from this very sheet is what cleans it, in front of the user.
        assertEquals(PinDemo.SAMPLE_TEXT, target.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(chooser.hasExtra(Intent.EXTRA_EXCLUDE_COMPONENTS))
        assertFalse(
            "nothing to record, so nothing to report",
            chooser.hasExtra(Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER),
        )
    }

    @Test
    fun `the sample carries only trackers the bundled rules really remove`() {
        // The real engine over the shipped Brave rules: this is what the user sees when they pick Shear in the demo.
        val offline =
            object : RedirectTransport {
                override suspend fun head(url: UrlParts): TransportResponse = error("the sample never goes online")

                override suspend fun get(url: UrlParts): TransportResponse = error("the sample never goes online")
            }
        val result = Shear.default(offline).clean(PinDemo.SAMPLE_TEXT)
        assertEquals("https://example.com/read", result.outputText)
        assertTrue("more than one tracker goes", PinDemo.SAMPLE_TEXT.count { it == '&' } >= 1)
    }
}
