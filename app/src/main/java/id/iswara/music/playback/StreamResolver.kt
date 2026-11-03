package id.iswara.music.playback

import id.iswara.music.model.ResolvedAudio
import id.iswara.music.model.Track
import id.iswara.music.source.MusicSource
import kotlinx.coroutines.*
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Shares in-flight extraction between the player and next-track preparation. Audio stays in RAM. */
@Singleton
class StreamResolver @Inject constructor(private val source: MusicSource) {
    private data class Entry(val audio: ResolvedAudio, val expires: Long)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = ConcurrentHashMap<String, Entry>()
    private val pending = ConcurrentHashMap<String, Deferred<Entry>>()
    private val fallback = ConcurrentHashMap.newKeySet<String>()

    suspend fun resolve(track: Track, quality: String): ResolvedAudio {
        if (track.local) return ResolvedAudio(track.url)
        val key = "${track.url}:$quality"
        entries[key]?.takeIf { it.expires > System.currentTimeMillis() }?.let { return it.audio }
        val task = pending.computeIfAbsent(key) {
            scope.async(start = CoroutineStart.LAZY) {
                val audio = withTimeout(35_000) { if (key in fallback) source.resolveFallback(track, quality) else source.resolve(track, quality) }
                val now = System.currentTimeMillis()
                val expiry = runCatching { URI(audio.url).rawQuery.split('&').firstOrNull { it.startsWith("expire=") }?.substringAfter('=')?.toLongOrNull()?.times(1000) }.getOrNull()
                Entry(audio, minOf(now + 15 * 60_000, expiry?.minus(60_000) ?: Long.MAX_VALUE)).also {
                    if (entries.size >= 64) entries.entries.minByOrNull { it.value.expires }?.let { oldest -> entries.remove(oldest.key) }
                    entries[key] = it
                }
            }
        }
        task.invokeOnCompletion { pending.remove(key, task) }
        return try { task.await().audio } finally { if (task.isCompleted) pending.remove(key, task) }
    }

    fun invalidate(track: Track, quality: String) {
        val key = "${track.url}:$quality"
        entries.remove(key)
        fallback.add(key)
        pending.remove(key)?.cancel()
    }
    fun invalidate(track: Track) { listOf("low", "medium", "high").forEach { invalidate(track, it) } }

    fun clear() { entries.clear(); pending.values.forEach { it.cancel() }; pending.clear(); fallback.clear() }
}
