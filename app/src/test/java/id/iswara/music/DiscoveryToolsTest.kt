package id.iswara.music

import id.iswara.music.model.LyricLine
import id.iswara.music.model.Track
import id.iswara.music.playback.ListeningTaste
import id.iswara.music.playback.QueueRules
import id.iswara.music.playback.freshShuffleOrder
import id.iswara.music.source.LyricCandidate
import id.iswara.music.source.Lyrics
import id.iswara.music.source.LyricsRules
import id.iswara.music.source.SongRecognition
import org.junit.Assert.*
import org.junit.Test

class DiscoveryToolsTest {
    private fun track(id: String, title: String, artist: String, duration: Long = 180) =
        Track(id, title, artist, duration = duration, url = "https://www.youtube.com/watch?v=$id")

    @Test fun snoozeCatchesAlternateUploadAndExpires() {
        val original = track("original", "Hujan", "Ari")
        val duplicate = track("other-upload", "Hujan (Official Audio)", "Ari - Topic")
        val otherArtist = track("new", "Hujan", "Dara")
        val until = System.currentTimeMillis() + 60_000
        val taste = ListeningTaste(mapOf(ListeningTaste.recordingKey(original) to until))
        assertTrue(taste.hidden(duplicate, until - 1))
        assertFalse(taste.hidden(duplicate, until))
        val seed = track("seed", "Senja", "Sari")
        assertEquals(listOf(otherArtist), QueueRules.smart(seed, emptyList(), listOf(duplicate, otherArtist), tasteRules = taste.rules))
    }

    @Test fun shuffleKeepsAllIndicesAndRecentRecordingsMoveBehindFreshPicks() {
        val played = track("played", "Laut", "Ari")
        val songs = listOf(played, track("a", "Hujan", "Ari"), track("b", "Awan", "Dara"), track("c", "Sore", "Ari"))
        val order = freshShuffleOrder(songs, listOf(played), previousArtist = "ari")
        assertEquals(songs.indices.toSet(), order.toSet())
        assertEquals(2, order.first())
        assertEquals(0, order.last())
    }

    @Test fun featuredArtistAndUploadLabelsMatchButDifferentTimingBecomesPlain() {
        val track = track("a", "Hujan (feat. Dara) - Official Music Video", "Ari - Topic")
        val candidate = LyricCandidate("1", "Hujan", "Ari", 189, "lrclib")
        assertTrue(LyricsRules.score(track, candidate) >= .82)
        assertEquals(0.0, LyricsRules.score(track, candidate.copy(artist = "Unknown Cover Band")), 0.0)
        val timed = Lyrics(listOf(LyricLine(1_000, "Test line")))
        assertEquals(timed, LyricsRules.fitTiming(track, candidate.copy(duration = 180), timed))
        assertEquals(Lyrics(plain = "Test line"), LyricsRules.fitTiming(track, candidate, timed))
    }

    @Test fun recognitionReturnsNoMatchAndRejectsProviderErrors() {
        assertNull(SongRecognition.parse("""{"status":"success","result":null}"""))
        assertEquals("Senja", SongRecognition.parse("""{"status":"success","result":{"title":"Senja","artist":"Ari"}}""")?.title)
        assertTrue(runCatching { SongRecognition.parse("""{"status":"error","error":{"error_code":900}}""") }.isFailure)
    }
}
