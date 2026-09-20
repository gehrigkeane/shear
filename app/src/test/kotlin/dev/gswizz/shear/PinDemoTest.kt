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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    fun `the demo shears its sample link and keeps Shear in the chooser`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.replacing("&utm_source=newsletter&fbclid=IwAR0pin", ""))
        val chooser = PinDemo.share(context, installed.graph)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, target.action)
        assertEquals("text/plain", target.type)
        assertEquals("https://example.com/read?id=42", target.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(chooser.hasExtra(Intent.EXTRA_EXCLUDE_COMPONENTS))
    }

    @Test
    fun `the sample carries trackers the bundled rules really remove, and keeps the parameter that matters`() {
        // The real engine over the shipped Brave rules; the demo is only convincing if the sheet shows a real clean.
        val offline =
            object : RedirectTransport {
                override suspend fun head(url: UrlParts): TransportResponse = error("the sample never goes online")

                override suspend fun get(url: UrlParts): TransportResponse = error("the sample never goes online")
            }
        val result = Shear.default(offline).clean(PinDemo.SAMPLE_TEXT)
        assertEquals("https://example.com/read?id=42", result.outputText)
        assertTrue("more than one tracker goes", PinDemo.SAMPLE_TEXT.count { it == '&' } >= 2)
    }

    @Test
    fun `the demo is not a share, so it leaves no history and asks for no destination`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.replacing("&utm_source=newsletter&fbclid=IwAR0pin", ""))
        val chooser = PinDemo.share(context, installed.graph)
        assertFalse(chooser.hasExtra(Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER))
        // Recording is asynchronous; give a stray insert time to land before asserting nothing did.
        delay(500)
        assertTrue(installed.history.observeEvents().first().isEmpty())
    }
}
