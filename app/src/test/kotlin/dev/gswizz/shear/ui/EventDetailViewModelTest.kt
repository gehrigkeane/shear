/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import android.content.ComponentName
import app.cash.turbine.test
import dev.gswizz.shear.FakeEngine
import dev.gswizz.shear.TestGraph
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.data.HistoryFixtures
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.data.ShareStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventDetailViewModelTest {
    @Test
    fun `maps a stored event and its traces into readable detail`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.noUrls())
        val result = TextResult(HistoryFixtures.ORIGINAL, HistoryFixtures.CLEANED, listOf(HistoryFixtures.failedTrace))
        installed.history
            .record("e1", HistoryFixtures.ORIGINAL, result, ShareStatus.RESOLUTION_INCOMPLETE, Settings())
            .join()
        installed.history
            .recordDestination("e1", ComponentName("com.messages", "com.messages.Share"), "Messages", 1L)
            .join()
        EventDetailViewModel(installed.graph, "e1").state.test {
            val loaded =
                awaitItem().let { if (it is EventDetailUiState.Loading) awaitItem() else it }
                    as EventDetailUiState.Loaded
            val event = loaded.event
            assertEquals(HistoryFixtures.ORIGINAL, event.originalText)
            assertEquals(HistoryFixtures.CLEANED, event.cleanedText)
            assertEquals("dest.example", event.summary)
            assertEquals("Messages", event.destination?.label)
            assertEquals(ShareStatus.RESOLUTION_INCOMPLETE, event.status)
            val trace = event.traces.single()
            assertEquals(HistoryFixtures.trace.originalUrl, trace.originalUrl)
            assertEquals(HistoryFixtures.trace.finalUrl, trace.finalUrl)
            assertEquals(2, trace.rules.size)
            assertTrue(trace.rules[0].contains("debounce") && trace.rules[0].contains("#3"))
            assertTrue(trace.rules[1].contains("clean-urls") && trace.rules[1].contains("#44"))
            assertEquals(listOf("utm_source=x"), trace.removedParams)
            assertEquals(1, trace.redirectSteps.size)
            assertTrue(trace.redirectSteps[0].contains("offline"))
            assertEquals("TIMEOUT", trace.failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unknown id is missing and a redacted event shows only the hash`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.noUrls())
        EventDetailViewModel(installed.graph, "nope").state.test {
            val state = awaitItem().let { if (it is EventDetailUiState.Loading) awaitItem() else it }
            assertEquals(EventDetailUiState.Missing, state)
            cancelAndIgnoreRemainingEvents()
        }
        installed.history
            .record(
                "e2",
                HistoryFixtures.ORIGINAL,
                HistoryFixtures.textResult,
                ShareStatus.CLEANED,
                Settings(retainOriginals = false),
            )
            .join()
        EventDetailViewModel(installed.graph, "e2").state.test {
            val loaded =
                awaitItem().let { if (it is EventDetailUiState.Loading) awaitItem() else it }
                    as EventDetailUiState.Loaded
            assertNull(loaded.event.originalText)
            assertEquals(64, loaded.event.originalHash.length)
            assertNull(loaded.event.traces.single().originalUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
