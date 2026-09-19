/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.data

import android.content.ComponentName
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gswizz.shear.core.TextResult
import dev.gswizz.shear.core.net.ResolveMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeSettingsStore(initial: Settings = Settings()) : SettingsStore {
    val state = MutableStateFlow(initial)
    override val settings = state

    override suspend fun setRedirectMode(mode: ResolveMode) = state.update { it.copy(redirectMode = mode) }

    override suspend fun setRetention(retention: HistoryRetention) = state.update { it.copy(retention = retention) }

    override suspend fun setRetainOriginals(retain: Boolean) = state.update { it.copy(retainOriginals = retain) }

    override suspend fun setPinPromptSeen() = state.update { it.copy(pinPromptSeen = true) }

    private inline fun MutableStateFlow<Settings>.update(f: (Settings) -> Settings) {
        value = f(value)
    }
}

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {
    private lateinit var db: ShearDatabase
    private val settings = FakeSettingsStore()
    private var now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun open() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    ShearDatabase::class.java,
                )
                .build()
    }

    @After fun close() = db.close()

    private fun repository(scope: kotlinx.coroutines.CoroutineScope) =
        HistoryRepository(db.dao(), settings, scope) { now }

    @Test
    fun `records an event with its traces and the original text by default`() = runBlocking {
        val repo = repository(this)
        repo
            .record(
                "e1",
                HistoryFixtures.ORIGINAL,
                HistoryFixtures.textResult,
                ShareStatus.CLEANED,
                settings.state.value,
            )
            .join()
        val loaded = repo.observeEvent("e1").first()!!
        assertEquals(HistoryFixtures.ORIGINAL, loaded.event.originalText)
        assertEquals(HistoryFixtures.CLEANED, loaded.event.cleanedText)
        assertEquals(HistoryRepository.sha256(HistoryFixtures.ORIGINAL), loaded.event.originalHash)
        assertEquals(HistoryFixtures.trace.rulesVersion, loaded.event.rulesVersion)
        assertEquals(now, loaded.event.timestamp)
        assertEquals(1, loaded.traces.size)
        assertEquals(HistoryFixtures.trace.originalUrl, loaded.traces[0].originalUrl)
        assertEquals(HistoryFixtures.trace, loaded.traces[0].result)
        assertNull(loaded.traces[0].failure)
    }

    @Test
    fun `retention off records nothing and deletes what exists`() = runBlocking {
        val repo = repository(this)
        repo.record("e1", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings()).join()
        assertEquals(1, repo.observeEvents().first().size)
        val off = Settings(retention = HistoryRetention.OFF)
        repo.record("e2", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, off).join()
        assertTrue(repo.observeEvents().first().isEmpty())
    }

    @Test
    fun `thirty day retention prunes at the boundary and indefinite never prunes`() = runBlocking {
        val repo = repository(this)
        repo.record("old", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings()).join()
        now += 30 * day
        repo
            .record("edge", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings())
            .join()
        assertEquals(listOf("edge", "old"), repo.observeEvents().first().map { it.id })
        now += 1
        repo.pruneExpired(Settings())
        assertEquals(listOf("edge"), repo.observeEvents().first().map { it.id })
        now += 365 * day
        repo.pruneExpired(Settings(retention = HistoryRetention.INDEFINITE))
        assertEquals(listOf("edge"), repo.observeEvents().first().map { it.id })
        repo.clear()
        assertTrue(repo.observeEvents().first().isEmpty())
    }

    @Test
    fun `without retain originals everything original is dropped but names hashes and finals stay`() = runBlocking {
        val repo = repository(this)
        val result = TextResult(HistoryFixtures.ORIGINAL, HistoryFixtures.CLEANED, listOf(HistoryFixtures.failedTrace))
        repo
            .record(
                "e1",
                HistoryFixtures.ORIGINAL,
                result,
                ShareStatus.RESOLUTION_INCOMPLETE,
                Settings(retainOriginals = false),
            )
            .join()
        val loaded = repo.observeEvent("e1").first()!!
        assertNull(loaded.event.originalText)
        assertEquals(HistoryRepository.sha256(HistoryFixtures.ORIGINAL), loaded.event.originalHash)
        assertEquals(HistoryFixtures.CLEANED, loaded.event.cleanedText)
        val trace = loaded.traces.single()
        assertNull(trace.originalUrl)
        assertEquals(HistoryFixtures.trace.finalUrl, trace.finalUrl)
        assertEquals("TIMEOUT", trace.failure)
        val redacted = trace.result
        assertEquals("https://go.example", redacted.originalUrl)
        assertEquals(HistoryFixtures.trace.finalUrl, redacted.finalUrl)
        assertEquals(listOf("utm_source"), redacted.removedParameters.map { it.token })
        assertEquals(listOf("utm_source"), redacted.removedParameters.map { it.name })
        assertEquals(listOf("utm_source"), redacted.applications[1].removedParameters)
        assertEquals("https://go.example", redacted.redirects.single().fromUrl)
        assertEquals("https://dest.example", redacted.redirects.single().toUrl)
        assertEquals("https://go.example", redacted.applications[0].inputUrl)
        assertEquals(HistoryFixtures.trace.rulesVersion, redacted.rulesVersion)
        assertEquals("https://dest.example", redacted.failure?.atUrl)
    }

    @Test
    fun `a share without urls is an event with no traces`() = runBlocking {
        val repo = repository(this)
        repo
            .record(
                "e1",
                "no links here",
                TextResult("no links here", "no links here", emptyList()),
                ShareStatus.NO_URLS,
                Settings(),
            )
            .join()
        val loaded = repo.observeEvent("e1").first()!!
        assertEquals(ShareStatus.NO_URLS, loaded.event.status)
        assertTrue(loaded.traces.isEmpty())
        assertEquals("", loaded.event.rulesVersion)
    }

    @Test
    fun `the chosen destination attaches to the event`() = runBlocking {
        val repo = repository(this)
        repo.record("e1", HistoryFixtures.ORIGINAL, HistoryFixtures.textResult, ShareStatus.CLEANED, Settings()).join()
        repo.recordDestination("e1", ComponentName("com.messages", "com.messages.Share"), "Messages", now + 5).join()
        val event = repo.observeEvent("e1").first()!!.event
        assertEquals("com.messages", event.destinationPackage)
        assertEquals("com.messages/.Share", event.destinationComponent)
        assertEquals("Messages", event.destinationLabel)
        assertEquals(now + 5, event.destinationTimestamp)
    }
}
