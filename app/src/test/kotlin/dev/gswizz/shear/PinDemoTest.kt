/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.data.ShareStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinDemoTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the demo cleans its sample link, records it, and keeps Shear in the chooser`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.replacing("?utm_source=shear&utm_medium=pin", ""))
        val chooser = PinDemo.share(context, installed.graph)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, target.action)
        assertEquals("text/plain", target.type)
        assertEquals("https://example.com/read", target.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(chooser.hasExtra(Intent.EXTRA_EXCLUDE_COMPONENTS))
        assertNotNull(chooser.getParcelableExtra(Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER, IntentSender::class.java))
        val event = withTimeout(5_000) { installed.history.observeEvents().first { it.isNotEmpty() }.single() }
        assertEquals(PinDemo.SAMPLE_TEXT, event.originalText)
        assertEquals("https://example.com/read", event.cleanedText)
        assertEquals(ShareStatus.CLEANED, event.status)
    }
}
