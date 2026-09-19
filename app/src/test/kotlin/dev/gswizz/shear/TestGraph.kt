/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.data.DestinationResolver
import dev.gswizz.shear.data.FakeSettingsStore
import dev.gswizz.shear.data.HistoryRepository
import dev.gswizz.shear.data.Settings
import dev.gswizz.shear.data.ShearDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A scriptable engine: `results[mode]` is returned by `process`, `results[OFF]` by `clean`. */
class FakeEngine(
    private val results: Map<ResolveMode, (String) -> TextResult>,
    private val networkNeeded: Boolean = false,
    override val rulesHealthy: Boolean = true,
    override val rulesVersion: String = "test-rules",
) : ShearEngine {
    val processed = mutableListOf<ResolveMode>()

    override fun clean(text: String): TextResult = results.getValue(ResolveMode.OFF)(text)

    override suspend fun process(text: String, mode: ResolveMode): TextResult {
        processed += mode
        return (results[mode] ?: results.getValue(ResolveMode.OFF))(text)
    }

    override fun needsNetwork(text: String, mode: ResolveMode): Boolean = mode != ResolveMode.OFF && networkNeeded

    companion object {
        /** An engine whose offline pass replaces [from] with [to] in the text. */
        fun replacing(from: String, to: String, healthy: Boolean = true, networkNeeded: Boolean = false) =
            FakeEngine(
                mapOf(
                    ResolveMode.OFF to
                        { text: String ->
                            TextResult(
                                text,
                                text.replace(from, to),
                                listOf(
                                    dev.gswizz.shear.data.HistoryFixtures.trace.copy(originalUrl = from, finalUrl = to)
                                ),
                            )
                        }
                ),
                networkNeeded = networkNeeded,
                rulesHealthy = healthy,
            )

        /** An engine that finds no URLs. */
        fun noUrls() = FakeEngine(mapOf(ResolveMode.OFF to { text: String -> TextResult(text, text, emptyList()) }))
    }
}

/** Installs an [AppGraph] built from fakes and an in-memory database into the Robolectric application. */
object TestGraph {
    fun install(
        engine: ShearEngine,
        settings: Settings = Settings(),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
    ): Installed {
        val app = ApplicationProvider.getApplicationContext<Context>() as ShearApplication
        val db = Room.inMemoryDatabaseBuilder(app, ShearDatabase::class.java).build()
        val store = FakeSettingsStore(settings)
        val history = HistoryRepository(db.dao(), store, scope)
        val graph =
            AppGraph(
                store,
                history,
                CompletableDeferred(engine),
                scope,
                Dispatchers.Unconfined,
                DestinationResolver(app.packageManager),
            )
        app.graph = graph
        return Installed(graph, store, history, db)
    }

    class Installed(
        val graph: AppGraph,
        val settings: FakeSettingsStore,
        val history: HistoryRepository,
        val db: ShearDatabase,
    )
}
