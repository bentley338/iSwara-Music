@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import android.net.Uri
import androidx.media3.datasource.*
import id.iswara.music.data.OfflineStore

class OfflineDataSource(private val offline: OfflineStore, private val upstream: DataSource) : DataSource {
    private var active: DataSource = upstream
    private val listeners = mutableListOf<TransferListener>()
    override fun open(dataSpec: DataSpec): Long {
        active = if (dataSpec.uri.scheme == "iswara-offline") ByteArrayDataSource(offline.read(dataSpec.uri.host ?: error("Unduhan tidak valid"))).also { data -> listeners.forEach(data::addTransferListener) } else upstream
        return active.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int) = active.read(buffer, offset, length)
    override fun getUri(): Uri? = active.uri
    override fun getResponseHeaders() = active.responseHeaders
    override fun addTransferListener(listener: TransferListener) { listeners += listener; upstream.addTransferListener(listener) }
    override fun close() = active.close()
}
