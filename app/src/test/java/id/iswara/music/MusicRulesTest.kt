package id.iswara.music

import id.iswara.music.model.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class MusicRulesTest {
    private fun track(id: String) = Track(id, "Lagu $id", "Artis", url = "https://www.youtube.com/watch?v=$id")
    @Test fun reorderingPreservesAllTracks() {
        val tracks = listOf(track("a"), track("b"), track("c"))
        assertEquals(listOf("b", "c", "a"), MusicRules.move(tracks, 0, 2).map { it.id })
        assertEquals(tracks, MusicRules.move(tracks, -1, 2))
        assertEquals(tracks, MusicRules.move(tracks, 0, 3))
    }
    @Test fun backupRoundtripPreservesPlaylistOrder() {
        val library = Library(listOf(track("a")), listOf(Playlist("p", "Perjalanan", listOf(track("b"), track("a")))))
        assertEquals(library, MusicRules.decodeBackup(AppJson.encodeToString(Backup(library = library))).library)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownBackupVersion() { MusicRules.decodeBackup("{\"version\":99,\"library\":{}}") }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnsafeFileUrls() {
        MusicRules.decodeBackup(AppJson.encodeToString(Backup(library = Library(favorites = listOf(track("a").copy(url = "file:///data/private"))))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicatePlaylistIds() {
        MusicRules.decodeBackup(AppJson.encodeToString(Backup(library = Library(playlists = listOf(Playlist("a", "Satu"), Playlist("a", "Dua"))))))
    }
    @Test fun parsesMultipleLyricStampsAndOrdersLines() {
        val lines = MusicRules.parseLyrics("[00:20.50]Dua\n[00:05.00][00:10.25]Satu\n[ar:Artis]")
        assertEquals(listOf(5000L, 10250L, 20500L), lines.map { it.time })
        assertEquals(listOf("Satu", "Satu", "Dua"), lines.map { it.text })
    }
    @Test fun ignoresUnstampedLyricMetadata() { assertEquals(emptyList<LyricLine>(), MusicRules.parseLyrics("[ar:Artis]\nLirik biasa")) }
    @Test fun backupReaderAcceptsExactByteLimit() { assertEquals("abc", BackupReader.read("abc".byteInputStream(), 3)) }
    @Test(expected = IllegalArgumentException::class) fun backupReaderRejectsOversizeBeforeLoadingWholeFile() { BackupReader.read("abcdef".byteInputStream(), 3) }
}
