/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.data.DestinationResolver
import dev.gswizz.shear.data.HistoryFixtures
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.data.ShareStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ChosenComponentReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val messages = ComponentName("com.messages", "com.messages.ShareActivity")

    private fun installMessages() {
        val pm = shadowOf(context.packageManager)
        val appInfo =
            ApplicationInfo().apply {
                packageName = messages.packageName
                nonLocalizedLabel = "Messages App"
            }
        pm.installPackage(
            android.content.pm.PackageInfo().apply {
                packageName = messages.packageName
                applicationInfo = appInfo
            }
        )
        pm.addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = messages.packageName
                name = messages.className
                nonLocalizedLabel = "Messages"
                applicationInfo = appInfo
            }
        )
    }

    @Test
    fun `attaches the chosen component and its label to the event`() = runBlocking {
        installMessages()
        val installed = TestGraph.install(FakeEngine.noUrls())
        installed.history
            .record("evt-1", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings())
            .join()
        val intent =
            Intent(context, ChosenComponentReceiver::class.java)
                .setData("shear://event/evt-1".toUri())
                .putExtra(Intent.EXTRA_CHOSEN_COMPONENT, messages)
        ChosenComponentReceiver().onReceive(context, intent)
        val event =
            withTimeout(5_000) {
                    installed.history.observeEvent("evt-1").first { it?.event?.destinationPackage != null }!!
                }
                .event
        assertEquals("com.messages", event.destinationPackage)
        assertEquals("com.messages/.ShareActivity", event.destinationComponent)
        assertEquals("Messages", event.destinationLabel)
    }

    @Test
    fun `a broadcast without a component or an event id is ignored`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.noUrls())
        installed.history
            .record("evt-2", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings())
            .join()
        ChosenComponentReceiver()
            .onReceive(
                context,
                Intent(context, ChosenComponentReceiver::class.java).setData("shear://event/evt-2".toUri()),
            )
        ChosenComponentReceiver()
            .onReceive(
                context,
                Intent(context, ChosenComponentReceiver::class.java).putExtra(Intent.EXTRA_CHOSEN_COMPONENT, messages),
            )
        assertNull(installed.history.observeEvent("evt-2").first()!!.event.destinationPackage)
    }

    @Test
    fun `the destination resolver falls back from activity to application label to nothing`() {
        installMessages()
        val resolver = DestinationResolver(context.packageManager)
        assertEquals("Messages", resolver.label(messages))
        assertEquals("Messages App", resolver.label(ComponentName("com.messages", "com.messages.Other")))
        assertNull(resolver.label(ComponentName("com.nobody", "com.nobody.X")))
    }
}
