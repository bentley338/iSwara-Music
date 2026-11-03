@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.*
import id.iswara.music.playback.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WarmAudioTest {
    private val bytes = ByteArray(400_003) { (it % 251).toByte() }
    private val uri = Uri.parse("https://r1.googlevideo.com/audio?clen=${bytes.size}")
    private class Factory(private val bytes: ByteArray) : DataSource.Factory {
        val requests = mutableListOf<DataSpec>()
        override fun createDataSource(): DataSource = object : DataSource {
            private val data = ByteArrayDataSource(bytes)
            override fun addTransferListener(listener: TransferListener) = data.addTransferListener(listener)
            override fun getUri(): Uri? = data.uri
            override fun open(dataSpec: DataSpec): Long { synchronized(requests) { requests += dataSpec }; return data.open(dataSpec) }
            override fun read(buffer: ByteArray, offset: Int, length: Int) = data.read(buffer, offset, length)
            override fun close() = data.close()
        }
    }
    private fun readAll(source: DataSource): ByteArray {
        val result = ByteArrayOutputStream(); val buffer = ByteArray(7001)
        while (true) { val n = source.read(buffer, 0, buffer.size); if (n == C.RESULT_END_OF_INPUT) break; assertTrue(n > 0); result.write(buffer, 0, n) }
        return result.toByteArray()
    }
    @Test fun prefixStartsWithoutNetworkAndRemainderHasNoMissingOrRepeatedBytes() = runBlocking {
        val cache = AudioWarmCache(); cache.preload(uri, Factory(bytes))
        val factory = Factory(bytes); val source = WarmAudioDataSource(cache, factory.createDataSource())
        try {
            assertEquals(bytes.size.toLong(), source.open(DataSpec.Builder().setUri(uri).build()))
            assertTrue(factory.requests.isEmpty())
            val first = ByteArray(AudioWarmCache.BYTES)
            assertEquals(first.size, source.read(first, 0, first.size))
            assertArrayEquals(bytes.copyOfRange(0, first.size), first)
            assertTrue("No network request before cached audio is consumed", factory.requests.isEmpty())
            assertArrayEquals(bytes.copyOfRange(first.size, bytes.size), readAll(source))
            assertEquals(first.size.toLong(), factory.requests.single().position)
        } finally { source.close() }
    }
    @Test fun seekExplicitRangeExpiryAndOwnerClearNeverReuseWrongBytes() = runBlocking {
        var time = 100L
        val cache = AudioWarmCache { time }; cache.preload(uri, Factory(bytes))
        val factory = Factory(bytes); val source = WarmAudioDataSource(cache, factory.createDataSource())
        try {
            source.open(DataSpec.Builder().setUri(uri).setPosition(130000).setLength(9000).build())
            assertArrayEquals(bytes.copyOfRange(130000, 139000), readAll(source))
            assertEquals(131072L, factory.requests.single().position)
            source.close(); factory.requests.clear()
            source.open(DataSpec.Builder().setUri(uri).setPosition(200000).setLength(5000).build())
            assertArrayEquals(bytes.copyOfRange(200000, 205000), readAll(source))
            source.close()
            assertNotNull(cache.get(uri)); assertNull(cache.get(Uri.parse(uri.toString() + "&quality=other")))
            time += 120_001; assertNull(cache.get(uri))
            cache.preload(uri, Factory(bytes)); cache.clear(); assertNull(cache.get(uri))
        } finally { source.close() }
    }
    @Test fun clearingWhilePrefetchRunsDoesNotRepopulatePreviousOwnerCache() = runBlocking {
        val cache = AudioWarmCache()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val original = Factory(bytes)
        val factory = DataSource.Factory {
            val data = original.createDataSource()
            object : DataSource by data {
                override fun open(dataSpec: DataSpec): Long = runBlocking { entered.complete(Unit); release.await(); data.open(dataSpec) }
            }
        }
        val job = launch { cache.preload(uri, factory) }
        entered.await(); cache.clear(); release.complete(Unit); job.join()
        assertNull(cache.get(uri))
    }
    @Test fun runtimeErrorsUseAudioRecoveryAndDiagnosticsExcludeSignedUrls() {
        val secret = "https://r1.googlevideo.com/audio?token=private-example"
        val error = androidx.media3.common.PlaybackException(secret, IllegalArgumentException(secret), androidx.media3.common.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK)
        assertEquals(Recovery.AUDIO, PlaybackFailures.recovery(error))
        assertFalse(PlaybackFailures.report(error).contains(secret))
        assertTrue(PlaybackFailures.message(error).contains("audio"))
    }
}
