package id.iswara.music

import id.iswara.music.model.*
import id.iswara.music.playback.StreamResolver
import id.iswara.music.source.MusicSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class StreamResolverTest {
    private val track = Track("a", "Audio", url = "https://www.youtube.com/watch?v=abcdefghijk")
    private class Source : MusicSource {
        val calls = AtomicInteger()
        val fallbacks = AtomicInteger()
        var fail = false
        var expired = false
        override suspend fun resolve(track: Track, quality: String): ResolvedAudio {
            calls.incrementAndGet(); delay(40)
            if (fail) throw java.io.IOException("offline")
            return ResolvedAudio("https://audio.test/$quality?expire=${if (expired) 1 else System.currentTimeMillis() / 1000 + 3600}")
        }
        override suspend fun resolveFallback(track: Track, quality: String): ResolvedAudio { fallbacks.incrementAndGet(); return ResolvedAudio("https://audio.test/fallback") }
        override suspend fun search(query: String, kind: SearchKind, next: String?) = CatalogPage(emptyList())
        override suspend fun collection(url: String, next: String?) = CatalogPage(emptyList())
        override suspend fun fromUrl(url: String) = error("unused")
        override suspend fun related(track: Track) = emptyList<Track>()
    }
    @Test fun simultaneousPrefetchAndPlaybackShareOneRequest() = runBlocking {
        val source = Source(); val resolver = StreamResolver(source)
        val audio = List(10) { async { resolver.resolve(track, "medium") } }.awaitAll()
        assertEquals(1, source.calls.get()); assertEquals(1, audio.distinct().size)
        resolver.resolve(track, "medium"); assertEquals(1, source.calls.get())
    }
    @Test fun expiredUrlsAreResolvedAgainAndQualityIsIsolated() = runBlocking {
        val source = Source(); val resolver = StreamResolver(source)
        resolver.resolve(track, "low"); resolver.resolve(track, "high")
        assertEquals(2, source.calls.get())
        val expired = Source().apply { this.expired = true }; val r = StreamResolver(expired)
        r.resolve(track, "low"); r.resolve(track, "low"); assertEquals(2, expired.calls.get())
    }
    @Test fun rejectedStreamRetriesWithFullExtractor() = runBlocking {
        val source = Source(); val resolver = StreamResolver(source)
        resolver.resolve(track, "medium"); resolver.invalidate(track, "medium")
        assertTrue(resolver.resolve(track, "medium").url.endsWith("fallback"))
        assertEquals(1, source.fallbacks.get())
    }
    @Test fun failureCanBeRetriedWithoutPoisoningCache() = runBlocking {
        val source = Source().apply { fail = true }; val resolver = StreamResolver(source)
        assertTrue(runCatching { resolver.resolve(track, "medium") }.isFailure)
        source.fail = false; resolver.resolve(track, "medium"); assertEquals(2, source.calls.get())
    }
    @Test fun cancellationOfPrefetchDoesNotCancelPlaybackRequest() = runBlocking {
        val source = Source(); val resolver = StreamResolver(source)
        val prefetch = launch { resolver.resolve(track, "medium") }
        delay(10); prefetch.cancelAndJoin()
        resolver.resolve(track, "medium"); assertEquals(1, source.calls.get())
        assertEquals("content://local/1", resolver.resolve(track.copy(local = true, url = "content://local/1"), "medium").url)
        assertEquals(1, source.calls.get())
    }
    @Test fun failedAbandonedPrefetchDoesNotPoisonLaterPlayback() = runBlocking {
        val source = Source().apply { fail = true }; val resolver = StreamResolver(source)
        val prefetch = launch { resolver.resolve(track, "medium") }
        delay(10); prefetch.cancelAndJoin(); delay(80)
        source.fail = false
        assertTrue(resolver.resolve(track, "medium").url.startsWith("https://"))
        assertEquals(2, source.calls.get())
    }
}
