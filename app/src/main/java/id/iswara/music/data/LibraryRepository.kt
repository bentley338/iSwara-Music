package id.iswara.music.data

import id.iswara.music.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(private val dao: DocumentDao) {
    val scope = MutableStateFlow("guest")
    private val lock = Mutex()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val library = scope.flatMapLatest { observe(it).onStart { emit(Library()) } }
    internal fun observe(owner: String): Flow<Library> = dao.observe(owner).map(::decode)
    private fun decode(docs: List<Document>): Library {
        val live = docs.filterNot { it.deleted }
        return Library(
            live.filter { it.kind == "favorite" }.mapNotNull { runCatching { AppJson.decodeFromString<Track>(it.payload) }.getOrNull() },
            live.filter { it.kind == "playlist" }.mapNotNull { runCatching { AppJson.decodeFromString<Playlist>(it.payload) }.getOrNull() },
            live.filter { it.kind == "history" }.take(100).mapNotNull { runCatching { AppJson.decodeFromString<Track>(it.payload) }.getOrNull() },
        )
    }
    suspend fun snapshot(owner: String): Library = decode(dao.all(owner))
    private suspend fun save(id: String, kind: String, payload: String, deleted: Boolean = false) {
        val owner = scope.value
        val previous = dao.get(owner, id)
        dao.put(Document(owner, id, kind, payload, previous?.revision ?: 0, deleted = deleted))
    }
    suspend fun favorite(track: Track) = lock.withLock {
        val old = dao.get(scope.value, "fav:${track.id}")
        save("fav:${track.id}", "favorite", AppJson.encodeToString(track), old != null && !old.deleted)
    }
    suspend fun history(track: Track) = lock.withLock { save("history:${track.id}", "history", AppJson.encodeToString(track)) }
    suspend fun createPlaylist(title: String): Playlist = lock.withLock {
        require(title.trim().isNotEmpty()) { "Nama playlist diperlukan" }
        Playlist(UUID.randomUUID().toString(), title.trim().take(120)).also { save(it.id, "playlist", AppJson.encodeToString(it)) }
    }
    suspend fun createPlaylistWithTracks(title: String, tracks: List<Track>, owner: String): Playlist = lock.withLock {
        require(title.isNotBlank() && tracks.isNotEmpty()) { "Nama playlist dan lagu diperlukan" }
        check(scope.value == owner) { "Akun berubah. Coba lagi." }
        val playlist = Playlist(UUID.randomUUID().toString(), title.trim().take(120), tracks.distinctBy { it.id }.take(500))
        dao.put(Document(owner, playlist.id, "playlist", AppJson.encodeToString(playlist)))
        playlist
    }
    suspend fun rename(playlist: Playlist, title: String) = lock.withLock {
        require(title.isNotBlank()) { "Nama playlist diperlukan" }
        val current = dao.get(scope.value, playlist.id)?.let { AppJson.decodeFromString<Playlist>(it.payload) } ?: playlist
        save(current.id, "playlist", AppJson.encodeToString(current.copy(title = title.trim().take(120))))
    }
    suspend fun add(playlist: Playlist, track: Track) = lock.withLock {
        val current = dao.get(scope.value, playlist.id)?.let { AppJson.decodeFromString<Playlist>(it.payload) } ?: playlist
        save(current.id, "playlist", AppJson.encodeToString(current.copy(tracks = (current.tracks + track).distinctBy { it.id })))
    }
    suspend fun updatePlaylist(playlist: Playlist) = lock.withLock { save(playlist.id, "playlist", AppJson.encodeToString(playlist)) }
    suspend fun delete(playlist: Playlist) = lock.withLock { save(playlist.id, "playlist", AppJson.encodeToString(playlist), true) }
    suspend fun clearHistory() = lock.withLock { dao.all(scope.value).filter { it.kind == "history" && !it.deleted }.forEach { save(it.id, it.kind, it.payload, true) } }
    suspend fun export(): String = AppJson.encodeToString(Backup(library = decode(dao.all(scope.value))))
    suspend fun clearOwner(owner: String) { dao.clear(owner) }
    suspend fun import(raw: String) = lock.withLock {
        val incoming = MusicRules.decodeBackup(raw).library
        incoming.favorites.forEach { save("fav:${it.id}", "favorite", AppJson.encodeToString(it)) }
        incoming.playlists.forEach { p ->
            val copy = p.copy(id = UUID.randomUUID().toString(), title = "${p.title} (impor)")
            save(copy.id, "playlist", AppJson.encodeToString(copy))
        }
        incoming.history.take(100).forEach { save("history:${it.id}", "history", AppJson.encodeToString(it)) }
    }
}
