package id.iswara.music

import id.iswara.music.data.*
import id.iswara.music.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class LibraryRepositoryTest {
    private val dao = MemoryDao()
    private val repository = LibraryRepository(dao)
    private val song = Track("test-song", "Test fixture", "Test artist", url = "https://www.youtube.com/watch?v=test-song")
    @Test fun backupExportsActualDataWithoutWaitingForObserver() = runTest {
        repository.favorite(song)
        val p = repository.createPlaylist("Perjalanan")
        repository.add(p, song)
        val backup = MusicRules.decodeBackup(repository.export())
        assertEquals(listOf(song), backup.library.favorites)
        assertEquals(listOf(song), backup.library.playlists.single().tracks)
    }
    @Test fun guestAndTwoAccountsStayIsolated() = runTest {
        repository.favorite(song)
        repository.scope.value = "account-a"
        assertTrue(MusicRules.decodeBackup(repository.export()).library.favorites.isEmpty())
        repository.createPlaylist("A")
        repository.scope.value = "account-b"
        assertTrue(MusicRules.decodeBackup(repository.export()).library.playlists.isEmpty())
        repository.scope.value = "guest"
        assertEquals(listOf(song), MusicRules.decodeBackup(repository.export()).library.favorites)
    }
    @Test fun favoriteRemovalLeavesSyncTombstone() = runTest {
        repository.favorite(song)
        val first = dao.all("guest").single()
        dao.put(first.copy(revision = 7, dirty = false))
        repository.favorite(song)
        val removed = dao.all("guest").single()
        assertTrue(removed.deleted)
        assertTrue(removed.dirty)
        assertEquals(7L, removed.revision)
        assertNotEquals(first.operation, removed.operation)
        assertTrue(MusicRules.decodeBackup(repository.export()).library.favorites.isEmpty())
    }
    @Test fun stalePlaylistObjectCannotOverwriteNewlyAddedTrack() = runTest {
        val p = repository.createPlaylist("Campuran")
        repository.add(p, song)
        repository.add(p, song.copy(id = "second", title = "Second test fixture"))
        assertEquals(listOf("test-song", "second"), MusicRules.decodeBackup(repository.export()).library.playlists.single().tracks.map { it.id })
    }
    @Test fun importedPlaylistDoesNotOverwriteExistingId() = runTest {
        val p = repository.createPlaylist("Asli")
        repository.import(AppJson.encodeToString(Backup(library = Library(playlists = listOf(p.copy(title = "Backup"))))))
        val playlists = MusicRules.decodeBackup(repository.export()).library.playlists
        assertEquals(2, playlists.size)
        assertEquals(2, playlists.map { it.id }.distinct().size)
        assertTrue(playlists.any { it.title == "Asli" })
    }
    @Test fun mixPlaylistIsOneOwnerBoundDocumentWithoutDuplicateTracks() = runTest {
        repository.scope.value = "mix-owner"
        val playlist = repository.createPlaylistWithTracks("  Sore  ", listOf(song, song, song.copy(id = "another")), "mix-owner")
        assertEquals("Sore", playlist.title)
        assertEquals(2, playlist.tracks.size)
        assertEquals(1, dao.all("mix-owner").size)
        assertTrue(dao.all("mix-owner").single().dirty)
        repository.scope.value = "different-owner"
        assertTrue(runCatching { repository.createPlaylistWithTracks("Wrong", listOf(song), "mix-owner") }.isFailure)
        assertTrue(dao.all("different-owner").isEmpty())
        assertEquals(listOf(playlist), repository.snapshot("mix-owner").playlists)
    }
    private class MemoryDao : DocumentDao {
        private val rows = MutableStateFlow<Map<Pair<String, String>, Document>>(emptyMap())
        override fun observe(scope: String) = rows.map { it.values.filter { d -> d.scope == scope }.sortedByDescending { d -> d.modified } }
        override suspend fun all(scope: String) = rows.value.values.filter { it.scope == scope }
        override suspend fun get(scope: String, id: String) = rows.value[scope to id]
        override suspend fun put(document: Document) { rows.value = rows.value + ((document.scope to document.id) to document) }
        override suspend fun clear(scope: String) { rows.value = rows.value.filterKeys { it.first != scope } }
        override suspend fun acknowledge(scope: String, id: String, operation: String, revision: Long) { get(scope, id)?.takeIf { it.operation == operation }?.let { put(it.copy(revision = revision, dirty = false)) } }
        override suspend fun advanceRevision(scope: String, id: String, expected: Long, revision: Long) { get(scope, id)?.takeIf { it.revision == expected }?.let { put(it.copy(revision = revision)) } }
    }
}
