/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import dev.gswizz.shear.core.net.ResolveMode
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The store is plain JVM: DataStore over a temp file, no Android runtime required. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(scope: TestScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            File(folder.root, "settings.preferences_pb")
        }

    @Test
    fun `defaults are off thirty days and retain originals`() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = SettingsRepository(store(this)).settings.first()
            assertEquals(Settings(ResolveMode.OFF, HistoryRetention.DAYS_30, retainOriginals = true), settings)
            assertEquals(Settings(), settings)
        }

    @Test
    fun `each setter is observed by collectors`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = SettingsRepository(store(this))
            repository.settings.test {
                assertEquals(Settings(), awaitItem())
                repository.setRedirectMode(ResolveMode.SMART)
                assertEquals(ResolveMode.SMART, awaitItem().redirectMode)
                repository.setRetention(HistoryRetention.INDEFINITE)
                assertEquals(HistoryRetention.INDEFINITE, awaitItem().retention)
                repository.setRetainOriginals(false)
                assertEquals(false, awaitItem().retainOriginals)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `values written by an unknown future version fall back to defaults`() =
        runTest(UnconfinedTestDispatcher()) {
            val dataStore = store(this)
            dataStore.edit {
                it[stringPreferencesKey("redirect_mode")] = "TELEPORT"
                it[stringPreferencesKey("history_retention")] = "FOREVER_AND_A_DAY"
            }
            assertEquals(Settings(), SettingsRepository(dataStore).settings.first())
        }
}
