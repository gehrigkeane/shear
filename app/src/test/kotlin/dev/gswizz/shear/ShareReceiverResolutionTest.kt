/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Intent
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.data.ShareStatus
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowChoreographer

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShareReceiverResolutionTest {
    private val short = "https://bit.ly/abc"
    private val resolved = "https://long.example/article"
    private val dispatcher = UnconfinedTestDispatcher()
    private val smart = Settings(redirectMode = ResolveMode.SMART)

    @Before
    fun main() {
        Dispatchers.setMain(dispatcher)
        // The indeterminate progress indicator animates forever; a paused choreographer keeps the looper able to idle.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
    }

    @After fun reset() = Dispatchers.resetMain()

    private fun launch(): ActivityController<ShareReceiverActivity> {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "see $short")
        val controller = Robolectric.buildActivity(ShareReceiverActivity::class.java, intent).setup()
        idle()
        return controller
    }

    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    private fun sharedText(activity: ShareReceiverActivity): String? =
        shadowOf(activity)
            .nextStartedActivity
            ?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            ?.getStringExtra(Intent.EXTRA_TEXT)

    private fun awaitEvent(installed: TestGraph.Installed) = runBlocking {
        withTimeout(5_000) { installed.history.observeEvents().first { it.isNotEmpty() }.single() }
    }

    @Test
    fun `smart mode resolves and shares the destination`() {
        val engine = FakeEngine.resolving(short, resolved)
        val installed = TestGraph.install(engine, smart)
        val activity = launch().get()
        assertEquals("see $resolved", sharedText(activity))
        assertEquals(listOf(ResolveMode.SMART), engine.processed)
        assertEquals(ShareStatus.CLEANED, awaitEvent(installed).status)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `off mode never calls process even when the engine would need the network`() {
        val engine = FakeEngine.resolving(short, resolved)
        TestGraph.install(engine, Settings(redirectMode = ResolveMode.OFF))
        val activity = launch().get()
        assertEquals("see $short", sharedText(activity))
        assertTrue(engine.processed.isEmpty())
    }

    @Test
    fun `while resolving the activity stays up showing progress and shares once resolution completes`() {
        val engine = FakeEngine.resolving(short, resolved).apply { gate = CompletableDeferred() }
        TestGraph.install(engine, smart)
        val activity = launch().get()
        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isResolving)
        engine.gate!!.complete(Unit)
        idle()
        assertEquals("see $resolved", sharedText(activity))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `cancelling shares the offline result and marks the event incomplete`() {
        val engine = FakeEngine.resolving(short, resolved).apply { gate = CompletableDeferred() }
        val installed = TestGraph.install(engine, smart)
        val activity = launch().get()
        activity.cancelResolution()
        idle()
        assertEquals("see $short", sharedText(activity))
        assertEquals(ShareStatus.RESOLUTION_INCOMPLETE, awaitEvent(installed).status)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `running out of time shares the offline result`() {
        val engine = FakeEngine.resolving(short, resolved).apply { gate = CompletableDeferred() }
        val installed = TestGraph.install(engine, smart)
        val activity = launch().get()
        dispatcher.scheduler.advanceTimeBy(ShareReceiverActivity.RESOLUTION_BUDGET_MS + 1)
        dispatcher.scheduler.runCurrent()
        idle()
        assertEquals("see $short", sharedText(activity))
        assertEquals(ShareStatus.RESOLUTION_INCOMPLETE, awaitEvent(installed).status)
    }

    @Test
    fun `leaving the app mid-resolution records a cancelled event and shares nothing`() {
        val engine = FakeEngine.resolving(short, resolved).apply { gate = CompletableDeferred() }
        val installed = TestGraph.install(engine, smart)
        val controller = launch()
        controller.pause().stop()
        idle()
        val activity = controller.get()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
        assertEquals(ShareStatus.CANCELLED, awaitEvent(installed).status)
    }

    @Test
    fun `a resolution that ends in a failure shares the best result but is marked incomplete`() {
        val failing =
            FakeEngine(
                mapOf(
                    ResolveMode.OFF to
                        { text: String ->
                            dev.gswizz.shear.core.TextResult(
                                text,
                                text,
                                listOf(dev.gswizz.shear.data.HistoryFixtures.trace),
                            )
                        },
                    ResolveMode.SMART to
                        { text: String ->
                            dev.gswizz.shear.core.TextResult(
                                text,
                                text,
                                listOf(dev.gswizz.shear.data.HistoryFixtures.failedTrace),
                            )
                        },
                ),
                networkNeeded = true,
            )
        val installed = TestGraph.install(failing, smart)
        launch()
        assertEquals(ShareStatus.RESOLUTION_INCOMPLETE, awaitEvent(installed).status)
    }
}
