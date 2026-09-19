/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.gswizz.shear.ui.EventDetailRoute
import dev.gswizz.shear.ui.HistoryRoute
import dev.gswizz.shear.ui.SettingsRoute
import dev.gswizz.shear.ui.SettingsUiState
import dev.gswizz.shear.ui.ShearTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HistoryKey : NavKey

@Serializable data class EventDetailKey(val eventId: String) : NavKey

@Serializable data object SettingsKey : NavKey

/**
 * Entry point when Shear is opened directly from the launcher: History, event detail, and Settings.
 *
 * Opening the app is also one of the two moments retention is enforced, the other being every share.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as ShearApplication).graph
        graph.appScope.launch { graph.history.pruneExpired() }
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            ShearTheme {
                ShearApp(
                    graph = graph,
                    appVersion = appVersion,
                    onOpenUpstream = { startActivity(Intent(Intent.ACTION_VIEW, SettingsUiState.UPSTREAM.toUri())) },
                )
            }
        }
    }
}

@Composable
private fun ShearApp(graph: AppGraph, appVersion: String, onOpenUpstream: () -> Unit, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HistoryKey)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider =
            entryProvider {
                entry<HistoryKey> {
                    HistoryRoute(
                        graph = graph,
                        onOpen = { backStack.add(EventDetailKey(it)) },
                        onSettings = { backStack.add(SettingsKey) },
                    )
                }
                entry<EventDetailKey> { key ->
                    EventDetailRoute(graph = graph, eventId = key.eventId, onBack = { backStack.removeLastOrNull() })
                }
                entry<SettingsKey> {
                    SettingsRoute(
                        graph = graph,
                        appVersion = appVersion,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenUpstream = onOpenUpstream,
                    )
                }
            },
    )
}
