/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.data

import android.content.ComponentName
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.engine.UrlCleanResult
import dev.gswizz.shear.core.url.UrlParts
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The on-device share history.
 *
 * Writes run on [scope], the application's single ordered worker, so the share path never waits on the database and a
 * destination callback can never overtake the insert it belongs to. Retention is enforced after every write and on
 * demand; there is no background job.
 */
class HistoryRepository(
    private val dao: ShareHistoryDao,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Records a share unless retention is off, redacting originals when the user asked for that, then prunes.
     *
     * History keeps one row per shared text: sharing the same text again replaces the earlier row, so the newest time
     * and destination win.
     */
    fun record(
        eventId: String,
        originalText: String,
        result: TextResult,
        status: ShareStatus,
        snapshot: Settings,
    ): Job = scope.launch {
        if (snapshot.retention != HistoryRetention.OFF) {
            val retain = snapshot.retainOriginals
            val event =
                ShareEventEntity(
                    id = eventId,
                    timestamp = clock(),
                    originalText = if (retain) originalText else null,
                    originalHash = sha256(originalText),
                    cleanedText = result.outputText,
                    status = status,
                    rulesVersion = result.urls.firstOrNull()?.rulesVersion ?: "",
                )
            val traces =
                result.urls.mapIndexed { position, url ->
                    UrlTraceEntity(
                        shareEventId = eventId,
                        position = position,
                        originalUrl = if (retain) url.originalUrl else null,
                        finalUrl = url.finalUrl,
                        result = if (retain) url else redact(url),
                        failure = url.failure?.kind?.name,
                    )
                }
            dao.replace(event, traces)
        }
        pruneExpired(snapshot)
    }

    /** Attaches the destination the chooser reported. Silently a no-op when the event was never stored. */
    fun recordDestination(eventId: String, component: ComponentName, label: String?, timestamp: Long): Job =
        scope.launch {
            dao.setDestination(eventId, component.packageName, component.flattenToShortString(), label, timestamp)
        }

    /** Deletes what the retention setting no longer allows; OFF deletes everything. */
    suspend fun pruneExpired(snapshot: Settings? = null) {
        when ((snapshot ?: settings.settings.first()).retention) {
            HistoryRetention.OFF -> dao.deleteAll()
            HistoryRetention.DAYS_30 -> dao.deleteOlderThan(clock() - THIRTY_DAYS_MS)
            HistoryRetention.INDEFINITE -> Unit
        }
    }

    suspend fun clear() = dao.deleteAll()

    fun observeEvents(): Flow<List<ShareEventEntity>> = dao.observeEvents()

    fun observeEvent(id: String): Flow<EventWithTraces?> = dao.observeEvent(id)

    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        /**
         * Strips everything original from a trace: URLs collapse to their origin, removed parameters keep only their
         * names, and the final URL, rule identities, and version survive.
         */
        fun redact(result: UrlCleanResult): UrlCleanResult =
            result.copy(
                originalUrl = origin(result.originalUrl),
                applications =
                    result.applications.map { app ->
                        app.copy(
                            inputUrl = origin(app.inputUrl),
                            outputUrl = origin(app.outputUrl),
                            removedParameters = app.removedParameters.map { it.substringBefore('=') },
                        )
                    },
                removedParameters = result.removedParameters.map { it.copy(token = it.name) },
                redirects = result.redirects.map { it.copy(fromUrl = origin(it.fromUrl), toUrl = origin(it.toUrl)) },
                failure = result.failure?.let { it.copy(atUrl = origin(it.atUrl), detail = null) },
            )

        private fun origin(url: String): String =
            UrlParts.parse(url)?.let { "${it.scheme.lowercase()}://${it.hostLower}" } ?: ""
    }
}
