/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.ComponentName
import android.content.Intent
import android.text.SpannableString
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.data.ShareStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShareReceiverActivityTest {
    private val dirty = "https://a.example/x?utm_source=t&id=1"
    private val clean = "https://a.example/x?id=1"

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun reset() = Dispatchers.resetMain()

    private fun share(text: CharSequence?, subject: CharSequence? = null): Intent =
        Intent(Intent.ACTION_SEND).setType("text/plain").also { intent ->
            text?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
            subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        }

    private fun launch(intent: Intent): ShareReceiverActivity {
        val controller = Robolectric.buildActivity(ShareReceiverActivity::class.java, intent).setup()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return controller.get()
    }

    private fun awaitEvent(installed: TestGraph.Installed) = runBlocking {
        withTimeout(5_000) { installed.history.observeEvents().first { it.isNotEmpty() }.single() }
    }

    @Test
    fun `cleans the text and relays it to the sharesheet with a callback and itself excluded`() {
        val installed = TestGraph.install(FakeEngine.replacing(dirty, clean))
        val activity = launch(share("see $dirty now", subject = "Subject"))
        val chooser = shadowOf(activity).nextStartedActivity
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val relay = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("see $clean now", relay.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Subject", relay.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("text/plain", relay.type)
        assertNotNull(
            chooser.getParcelableExtra(
                Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER,
                android.content.IntentSender::class.java,
            )
        )
        val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ComponentName::class.java)!!
        assertEquals(ComponentName(activity, ShareReceiverActivity::class.java), excluded.single())
        assertTrue(activity.isFinishing)
        val event = awaitEvent(installed)
        assertEquals(ShareStatus.CLEANED, event.status)
        assertEquals("see $clean now", event.cleanedText)
        assertEquals("see $dirty now", event.originalText)
    }

    @Test
    fun `spannable text from browsers is accepted`() {
        TestGraph.install(FakeEngine.replacing(dirty, clean))
        val activity = launch(share(SpannableString("bold $dirty")))
        val relay = shadowOf(activity).nextStartedActivity.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("bold $clean", relay.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `without text nothing is shared and the activity finishes`() {
        val installed = TestGraph.install(FakeEngine.replacing(dirty, clean))
        val activity = launch(share(null))
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
        runBlocking { assertTrue(installed.history.observeEvents().first().isEmpty()) }
    }

    @Test
    fun `unhealthy rules forward the original text and say so`() {
        val installed = TestGraph.install(FakeEngine.replacing(dirty, clean, healthy = false))
        val activity = launch(share("see $dirty"))
        val relay = shadowOf(activity).nextStartedActivity.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("see $dirty", relay.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(ShareStatus.RULES_UNAVAILABLE, awaitEvent(installed).status)
        assertEquals(
            ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.rules_unavailable),
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `status distinguishes no urls from unchanged urls`() {
        val none = TestGraph.install(FakeEngine.noUrls())
        launch(share("no links"))
        assertEquals(ShareStatus.NO_URLS, awaitEvent(none).status)
        val same = TestGraph.install(FakeEngine.replacing(dirty, dirty))
        launch(share("see $dirty"))
        assertEquals(ShareStatus.UNCHANGED, awaitEvent(same).status)
    }
}
