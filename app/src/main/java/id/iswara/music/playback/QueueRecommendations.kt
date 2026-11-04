package id.iswara.music.playback

import id.iswara.music.model.Library
import id.iswara.music.model.Track
import id.iswara.music.source.LyricsRules
import id.iswara.music.source.MusicSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun MusicSource.recommendQueue(
    seed: Track,
    queue: List<Track>,
    library: Library,
    mode: String,
    recent: List<String> = emptyList(),
    limit: Int = 15,
): List<Track> {
    val recentIds = recent + library.history.take(8).map { it.id }
    fun select(tracks: List<Track>) =
        QueueRules.smart(seed, queue, tracks, recentIds, library.favorites, library.history, mode, limit)

    val related = if (seed.local) emptyList() else recommendationRequest(8_000) { related(seed) }
    val ranked = select(related)
    if (ranked.size >= minOf(limit, 8)) return ranked

    val artists = (listOf(seed.artist) + ranked.map { it.artist } + queue.asReversed().map { it.artist })
        .map(LyricsRules::cleanArtist)
        .filter { QueueRules.artistKey(it).isNotEmpty() }
        .distinctBy(QueueRules::artistKey)
        .take(2)
    val fallback = coroutineScope {
        artists.map { artist ->
            async {
                val key = QueueRules.artistKey(artist)
                recommendationRequest(5_000) {
                    search(artist.take(160)).items.mapNotNull { it.track }
                        .filter { QueueRules.artistKey(it.artist) == key }
                }
            }
        }.awaitAll()
    }.flatten()
    return select(related + fallback)
}

private suspend fun recommendationRequest(
    timeoutMs: Long,
    request: suspend () -> List<Track>,
): List<Track> = withTimeoutOrNull(timeoutMs) {
    try {
        request()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyList()
    }
}.orEmpty()
