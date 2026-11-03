@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Small, short-lived RAM prefixes. Signed URLs and audio are never written to disk here. */
class AudioWarmCache(private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
    data class Prefix(val bytes: ByteArray, val total: Long, val expires: Long)
    private val entries = LinkedHashMap<String, Prefix>()
    private var generation = 0L
    @Synchronized fun get(uri: Uri): Prefix? = entries[uri.toString()]?.takeIf { it.expires > clock() }
    @Synchronized fun clear() { generation++; entries.clear() }
    @Synchronized private fun token() = generation
    @Synchronized private fun put(uri: Uri, prefix: Prefix, token: Long) {
        if (token != generation) return
        entries.entries.removeAll { it.value.expires <= clock() }
        entries.remove(uri.toString())
        entries[uri.toString()] = prefix
        while (entries.size > 2) entries.remove(entries.keys.first())
    }
    suspend fun preload(uri: Uri, factory: DataSource.Factory) {
        if (get(uri) != null) return
        val token = token()
        val prefix = withContext(Dispatchers.IO) {
            val source = factory.createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(uri).setLength(BYTES.toLong()).build())
                val total = uri.getQueryParameter("clen")?.toLongOrNull()
                    ?: source.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", true) }?.value?.firstOrNull()?.substringAfterLast('/')?.toLongOrNull()
                    ?: -1L
                if (total <= 0) return@withContext null
                val target = minOf(BYTES.toLong(), total).toInt()
                val output = ByteArrayOutputStream(target)
                val buffer = ByteArray(16384)
                while (output.size() < target) {
                    ensureActive()
                    val count = source.read(buffer, 0, minOf(buffer.size, target - output.size()))
                    if (count < 0) break
                    if (count == 0) throw IOException("Audio belum tersedia")
                    output.write(buffer, 0, count)
                }
                if (output.size() != target) null else Prefix(output.toByteArray(), total, clock() + 120_000)
            } finally { source.close() }
        }
        if (prefix != null) put(uri, prefix, token)
    }
    companion object { const val BYTES = 128 * 1024 }
}

/** Exposes the prefetched prefix and the remaining CDN ranges as one seekable stream. */
class WarmAudioDataSource(private val cache: AudioWarmCache, private val upstream: DataSource) : DataSource {
    private var spec: DataSpec? = null
    private var prefix: AudioWarmCache.Prefix? = null
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false
    override fun addTransferListener(listener: TransferListener) = upstream.addTransferListener(listener)
    override fun getUri(): Uri? = if (opened) upstream.uri else spec?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = if (opened) upstream.responseHeaders else emptyMap()
    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec; position = dataSpec.position; remaining = dataSpec.length
        prefix = if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET && dataSpec.httpBody == null)
            cache.get(dataSpec.uri)?.takeIf { position < it.bytes.size } else null
        val cached = prefix
        return if (cached == null) openUpstream() else {
            remaining = minOf(cached.total - position, remaining.takeIf { it >= 0 } ?: Long.MAX_VALUE)
            remaining
        }
    }
    private fun openUpstream(): Long {
        opened = true
        return upstream.open(checkNotNull(spec).buildUpon().setPosition(position).setLength(remaining).build())
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val cached = prefix
        val count = if (cached != null && position < cached.bytes.size) {
            minOf(length.toLong(), cached.bytes.size - position, remaining).toInt().also {
                System.arraycopy(cached.bytes, position.toInt(), buffer, offset, it)
            }
        } else {
            if (!opened) openUpstream()
            upstream.read(buffer, offset, if (remaining >= 0) minOf(length.toLong(), remaining).toInt() else length)
        }
        if (count < 0) {
            if (prefix != null && remaining > 0) throw IOException("Data audio terputus")
            return count
        }
        position += count
        if (remaining >= 0) remaining -= count
        return count
    }
    override fun close() {
        try { if (opened) upstream.close() } finally { opened = false; spec = null; prefix = null; remaining = -1 }
    }
}
