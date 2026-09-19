/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.gswizz.shear.core.Shear
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.net.OkHttpRedirectTransport
import dev.gswizz.shear.core.net.ResolveMode
import dev.gswizz.shear.data.HistoryRepository
import dev.gswizz.shear.data.SettingsRepository
import dev.gswizz.shear.data.SettingsStore
import dev.gswizz.shear.data.ShearDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** The cleaning engine as the app sees it; an interface so tests can script results without touching rules. */
interface ShearEngine {
    val rulesVersion: String
    val rulesHealthy: Boolean

    fun clean(text: String): TextResult

    suspend fun process(text: String, mode: ResolveMode): TextResult

    fun needsNetwork(text: String, mode: ResolveMode): Boolean
}

/** [ShearEngine] backed by `:core`. */
class CoreEngine(private val shear: Shear) : ShearEngine {
    override val rulesVersion: String
        get() = shear.rulesVersion

    override val rulesHealthy: Boolean
        get() = shear.rulesHealthy

    override fun clean(text: String): TextResult = shear.clean(text)

    override suspend fun process(text: String, mode: ResolveMode): TextResult = shear.process(text, mode)

    override fun needsNetwork(text: String, mode: ResolveMode): Boolean = shear.needsNetwork(text, mode)
}

/**
 * Application-scoped object graph, wired by hand.
 *
 * The engine loads rule snapshots and the public suffix list, so it is built off the main thread and awaited by the
 * first caller. [appScope] outlives any activity and runs persistence on one worker so writes stay ordered.
 */
class AppGraph(
    val settings: SettingsStore,
    val history: HistoryRepository,
    private val engineLoader: Deferred<ShearEngine>,
    val appScope: CoroutineScope,
    val defaultDispatcher: CoroutineDispatcher,
) {
    suspend fun engine(): ShearEngine = engineLoader.await()

    companion object {
        private val Context.settingsDataStore by preferencesDataStore(name = "settings")

        fun create(app: Application): AppGraph {
            val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
            val engine = appScope.async(Dispatchers.Default) { CoreEngine(Shear.default(OkHttpRedirectTransport())) }
            val settings = SettingsRepository(app.settingsDataStore)
            return AppGraph(
                settings = settings,
                history = HistoryRepository(ShearDatabase.create(app).dao(), settings, appScope),
                engineLoader = engine,
                appScope = appScope,
                defaultDispatcher = Dispatchers.Default,
            )
        }
    }
}

class ShearApplication : Application() {
    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.create(this)
    }
}
