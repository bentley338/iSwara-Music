@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package id.iswara.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.*
import java.io.IOException

/** Bounds CDN requests while exposing one continuous, seekable byte stream to Media3. */
class ChunkedAudioDataSource(factory: DataSource.Factory) : DataSource {
    private val upstream = factory.createDataSource()
    private var original: DataSpec? = null
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var chunkRemaining = 0L
    private var opened = false
    private var chunked = false
    private var ended = false

    override fun addTransferListener(listener: TransferListener) = upstream.addTransferListener(listener)
    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun open(dataSpec: DataSpec): Long {
        original = dataSpec
        position = dataSpec.position
        remaining = dataSpec.length
        ended = false
        chunked = dataSpec.uri.host.orEmpty().endsWith(".googlevideo.com") && dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET
        if (!chunked) { opened = true; return upstream.open(dataSpec) }
        dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()?.takeIf { it >= position }?.let { total ->
            remaining = if (remaining == C.LENGTH_UNSET.toLong()) total - position else minOf(remaining, total - position)
        }
        if (remaining != 0L) openChunk()
        return remaining
    }

    private fun openChunk() {
        val spec = checkNotNull(original)
        val requested = if (remaining < 0) CHUNK_BYTES else minOf(CHUNK_BYTES, remaining)
        opened = true
        val size = upstream.open(spec.buildUpon().setPosition(position).setLength(requested).build())
        val range = upstream.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", true) }?.value?.firstOrNull()
            ?.let { Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(it.trim()) }
        val total = range?.groupValues?.get(3)?.toLongOrNull()
        if (total != null) {
            val available = (total - position).coerceAtLeast(0)
            remaining = if (remaining < 0) available else minOf(remaining, available)
        }
        val responseBytes = range?.let { it.groupValues[2].toLong() - it.groupValues[1].toLong() + 1 }
        chunkRemaining = minOf(requested, size.takeIf { it >= 0 } ?: requested, responseBytes ?: requested)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (!chunked) return upstream.read(buffer, offset, length)
        if (ended || remaining == 0L) return C.RESULT_END_OF_INPUT
        if (chunkRemaining == 0L) {
            closeUpstream()
            try { openChunk() } catch (e: HttpDataSource.InvalidResponseCodeException) {
                if (e.responseCode == 416 && remaining < 0) { ended = true; return C.RESULT_END_OF_INPUT }
                throw e
            }
        }
        val count = upstream.read(buffer, offset, minOf(length.toLong(), chunkRemaining).toInt())
        if (count == C.RESULT_END_OF_INPUT) {
            if (remaining > 0) throw IOException("Data audio terputus sebelum akhir stream")
            ended = true
            return count
        }
        position += count
        chunkRemaining -= count
        if (remaining >= 0) remaining -= count
        return count
    }

    private fun closeUpstream() { if (opened) { opened = false; upstream.close() } }
    override fun close() { try { closeUpstream() } finally { original = null; remaining = -1; chunkRemaining = 0; ended = false } }
    companion object { const val CHUNK_BYTES = 512 * 1024L }
}
