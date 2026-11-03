@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package id.iswara.music

import android.app.Application
import androidx.media3.common.C
import androidx.media3.datasource.*
import androidx.media3.datasource.okhttp.OkHttpDataSource
import id.iswara.music.playback.ChunkedAudioDataSource
import id.iswara.music.playback.AudioWarmCache
import id.iswara.music.playback.WarmAudioDataSource
import id.iswara.music.source.YoutubeMusicSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioDataSourceTest {
    private val bytes = ByteArray(1_300_037) { (it % 251).toByte() }
    private class Factory(private val bytes: ByteArray, private val headers: Boolean = false, private val truncated: Boolean = false) : DataSource.Factory {
        val requests = mutableListOf<DataSpec>()
        override fun createDataSource(): DataSource = object : DataSource {
            private val data = ByteArrayDataSource(bytes)
            private var spec: DataSpec? = null
            private var received = 0
            override fun addTransferListener(listener: TransferListener) = data.addTransferListener(listener)
            override fun getUri() = data.uri
            override fun getResponseHeaders(): Map<String, List<String>> {
                val request = spec ?: return emptyMap()
                return if (headers) mapOf("Content-Range" to listOf("bytes ${request.position}-${minOf(bytes.lastIndex.toLong(), request.position + request.length - 1)}/${bytes.size}")) else emptyMap()
            }
            override fun open(dataSpec: DataSpec): Long { spec = dataSpec; received = 0; requests += dataSpec; return data.open(dataSpec) }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (truncated && received > 100) return C.RESULT_END_OF_INPUT
                return data.read(buffer, offset, length).also { if (it > 0) received += it }
            }
            override fun close() = data.close()
        }
    }
    private fun readAll(source: DataSource): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(7001)
        while (true) { val count = source.read(buffer, 0, buffer.size); if (count == C.RESULT_END_OF_INPUT) break; check(count > 0); output.write(buffer, 0, count) }
        return output.toByteArray()
    }
    @Test fun readsContinuouslyAcrossThreeRangesWithoutDroppingBytes() {
        val factory = Factory(bytes); val source = ChunkedAudioDataSource(factory)
        try {
            assertEquals(bytes.size.toLong(), source.open(DataSpec.Builder().setUri("https://r1.googlevideo.com/audio?clen=${bytes.size}").build()))
            assertEquals(0, source.read(ByteArray(0), 0, 0))
            assertArrayEquals(bytes, readAll(source))
            assertEquals(listOf(0L, 524288L, 1048576L), factory.requests.map { it.position })
            assertTrue(factory.requests.all { it.length in 1..524288L })
        } finally { source.close() }
    }
    @Test fun seekAndExplicitLengthReturnTheExactRequestedBytes() {
        val factory = Factory(bytes); val source = ChunkedAudioDataSource(factory)
        try {
            val spec = DataSpec.Builder().setUri("https://r1.googlevideo.com/audio?clen=${bytes.size}").setPosition(500000).setLength(600000).build()
            assertEquals(600000L, source.open(spec))
            assertArrayEquals(bytes.copyOfRange(500000, 1100000), readAll(source))
            assertEquals(listOf(500000L, 1024288L), factory.requests.map { it.position })
        } finally { source.close() }
    }
    @Test fun responseRangeSuppliesLengthWhenUrlHasNoLength() {
        val source = ChunkedAudioDataSource(Factory(bytes, headers = true))
        try {
            assertEquals(bytes.size.toLong(), source.open(DataSpec.Builder().setUri("https://r1.googlevideo.com/audio").build()))
            assertArrayEquals(bytes, readAll(source))
        } finally { source.close() }
    }
    @Test fun otherHostsKeepTheirOriginalRequest() {
        val factory = Factory(bytes); val source = ChunkedAudioDataSource(factory)
        try {
            source.open(DataSpec.Builder().setUri("https://audio.example/music").build())
            assertArrayEquals(bytes, readAll(source))
            assertEquals(1, factory.requests.size); assertEquals(-1L, factory.requests.single().length)
        } finally { source.close() }
    }
    @Test fun prematureEndIsAnErrorInsteadOfASilentlyTruncatedSong() {
        val source = ChunkedAudioDataSource(Factory(bytes, truncated = true))
        try {
            source.open(DataSpec.Builder().setUri("https://r1.googlevideo.com/audio?clen=${bytes.size}").build())
            assertTrue(runCatching { readAll(source) }.exceptionOrNull() is java.io.IOException)
        } finally { source.close() }
    }
    @Test fun liveContinuousPlaybackTransportAndSeek() = runBlocking {
        assumeTrue(System.getenv("ISWARA_LIVE_TEST") == "1")
        val music = YoutubeMusicSource()
        val track = music.search("Tulus Hati Hati di Jalan").items.firstNotNullOf { it.track }
        val start = System.nanoTime()
        val audio = music.resolve(track, "medium")
        val client = okhttp3.OkHttpClient.Builder().readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
        val source = ChunkedAudioDataSource(OkHttpDataSource.Factory(client).setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"))
        val buffer = ByteArray(8192)
        try {
            val total = source.open(DataSpec.Builder().setUri(audio.url).build())
            val first = source.read(buffer, 0, buffer.size)
            assertTrue(first > 0)
            val firstMs = (System.nanoTime() - start) / 1_000_000
            var received = first.toLong()
            while (received < 1_100_000) { val count = source.read(buffer, 0, buffer.size); if (count == C.RESULT_END_OF_INPUT) break; received += count }
            assertTrue(received >= 1_048_576)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            source.close()
            source.open(DataSpec.Builder().setUri(audio.url).setPosition(600000).setLength(32768).build())
            val seekBytes = readAll(source)
            assertEquals(32768, seekBytes.size)
            println("CHUNKED_METRICS firstByteIncludingResolveMs=$firstMs bytes=$received totalMs=$elapsed contentLength=$total seekBytes=${seekBytes.size}")
            val transport = DataSource.Factory { ChunkedAudioDataSource(OkHttpDataSource.Factory(client)) }
            val cache = AudioWarmCache()
            cache.preload(android.net.Uri.parse(audio.url), transport)
            val warm = WarmAudioDataSource(cache, transport.createDataSource())
            try {
                val warmStart = System.nanoTime()
                val warmLength = warm.open(DataSpec.Builder().setUri(audio.url).build())
                val warmBytes = warm.read(buffer, 0, buffer.size)
                assertEquals(total, warmLength); assertTrue(warmBytes > 0)
                println("WARM_METRICS cachedBytes=${cache.get(android.net.Uri.parse(audio.url))?.bytes?.size} firstCachedReadUs=${(System.nanoTime() - warmStart) / 1000}")
            } finally { warm.close(); cache.clear() }
        } finally { source.close() }
    }
}
