/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.animation.ValueAnimator
import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.data.MomentStyle
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.data.ShareStatus
import dev.gswizz.shear.ui.theme.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareMomentActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val dirty = "https://a.example/x?utm_source=t&id=1"
    private val clean = "https://a.example/x?id=1"

    @After fun animatorsBackOn() = durationScale(1f)

    private fun share(text: String): Intent =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    /** The framework's hidden switch behind ValueAnimator.areAnimatorsEnabled, what "Remove animations" flips. */
    private fun durationScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.java).invoke(null, scale)
    }

    private fun ActivityScenario<ShareReceiverActivity>.chooser(): Intent? {
        var started: Intent? = null
        onActivity { started = shadowOf(it).nextStartedActivity }
        return started
    }

    private fun awaitEvent(installed: TestGraph.Installed) = runBlocking {
        withTimeout(5_000) { installed.history.observeEvents().first { it.isNotEmpty() }.single() }
    }

    @Test
    fun `the default moment plays out before the chooser opens`() {
        val installed = TestGraph.install(FakeEngine.replacing(dirty, clean))
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<ShareReceiverActivity>(share("see $dirty")).use { scenario ->
            compose.waitForIdle()
            compose.onNodeWithText("Removed utm_source").assertExists()
            assertNull(scenario.chooser())
            compose.mainClock.advanceTimeBy(Motion.CUT_MS + 200L)
            compose.waitForIdle()
            val chooser = scenario.chooser()
            assertNotNull(chooser)
            val relay = chooser!!.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            assertEquals("see $clean", relay.getStringExtra(Intent.EXTRA_TEXT))
            scenario.onActivity { assertTrue(it.isFinishing) }
            assertEquals(ShareStatus.CLEANED, awaitEvent(installed).status)
        }
    }

    @Test
    fun `back skips the moment straight to the chooser`() {
        TestGraph.install(FakeEngine.replacing(dirty, clean))
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<ShareReceiverActivity>(share("see $dirty")).use { scenario ->
            compose.waitForIdle()
            assertNull(scenario.chooser())
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            assertNotNull(scenario.chooser())
            scenario.onActivity { assertTrue(it.isFinishing) }
        }
    }

    @Test
    fun `leaving mid-moment records a cancelled share and opens nothing`() {
        val installed = TestGraph.install(FakeEngine.replacing(dirty, clean))
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<ShareReceiverActivity>(share("see $dirty")).use { scenario ->
            compose.waitForIdle()
            scenario.moveToState(Lifecycle.State.CREATED)
            assertNull(scenario.chooser())
            assertEquals(ShareStatus.CANCELLED, awaitEvent(installed).status)
        }
    }

    /** When the moment is skipped the activity finishes before the scenario can look at it; the app remembers. */
    private fun appChooser(): Intent? =
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity

    @Test
    fun `with system animations off the moment is skipped`() {
        durationScale(0f)
        TestGraph.install(FakeEngine.replacing(dirty, clean))
        ActivityScenario.launch<ShareReceiverActivity>(share("see $dirty")).use {
            compose.waitForIdle()
            assertEquals(Intent.ACTION_CHOOSER, appChooser()?.action)
        }
    }

    @Test
    fun `a share with no links still gets its moment and caption`() {
        TestGraph.install(FakeEngine.noUrls())
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<ShareReceiverActivity>(share("no links")).use { scenario ->
            compose.waitForIdle()
            compose.onNodeWithText("No links").assertExists()
            assertNull(scenario.chooser())
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }

    @Test
    fun `the moment is configurable off`() {
        TestGraph.install(FakeEngine.replacing(dirty, clean), Settings(moment = MomentStyle.OFF))
        ActivityScenario.launch<ShareReceiverActivity>(share("see $dirty")).use {
            compose.waitForIdle()
            assertEquals(Intent.ACTION_CHOOSER, appChooser()?.action)
        }
    }
}
