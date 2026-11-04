package id.iswara.music.playback

import id.iswara.music.model.Library
import id.iswara.music.model.Track
import id.iswara.music.source.LyricsRules
import id.iswara.music.source.MusicSource
import id.iswara.music.source.discoveryCandidates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

internal data class RadioContext(val query: String, val tracks: List<Track>)

internal suspend fun MusicSource.recommendQueue(
    seed: Track,
    queue: List<Track>,
    library: Library,
    mode: String,
    recent: List<String> = emptyList(),
    limit: Int = 15,
    context: RadioContext? = null,
    topArtists: Set<String> = emptySet(),
): List<Track> {
    val recentIds = recent + library.history.take(8).map { it.id }
    fun select(tracks: List<Track>, chosen: List<Track> = emptyList()) = QueueRules.smart(
        seed, queue + chosen, tracks, recentIds, library.favorites, library.history, mode, limit - chosen.size, topArtists,
    )

    val (related, themed) = coroutineScope {
        val related = async { if (seed.local) emptyList() else recommendationRequest(8_000) { related(seed) } }
        val themed = if (context != null && context.query.isNotBlank()) {
            val first = select(context.tracks)
            val more = if (queue.size <= 1 || first.size < limit) recommendationRequest(9_000) {
                discoveryCandidates(context.query, expand = true)
            } else emptyList()
            val visible = context.tracks.mapTo(hashSetOf()) { QueueRules.Recording(it).identity }
            val fresh = more.filter { QueueRules.Recording(it).identity !in visible }
            context.tracks.take(4) + fresh + context.tracks.drop(4)
        } else emptyList()
        related.await() to themed
    }
    val themedKeys = themed.mapTo(hashSetOf()) { QueueRules.Recording(it).identity }
    val themedPicks = select(related.filter { QueueRules.Recording(it).identity in themedKeys } + themed)
    val target = minOf(limit, 8)
    if (themedPicks.size >= target) return themedPicks

    val ranked = select(related, themedPicks)
    if (themedPicks.size + ranked.size >= target) {
        return themedPicks + if (themedPicks.isEmpty()) ranked else ranked.take(target - themedPicks.size)
    }

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
    val fillers = select(related + fallback, themedPicks)
    return themedPicks + if (themedPicks.isEmpty()) fillers else fillers.take(target - themedPicks.size)
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
