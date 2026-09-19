/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareHistoryDaoTest {
    private lateinit var db: ShearDatabase
    private lateinit var dao: ShareHistoryDao

    @Before
    fun open() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    ShearDatabase::class.java,
                )
                .build()
        dao = db.dao()
    }

    @After fun close() = db.close()

    private fun event(id: String, timestamp: Long) =
        ShareEventEntity(
            id = id,
            timestamp = timestamp,
            originalText = HistoryFixtures.ORIGINAL,
            originalHash = "hash-$id",
            cleanedText = HistoryFixtures.CLEANED,
            status = ShareStatus.CLEANED,
            rulesVersion = "v",
        )

    private fun trace(eventId: String, position: Int = 0) =
        UrlTraceEntity(
            shareEventId = eventId,
            position = position,
            originalUrl = HistoryFixtures.trace.originalUrl,
            finalUrl = HistoryFixtures.trace.finalUrl,
            result = HistoryFixtures.trace,
            failure = null,
        )

    @Test
    fun `an event and its traces round trip including the json trace`() = runBlocking {
        dao.insert(event("e1", 1_000), listOf(trace("e1", 1), trace("e1", 0)))
        val loaded = dao.observeEvent("e1").first()!!
        assertEquals("e1", loaded.event.id)
        assertEquals(ShareStatus.CLEANED, loaded.event.status)
        assertEquals(listOf(0, 1), loaded.traces.map { it.position })
        assertEquals(HistoryFixtures.trace, loaded.traces[0].result)
        assertNull(dao.observeEvent("missing").first())
    }

    @Test
    fun `setting the destination touches exactly the matching row`() = runBlocking {
        dao.insert(event("e1", 1_000), emptyList())
        assertEquals(1, dao.setDestination("e1", "com.messages", "com.messages/.Share", "Messages", 2_000))
        assertEquals(0, dao.setDestination("nope", "x", "x/.Y", null, 2_000))
        val loaded = dao.observeEvent("e1").first()!!.event
        assertEquals("Messages", loaded.destinationLabel)
        assertEquals("com.messages/.Share", loaded.destinationComponent)
        assertEquals(2_000L, loaded.destinationTimestamp)
    }

    @Test
    fun `events list newest first and pruning cascades to traces`() = runBlocking {
        dao.insert(event("old", 1_000), listOf(trace("old")))
        dao.insert(event("new", 3_000), listOf(trace("new")))
        assertEquals(listOf("new", "old"), dao.observeEvents().first().map { it.id })
        assertEquals(2, dao.traceCount())
        assertEquals(1, dao.deleteOlderThan(2_000))
        assertEquals(listOf("new"), dao.observeEvents().first().map { it.id })
        assertEquals(1, dao.traceCount())
        dao.deleteAll()
        assertEquals(0, dao.traceCount())
    }
}
