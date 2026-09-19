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
import dev.gswizz.shear.data.HistoryFixtures
import dev.gswizz.shear.data.HistoryRetention
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.data.ShareStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {
    @Test
    fun `groups events by day newest first with host summaries and destinations`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.noUrls())
        val day = 24L * 60 * 60 * 1000
        var now = 1_700_000_000_000L
        val repo = installed.history
        val clocked =
            dev.gswizz.shear.data.HistoryRepository(installed.db.dao(), installed.settings, installed.graph.appScope) {
                now
            }
        clocked
            .record("old", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings())
            .join()
        now += 2 * day
        clocked
            .record(
                "new",
                "no links",
                dev.gswizz.shear.core.TextResult("no links", "no links", emptyList()),
                ShareStatus.NO_URLS,
                Settings(),
            )
            .join()
        clocked.recordDestination("new", ComponentName("com.messages", "com.messages.Share"), "Messages", now).join()
        val viewModel = HistoryViewModel(installed.graph)
        viewModel.state.test {
            val loaded = awaitItem().let { if (it.loading) awaitItem() else it }
            assertFalse(loaded.loading)
            assertEquals(2, loaded.sections.size)
            assertEquals(listOf("new", "old"), loaded.sections.flatMap { it.rows }.map { it.id })
            val newRow = loaded.sections[0].rows.single()
            assertEquals("Messages", newRow.destination?.label)
            assertEquals(ShareStatus.NO_URLS, newRow.status)
            assertEquals(0, newRow.urlCount)
            val oldRow = loaded.sections[1].rows.single()
            assertEquals("dest.example", oldRow.summary)
            assertEquals(1, oldRow.urlCount)
            assertTrue(loaded.sections[0].date.isAfter(loaded.sections[1].date))
            assertFalse(loaded.rulesUnavailable)
            assertFalse(loaded.retentionOff)
            cancelAndIgnoreRemainingEvents()
        }
        repo.clear()
    }

    @Test
    fun `flags unhealthy rules and retention off`() = runBlocking {
        val installed =
            TestGraph.install(
                FakeEngine.noUrls().let { FakeEngine.replacing("a", "a", healthy = false) },
                Settings(retention = HistoryRetention.OFF),
            )
        val viewModel = HistoryViewModel(installed.graph)
        viewModel.state.test {
            val loaded = awaitItem().let { if (it.loading) awaitItem() else it }
            assertTrue(loaded.rulesUnavailable)
            assertTrue(loaded.retentionOff)
            assertTrue(loaded.sections.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
