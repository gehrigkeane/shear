/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gswizz.shear.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.gswizz.shear.core.engine.UrlCleanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/** What happened to a share, from the app's point of view. */
enum class ShareStatus {
    CLEANED,
    UNCHANGED,
    NO_URLS,
    RULES_UNAVAILABLE,
    RESOLUTION_INCOMPLETE,
    CANCELLED,
}

/**
 * One trip through Shear.
 *
 * [originalText] is null when the user chose not to retain originals; [originalHash] is always present so a share can
 * still be recognized. Destination columns fill in later, when and if the chooser reports what the user picked.
 */
@Entity(tableName = "share_event", indices = [Index("timestamp")])
data class ShareEventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val originalText: String?,
    val originalHash: String,
    val cleanedText: String,
    val status: ShareStatus,
    val rulesVersion: String,
    val destinationPackage: String? = null,
    val destinationComponent: String? = null,
    val destinationLabel: String? = null,
    val destinationTimestamp: Long? = null,
)

/** The full trace for one URL in a share; [result] carries every rule application and redirect step as JSON. */
@Entity(
    tableName = "url_trace",
    foreignKeys =
        [
            ForeignKey(
                entity = ShareEventEntity::class,
                parentColumns = ["id"],
                childColumns = ["shareEventId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("shareEventId")],
)
data class UrlTraceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shareEventId: String,
    val position: Int,
    val originalUrl: String?,
    val finalUrl: String,
    val result: UrlCleanResult,
    val failure: String?,
)

/** An event with its traces in share order. */
data class EventWithTraces(
    @Embedded val event: ShareEventEntity,
    @Relation(parentColumn = "id", entityColumn = "shareEventId") val unorderedTraces: List<UrlTraceEntity>,
) {
    val traces: List<UrlTraceEntity>
        get() = unorderedTraces.sortedBy { it.position }
}

/** JSON columns for the trace model, which already carries kotlinx-serialization descriptors. */
class JsonConverters {
    @TypeConverter
    fun fromResult(result: UrlCleanResult): String = json.encodeToString(UrlCleanResult.serializer(), result)

    @TypeConverter fun toResult(text: String): UrlCleanResult = json.decodeFromString(UrlCleanResult.serializer(), text)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Dao
abstract class ShareHistoryDao {
    @Insert abstract suspend fun insertEvent(event: ShareEventEntity)

    @Insert abstract suspend fun insertTraces(traces: List<UrlTraceEntity>)

    @Query("DELETE FROM share_event WHERE originalHash = :hash") abstract suspend fun deleteByHash(hash: String)

    /** Inserts the event and its traces after removing any earlier share of the same text, so one row per text. */
    @Transaction
    open suspend fun replace(event: ShareEventEntity, traces: List<UrlTraceEntity>) {
        deleteByHash(event.originalHash)
        insert(event, traces)
    }

    @Transaction
    open suspend fun insert(event: ShareEventEntity, traces: List<UrlTraceEntity>) {
        insertEvent(event)
        insertTraces(traces)
    }

    @Query(
        "UPDATE share_event SET destinationPackage = :pkg, destinationComponent = :component, destinationLabel = :label, " +
            "destinationTimestamp = :timestamp WHERE id = :id"
    )
    abstract suspend fun setDestination(
        id: String,
        pkg: String,
        component: String,
        label: String?,
        timestamp: Long,
    ): Int

    @Query("SELECT * FROM share_event ORDER BY timestamp DESC LIMIT 1000")
    abstract fun observeEvents(): Flow<List<ShareEventEntity>>

    @Transaction
    @Query("SELECT * FROM share_event WHERE id = :id")
    abstract fun observeEvent(id: String): Flow<EventWithTraces?>

    @Query("DELETE FROM share_event WHERE timestamp < :cutoff") abstract suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM share_event") abstract suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM url_trace") abstract suspend fun traceCount(): Int
}

@Database(entities = [ShareEventEntity::class, UrlTraceEntity::class], version = 1, exportSchema = true)
@TypeConverters(JsonConverters::class)
abstract class ShearDatabase : RoomDatabase() {
    abstract fun dao(): ShareHistoryDao

    companion object {
        fun create(context: Context): ShearDatabase =
            Room.databaseBuilder(context, ShearDatabase::class.java, "shear.db").build()
    }
}
