package id.iswara.music.data

import id.iswara.music.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class ListeningDay(val date: String, val track: Track, val seconds: Long = 0)
data class ListeningSummary(val seconds: Long, val artists: List<Pair<String, Long>>, val tracks: List<Pair<Track, Long>>)
fun summarizeListening(entries: List<ListeningDay>, prefix: String): ListeningSummary {
    val matching = entries.filter { it.date.startsWith(prefix) }
    return ListeningSummary(matching.sumOf { it.seconds }, matching.groupBy { it.track.artist }.map { it.key to it.value.sumOf { e -> e.seconds } }.sortedByDescending { it.second }.take(5),
        matching.groupBy { it.track.id }.values.map { it.first().track to it.sumOf { e -> e.seconds } }.sortedByDescending { it.second }.take(5))
}
@Singleton class ListeningStats @Inject constructor(private val preferences: Preferences, private val library: LibraryRepository, private val database: IswaraDatabase) {
    private val mutex = Mutex()
    private val migrated = mutableSetOf<String>()
    private val deletedOwners = mutableSetOf<String>()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries = library.scope.flatMapLatest { owner -> flow {
        migrate(owner)
        emitAll(database.listening().observe(owner).map { rows -> rows.mapNotNull { row ->
            runCatching { ListeningDay(row.date, AppJson.decodeFromString<Track>(row.track), row.seconds) }.getOrNull()
        } })
    }.flowOn(Dispatchers.IO) }
    private suspend fun migrate(owner: String) = mutex.withLock {
        if (owner in migrated) return@withLock
        val key = "stats:$owner"
        val legacy = preferences.raw(key).first()
        val entries = runCatching { AppJson.decodeFromString<List<ListeningDay>>(legacy ?: "[]") }.getOrDefault(emptyList())
        database.withTransaction {
            entries.filter { it.seconds > 0 }.forEach { entry ->
                val old = database.listening().get(owner, entry.date, entry.track.id)
                database.listening().put(ListeningRecord(owner, entry.date, entry.track.id, AppJson.encodeToString(entry.track), maxOf(entry.seconds, old?.seconds ?: 0)))
            }
        }
        database.listening().prune(owner, LocalDate.now().minusYears(2).toString())
        if (!legacy.isNullOrBlank()) preferences.set(key, "[]")
        migrated += owner
    }
    suspend fun record(owner: String, track: Track, seconds: Long) = withContext(Dispatchers.IO) {
        if (seconds <= 0) return@withContext
        migrate(owner)
        mutex.withLock {
            if (owner !in deletedOwners) database.listening().increment(ListeningRecord(owner, LocalDate.now().toString(), track.id, AppJson.encodeToString(track), seconds.coerceAtMost(30)))
        }
    }
    suspend fun clearOwner(owner: String, deleting: Boolean = false) = withContext(Dispatchers.IO) { mutex.withLock {
        if (deleting) deletedOwners += owner
        database.listening().clear(owner); preferences.set("stats:$owner", "[]"); migrated += owner
    } }
    suspend fun prune(owner: String) = withContext(Dispatchers.IO) {
        database.listening().prune(owner, LocalDate.now().minusYears(2).toString())
    }
}
