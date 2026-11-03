package id.iswara.music

import id.iswara.music.model.SearchKind
import id.iswara.music.source.YoutubeMusicSource
import id.iswara.music.playback.StreamResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SourceLiveTest {
    @Test fun searchAndResolveRealAudio() = runBlocking {
        assumeTrue("Set ISWARA_LIVE_TEST=1 for network tests", System.getenv("ISWARA_LIVE_TEST") == "1")
        val source = YoutubeMusicSource()
        val page = source.search("Tulus Hati Hati di Jalan", SearchKind.SONGS)
        assertTrue("Real music results", page.items.isNotEmpty())
        val track = page.items.firstNotNullOf { it.track }
        assertTrue("Artwork is supplied by the source", track.artwork.startsWith("https://"))
        val resolver = StreamResolver(source)
        val start = System.nanoTime()
        val audio = resolver.resolve(track, "medium")
        val resolveMs = (System.nanoTime() - start) / 1_000_000
        val cachedStart = System.nanoTime()
        assertEquals(audio, resolver.resolve(track, "medium"))
        val cachedMs = (System.nanoTime() - cachedStart) / 1_000_000
        assertTrue(audio.url.startsWith("https://"))
        val client = okhttp3.OkHttpClient()
        val downloadStart = System.nanoTime()
        var bytes = 0L
        var firstByteMs = 0L
        client.newCall(okhttp3.Request.Builder().url(audio.url).header("Range", "bytes=0-524287").build()).execute().use {
            assertTrue("Stream HTTP ${it.code}", it.isSuccessful)
            val input = it.body!!.byteStream()
            assertTrue(input.read() >= 0); bytes++
            firstByteMs = (System.nanoTime() - start) / 1_000_000
            val buffer = ByteArray(8192)
            while (bytes < 524288) { val count = input.read(buffer); if (count < 0) break; bytes += count }
        }
        val downloadMs = ((System.nanoTime() - downloadStart) / 1_000_000).coerceAtLeast(1)
        assertTrue("Stream delivered meaningful audio data", bytes >= 65536)
        println("PLAYBACK_METRICS resolveMs=$resolveMs cachedResolveMs=$cachedMs firstByteIncludingResolveMs=$firstByteMs bytes=$bytes downloadMs=$downloadMs bitrateKbps=${audio.bitrate}")
        client.newCall(okhttp3.Request.Builder().url(track.artwork).build()).execute().use {
            assertTrue("Artwork HTTP ${it.code}", it.isSuccessful)
            assertTrue(it.header("Content-Type").orEmpty().startsWith("image/"))
        }
    }
    @Test fun browseRealAlbumAndArtist() = runBlocking {
        assumeTrue(System.getenv("ISWARA_LIVE_TEST") == "1")
        val source = YoutubeMusicSource()
        val albums = source.search("Tulus Manusia", SearchKind.ALBUMS)
        assertTrue("Real album results", albums.items.isNotEmpty())
        val album = albums.items.first()
        val songs = source.collection(album.url)
        assertTrue("Album has playable track metadata", songs.items.any { it.track != null })
        val artists = source.search("Tulus", SearchKind.ARTISTS)
        assertTrue("Artist channels", artists.items.any { it.kind == "artist" })
    }
}
