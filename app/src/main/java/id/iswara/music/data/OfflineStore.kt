@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.model.*
import id.iswara.music.playback.StreamResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class DownloadedTrack(val track: Track, val file: String, val bytes: Long)
@Singleton class OfflineStore @Inject constructor(@ApplicationContext private val context: Context, private val preferences: Preferences, private val library: LibraryRepository, private val streams: StreamResolver) {
    private val folder = File(context.filesDir, "offline").apply { mkdirs() }
    private val client = OkHttpClient.Builder().readTimeout(25, java.util.concurrent.TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    val progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    @OptIn(ExperimentalCoroutinesApi::class)
    val downloads = library.scope.flatMapLatest { owner -> preferences.raw("downloads:$owner").map { decode(it) } }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    private fun decode(raw: String?) = runCatching { AppJson.decodeFromString<List<DownloadedTrack>>(raw ?: "[]") }.getOrDefault(emptyList())
    fun enqueue(track: Track, quality: String) { DownloadWorker.enqueue(context, library.scope.value, track, quality) }
    fun cancel(id: String) { androidx.work.WorkManager.getInstance(context).cancelUniqueWork("download:${library.scope.value}:$id") }
    private fun fileName(owner: String, id: String) = offlineFileName(owner, id)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("iswara.offline", null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("iswara.offline", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    suspend fun download(track: Track, quality: String, owner: String = library.scope.value) = mutex.withLock { withContext(Dispatchers.IO) {
        require(!track.local) { "Musik lokal sudah ada di perangkat." }
        check(library.scope.value == owner) { "Akun berubah. Unduhan dibatalkan." }
        val name = fileName(owner, track.id)
        val partial = File(folder, "$name.part")
        progress.update { it + (track.id to 0) }
        try {
            val audio = streams.resolve(track, quality)
            val uri = android.net.Uri.parse(audio.url)
            // Reuse the same bounded transport as playback to avoid CDN stalls.
            val source = id.iswara.music.playback.ChunkedAudioDataSource(androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(client))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            var received = 0L
            try {
                val total = source.open(androidx.media3.datasource.DataSpec.Builder().setUri(uri).build())
                require(total < 32 * 1024 * 1024) { "Ukuran unduhan maksimal 32 MB per lagu." }
                partial.outputStream().use { file ->
                    file.write(cipher.iv)
                    CipherOutputStream(file, cipher).use { output ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            check(library.scope.value == owner) { "Akun berubah. Unduhan dibatalkan." }
                            val count = source.read(buffer, 0, buffer.size)
                            if (count < 0) break
                            received += count
                            require(received <= 32 * 1024 * 1024) { "Ukuran unduhan maksimal 32 MB per lagu." }
                            output.write(buffer, 0, count)
                            progress.update { it + (track.id to if (total > 0) (received * 100 / total).toInt().coerceAtMost(99) else 0) }
                        }
                    }
                }
            } finally { source.close() }
            check(received > 0 && library.scope.value == owner) { "Unduhan tidak selesai." }
            java.nio.file.Files.move(partial.toPath(), File(folder, name).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            val old = decode(preferences.raw("downloads:$owner").first())
            preferences.set("downloads:$owner", AppJson.encodeToString(old.filterNot { it.track.id == track.id } + DownloadedTrack(track, name, received)))
        } finally { partial.delete(); progress.update { it - track.id } }
    } }
    fun available(id: String): Boolean = File(folder, fileName(library.scope.value, id)).isFile
    fun read(id: String): ByteArray {
        val file = File(folder, fileName(library.scope.value, id))
        check(file.isFile && file.length() in 29..(32 * 1024 * 1024 + 28).toLong()) { "Unduhan tidak tersedia. Unduh ulang lagu ini." }
        val encrypted = file.readBytes()
        return decryptOffline(encrypted, key())
    }
    suspend fun remove(track: Track, owner: String = library.scope.value) = mutex.withLock { withContext(Dispatchers.IO) {
        check(library.scope.value == owner) { "Akun berubah." }
        File(folder, fileName(owner, track.id)).delete()
        val old = decode(preferences.raw("downloads:$owner").first())
        preferences.set("downloads:$owner", AppJson.encodeToString(old.filterNot { it.track.id == track.id }))
    } }
    suspend fun clearOwner(owner: String) = mutex.withLock { withContext(Dispatchers.IO) {
        decode(preferences.raw("downloads:$owner").first()).forEach { File(folder, fileName(owner, it.track.id)).delete() }
        preferences.set("downloads:$owner", "[]")
        preferences.set("stats:$owner", "[]")
    } }
}
