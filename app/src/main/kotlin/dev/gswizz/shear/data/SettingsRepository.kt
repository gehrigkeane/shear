/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.gswizz.shear.core.net.ResolveMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How long share history stays on the device. */
enum class HistoryRetention {
    /** Record nothing; existing history is deleted. */
    OFF,
    DAYS_30,
    INDEFINITE,
}

/** Everything the user can configure. Defaults are the privacy-preserving choices. */
data class Settings(
    val redirectMode: ResolveMode = ResolveMode.OFF,
    val retention: HistoryRetention = HistoryRetention.DAYS_30,
    val retainOriginals: Boolean = true,
)

/** Read and write access to [Settings]; the interface exists so tests can substitute an in-memory store. */
interface SettingsStore {
    val settings: Flow<Settings>

    suspend fun setRedirectMode(mode: ResolveMode)

    suspend fun setRetention(retention: HistoryRetention)

    suspend fun setRetainOriginals(retain: Boolean)
}

/**
 * [SettingsStore] over DataStore Preferences.
 *
 * Enum values are stored by name. A value this version does not recognize, for example one written by a newer build,
 * reads back as the default rather than crashing.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsStore {
    override val settings: Flow<Settings> =
        dataStore.data.map { prefs ->
            Settings(
                redirectMode =
                    prefs[REDIRECT_MODE]?.let { name -> ResolveMode.entries.firstOrNull { it.name == name } }
                        ?: ResolveMode.OFF,
                retention =
                    prefs[RETENTION]?.let { name -> HistoryRetention.entries.firstOrNull { it.name == name } }
                        ?: HistoryRetention.DAYS_30,
                retainOriginals = prefs[RETAIN_ORIGINALS] ?: true,
            )
        }

    override suspend fun setRedirectMode(mode: ResolveMode) {
        dataStore.edit { it[REDIRECT_MODE] = mode.name }
    }

    override suspend fun setRetention(retention: HistoryRetention) {
        dataStore.edit { it[RETENTION] = retention.name }
    }

    override suspend fun setRetainOriginals(retain: Boolean) {
        dataStore.edit { it[RETAIN_ORIGINALS] = retain }
    }

    private companion object {
        val REDIRECT_MODE = stringPreferencesKey("redirect_mode")
        val RETENTION = stringPreferencesKey("history_retention")
        val RETAIN_ORIGINALS = booleanPreferencesKey("retain_originals")
    }
}
