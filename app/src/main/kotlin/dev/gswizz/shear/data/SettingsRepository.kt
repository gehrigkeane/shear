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

/** What plays between picking Shear and the sharesheet appearing. */
enum class MomentStyle {
    /** Straight to the sharesheet, nothing drawn. */
    OFF,
    /** The tracking tail is sheared off the URL. */
    CUT,
    /** The sheep is shorn along the icon's cut. */
    SHEEP,
    /** A cursor hollows the wordmark out. */
    TYPEWRITER,
}

/** Everything the user can configure. Defaults are the privacy-preserving choices. */
data class Settings(
    val redirectMode: ResolveMode = ResolveMode.OFF,
    val retention: HistoryRetention = HistoryRetention.DAYS_30,
    val retainOriginals: Boolean = true,
    /** Whether the one-time hint about pinning Shear in the sharesheet has been shown or dismissed. */
    val pinPromptSeen: Boolean = false,
    val moment: MomentStyle = MomentStyle.CUT,
)

/** Read and write access to [Settings]; the interface exists so tests can substitute an in-memory store. */
interface SettingsStore {
    val settings: Flow<Settings>

    suspend fun setRedirectMode(mode: ResolveMode)

    suspend fun setRetention(retention: HistoryRetention)

    suspend fun setRetainOriginals(retain: Boolean)

    suspend fun setPinPromptSeen()

    suspend fun setMoment(style: MomentStyle)
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
                pinPromptSeen = prefs[PIN_PROMPT_SEEN] ?: false,
                moment =
                    prefs[MOMENT]?.let { name -> MomentStyle.entries.firstOrNull { it.name == name } }
                        ?: if (prefs[MOMENT] == LEGACY_CONFETTI) MomentStyle.SHEEP else MomentStyle.CUT,
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

    override suspend fun setPinPromptSeen() {
        dataStore.edit { it[PIN_PROMPT_SEEN] = true }
    }

    override suspend fun setMoment(style: MomentStyle) {
        dataStore.edit { it[MOMENT] = style.name }
    }

    private companion object {
        val REDIRECT_MODE = stringPreferencesKey("redirect_mode")
        val RETENTION = stringPreferencesKey("history_retention")
        val RETAIN_ORIGINALS = booleanPreferencesKey("retain_originals")
        val PIN_PROMPT_SEEN = booleanPreferencesKey("pin_prompt_seen")
        val MOMENT = stringPreferencesKey("share_moment")

        /** The name The Sheep was stored under before it was The Sheep. */
        const val LEGACY_CONFETTI = "CONFETTI"
    }
}
