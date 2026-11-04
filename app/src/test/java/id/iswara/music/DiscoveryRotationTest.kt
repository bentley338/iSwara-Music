package id.iswara.music

import id.iswara.music.model.CatalogItem
import id.iswara.music.model.CatalogPage
import id.iswara.music.model.Library
import id.iswara.music.model.SearchKind
import id.iswara.music.model.Track
import id.iswara.music.source.DiscoveryRotation
import id.iswara.music.source.MusicSource
import id.iswara.music.source.discoveryCandidates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DiscoveryRotationTest {
    private fun song(id: String, artist: String = "Artist $id", title: String = "Song $id", duration: Long = 180) =
        Track(id, title, artist, duration = duration, url = "https://www.youtube.com/watch?v=$id")

    @Test fun repeatedMixUsesUnseenSongsBeforeRecyclingOlderResults() {
        val pool = (1..6).map { song(it.toString()) }
        val rotation = DiscoveryRotation()
        val first = rotation.select(pool, 3, Library())
        val second = rotation.select(pool, 3, Library())
        assertEquals(pool.toSet() - first.toSet(), second.toSet())
        assertEquals(first.toSet(), rotation.select(pool, 3, Library()).toSet())
    }

    @Test fun smallPoolKeepsUsableSongsButChangesItsOpeningTrack() {
        val pool = listOf(song("a"), song("b"))
        val rotation = DiscoveryRotation()
        val first = rotation.select(pool, 15, Library())
        val second = rotation.select(pool, 15, Library())
        assertEquals(first.toSet(), second.toSet())
        assertNotEquals(first.first(), second.first())
    }

    @Test fun selectionRespectsDurationAndAvoidsCopiesFromOtherShelves() {
        val original = song("1", "Nara", "Hujan")
        val duplicate = song("2", "Nara - Topic", "Hujan (Official Audio)")
        val long = song("3", "Nara", "Awan", duration = 250)
        val differentArtist = song("4", "Sari", "Hujan")
        val result = DiscoveryRotation().select(
            listOf(duplicate, long, differentArtist), 12, Library(), "short", blocked = listOf(original),
        )
        assertEquals(listOf(differentArtist), result)
    }

    @Test fun expandedSearchAddsAnotherPageAndRetainsFirstPageIfExpansionFails() = runBlocking {
        val calls = mutableListOf<Pair<String, String?>>()
        var failSecondPage = false
        val source = object : MusicSource {
            override suspend fun search(query: String, kind: SearchKind, next: String?): CatalogPage {
                calls += query to next
                if (next != null && failSecondPage) error("Source unavailable")
                val track = song(if (next == null) "first" else "second")
                return CatalogPage(
                    listOf(CatalogItem(track.id, track.title, track.artist, "", track.url, "song", track)),
                    if (next == null) "cursor" else null,
                )
            }
            override suspend fun collection(url: String, next: String?) = error("Unexpected collection")
            override suspend fun resolve(track: Track, quality: String) = error("Unexpected resolve")
            override suspend fun fromUrl(url: String) = error("Unexpected URL")
            override suspend fun related(track: Track) = error("Unexpected related tracks")
        }
        assertEquals(listOf("first", "second"), source.discoveryCandidates("acoustic songs", true).map { it.id })
        assertEquals(listOf("acoustic songs" to null, "acoustic songs" to "cursor"), calls)
        failSecondPage = true
        assertEquals(listOf("first"), source.discoveryCandidates("acoustic songs", true).map { it.id })
    }
}
