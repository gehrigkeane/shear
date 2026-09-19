/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.ui

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.gswizz.shear.FakeEngine
import dev.gswizz.shear.TestGraph
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.data.HistoryFixtures
import dev.gswizz.shear.data.HistoryRetention
import dev.gswizz.shear.data.Settings
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun reset() = Dispatchers.resetMain()

    private suspend fun awaitEmpty(installed: TestGraph.Installed) =
        withTimeout(5_000) { installed.history.observeEvents().first { it.isEmpty() } }

    private suspend fun ReceiveTurbine<SettingsUiState>.awaitUntil(
        predicate: (SettingsUiState) -> Boolean
    ): SettingsUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `turning history off asks first and then deletes and applies`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.noUrls())
        installed.history
            .record("e1", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings())
            .join()
        val viewModel = SettingsViewModel(installed.graph, appVersion = "0.1.0")
        viewModel.state.test {
            viewModel.requestRetention(HistoryRetention.OFF)
            awaitUntil { it.confirmation == Confirmation.RETENTION_OFF }
            assertEquals(HistoryRetention.DAYS_30, installed.settings.state.value.retention)
            viewModel.dismiss()
            awaitUntil { it.confirmation == null }
            assertEquals(1, installed.history.observeEvents().first().size)
            viewModel.requestRetention(HistoryRetention.OFF)
            awaitUntil { it.confirmation == Confirmation.RETENTION_OFF }
            viewModel.confirm()
            val applied = awaitUntil { it.confirmation == null && it.settings.retention == HistoryRetention.OFF }
            assertEquals(HistoryRetention.OFF, applied.settings.retention)
            awaitEmpty(installed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `other settings apply immediately and clear history asks first`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.noUrls())
        installed.history
            .record("e1", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings())
            .join()
        val viewModel = SettingsViewModel(installed.graph, appVersion = "0.1.0")
        viewModel.state.test {
            assertEquals("0.1.0", awaitItem().appVersion)
            viewModel.setRedirectMode(ResolveMode.SMART)
            awaitUntil { it.settings.redirectMode == ResolveMode.SMART }
            viewModel.requestRetention(HistoryRetention.INDEFINITE)
            awaitUntil { it.settings.retention == HistoryRetention.INDEFINITE }
            viewModel.setRetainOriginals(false)
            awaitUntil { !it.settings.retainOriginals }
            viewModel.requestClearHistory()
            awaitUntil { it.confirmation == Confirmation.CLEAR_HISTORY }
            viewModel.confirm()
            assertNull(awaitUntil { it.confirmation == null }.confirmation)
            awaitEmpty(installed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exposes the rules version once the engine is ready`() = runBlocking {
        val installed = TestGraph.install(FakeEngine.noUrls())
        val viewModel = SettingsViewModel(installed.graph, appVersion = "0.1.0")
        viewModel.state.test {
            val loaded = awaitUntil { it.rulesVersion.isNotEmpty() }
            assertEquals("test-rules", loaded.rulesVersion)
            assertTrue(loaded.rulesUpstream.contains("brave/adblock-lists"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
