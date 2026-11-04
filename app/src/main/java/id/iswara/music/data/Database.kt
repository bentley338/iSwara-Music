package id.iswara.music.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "documents", primaryKeys = ["scope", "id"])
data class Document(
    val scope: String,
    val id: String,
    val kind: String,
    val payload: String,
    val revision: Long = 0,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
    val operation: String = java.util.UUID.randomUUID().toString(),
    val modified: Long = System.currentTimeMillis(),
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE scope = :scope ORDER BY modified DESC") fun observe(scope: String): Flow<List<Document>>
    @Query("SELECT * FROM documents WHERE scope = :scope") suspend fun all(scope: String): List<Document>
    @Query("SELECT * FROM documents WHERE scope = :scope AND id = :id") suspend fun get(scope: String, id: String): Document?
    @Upsert suspend fun put(document: Document)
    @Query("DELETE FROM documents WHERE scope = :scope") suspend fun clear(scope: String)
    @Query("UPDATE documents SET revision = :revision, dirty = 0 WHERE scope = :scope AND id = :id AND operation = :operation")
    suspend fun acknowledge(scope: String, id: String, operation: String, revision: Long)
    @Query("UPDATE documents SET revision = :revision WHERE scope = :scope AND id = :id AND revision = :expected")
    suspend fun advanceRevision(scope: String, id: String, expected: Long, revision: Long)
}

@Entity(tableName = "listening_days", primaryKeys = ["owner", "date", "trackId"])
data class ListeningRecord(val owner: String, val date: String, val trackId: String, val track: String, val seconds: Long)

@Dao
abstract class ListeningDao {
    @Query("SELECT * FROM listening_days WHERE owner = :owner ORDER BY date DESC, trackId")
    abstract fun observe(owner: String): Flow<List<ListeningRecord>>
    @Query("SELECT * FROM listening_days WHERE owner = :owner AND date BETWEEN :from AND :through ORDER BY date DESC, trackId")
    abstract suspend fun between(owner: String, from: String, through: String): List<ListeningRecord>
    @Query("SELECT * FROM listening_days WHERE owner = :owner AND date = :date AND trackId = :trackId")
    abstract suspend fun get(owner: String, date: String, trackId: String): ListeningRecord?
    @Upsert abstract suspend fun put(record: ListeningRecord)
    @Query("DELETE FROM listening_days WHERE owner = :owner") abstract suspend fun clear(owner: String)
    @Query("DELETE FROM listening_days WHERE owner = :owner AND date < :before") abstract suspend fun prune(owner: String, before: String)
    @Transaction open suspend fun increment(record: ListeningRecord) {
        val old = get(record.owner, record.date, record.trackId)
        put(record.copy(seconds = (old?.seconds ?: 0) + record.seconds))
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `listening_days` (`owner` TEXT NOT NULL, `date` TEXT NOT NULL, `trackId` TEXT NOT NULL, `track` TEXT NOT NULL, `seconds` INTEGER NOT NULL, PRIMARY KEY(`owner`, `date`, `trackId`))")
    }
}
@Entity(tableName = "saved_queues", primaryKeys = ["owner", "id"])
data class SavedQueueRow(val owner: String, val id: String, val title: String, val payload: String, val modified: Long)
@Entity(tableName = "lyrics_cache", primaryKeys = ["owner", "id"])
data class LyricsCacheRow(val owner: String, val id: String, val metadata: String, val payload: String, val manual: Boolean, val modified: Long)
@Dao interface ExperienceDao {
    @Query("SELECT * FROM saved_queues WHERE owner = :owner ORDER BY modified DESC") fun queues(owner: String): Flow<List<SavedQueueRow>>
    @Query("SELECT * FROM saved_queues WHERE owner = :owner AND id = :id") suspend fun queue(owner: String, id: String): SavedQueueRow?
    @Upsert suspend fun saveQueue(row: SavedQueueRow)
    @Query("DELETE FROM saved_queues WHERE owner = :owner AND id = :id") suspend fun deleteQueue(owner: String, id: String)
    @Query("DELETE FROM saved_queues WHERE owner = :owner") suspend fun clearQueues(owner: String)
    @Query("SELECT * FROM lyrics_cache WHERE owner = :owner AND id = :id") suspend fun lyrics(owner: String, id: String): LyricsCacheRow?
    @Query("SELECT * FROM lyrics_cache WHERE owner = :owner ORDER BY modified DESC LIMIT 500") suspend fun recentLyrics(owner: String): List<LyricsCacheRow>
    @Upsert suspend fun saveLyrics(row: LyricsCacheRow)
    @Query("DELETE FROM lyrics_cache WHERE owner = :owner AND id = :id") suspend fun deleteLyrics(owner: String, id: String)
    @Query("DELETE FROM lyrics_cache WHERE owner = :owner") suspend fun clearLyrics(owner: String)
    @Query("DELETE FROM lyrics_cache WHERE owner = :owner AND manual = 0 AND id NOT IN (SELECT id FROM lyrics_cache WHERE owner = :owner AND manual = 0 ORDER BY modified DESC LIMIT 100)") suspend fun pruneLyrics(owner: String)
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `saved_queues` (`owner` TEXT NOT NULL, `id` TEXT NOT NULL, `title` TEXT NOT NULL, `payload` TEXT NOT NULL, `modified` INTEGER NOT NULL, PRIMARY KEY(`owner`, `id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `lyrics_cache` (`owner` TEXT NOT NULL, `id` TEXT NOT NULL, `metadata` TEXT NOT NULL, `payload` TEXT NOT NULL, `manual` INTEGER NOT NULL, `modified` INTEGER NOT NULL, PRIMARY KEY(`owner`, `id`))")
    }
}
@Database(entities = [Document::class, ListeningRecord::class, SavedQueueRow::class, LyricsCacheRow::class], version = 3, exportSchema = true)
abstract class IswaraDatabase : RoomDatabase() {
    abstract fun documents(): DocumentDao
    abstract fun listening(): ListeningDao
    abstract fun experience(): ExperienceDao
}
