package id.iswara.music.data

import id.iswara.music.model.*
import id.iswara.music.playback.QueueRules
import id.iswara.music.source.LyricsRules
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
data class OwnedListening(val owner: String = "", val entries: List<ListeningDay> = emptyList())

fun summarizeListening(entries: List<ListeningDay>, prefix: String, limit: Int = 5): ListeningSummary {
    val matching = entries.filter { it.seconds > 0 && it.date.startsWith(prefix) }.sortedByDescending { it.date }
    val artists = matching.groupBy { QueueRules.artistKey(it.track.artist) }.filterKeys { it.isNotBlank() }
        .values.map { rows -> LyricsRules.cleanArtist(rows.first().track.artist) to rows.sumOf { it.seconds } }
        .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }).take(limit)
    val tracks = matching.groupBy {
        val recording = QueueRules.Recording(it.track)
        val key = recording.identity
        val identity = if (key.first.isBlank() || key.second.isBlank()) "" to it.track.id else key
        identity to recording.variants
    }.values.map { rows -> rows.first().track to rows.sumOf { it.seconds } }
        .sortedWith(compareByDescending<Pair<Track, Long>> { it.second }.thenBy { it.first.id }).take(limit)
    return ListeningSummary(matching.sumOf { it.seconds }, artists, tracks)
}
@Singleton class ListeningStats @Inject constructor(private val preferences: Preferences, private val library: LibraryRepository, private val database: IswaraDatabase) {
    private val mutex = Mutex()
    private val migrated = mutableSetOf<String>()
    private val deletedOwners = mutableSetOf<String>()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val scopedEntries = library.scope.flatMapLatest { owner -> flow {
        migrate(owner)
        emitAll(database.listening().observe(owner).map { rows -> OwnedListening(owner, rows.mapNotNull(::decode)) })
    }.flowOn(Dispatchers.IO) }
    val entries = scopedEntries.map { it.entries }
    private fun decode(row: ListeningRecord): ListeningDay? =
        runCatching { ListeningDay(row.date, AppJson.decodeFromString<Track>(row.track), row.seconds) }.getOrNull()

    suspend fun topArtistKeys(owner: String): Set<String> = withContext(Dispatchers.IO) {
        val now = LocalDate.now()
        val rows = database.listening().between(owner, now.minusDays(29).toString(), now.toString())
        rows.mapNotNull(::decode).groupBy { QueueRules.artistKey(it.track.artist) }.filterKeys { it.isNotBlank() }
            .entries.sortedByDescending { (_, entries) -> entries.sumOf { it.seconds } }
            .take(8).mapTo(hashSetOf()) { it.key }
    }
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
