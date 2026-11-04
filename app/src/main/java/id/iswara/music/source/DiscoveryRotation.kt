package id.iswara.music.source

import id.iswara.music.model.Library
import id.iswara.music.model.Track
import id.iswara.music.playback.QueueRules.Recording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Tracks which recordings were shown during this session, across mood and custom mixes. */
internal class DiscoveryRotation {
    private val lastShown = LinkedHashMap<Pair<String, String>, Int>()
    private var round = 0
    private var lastOpener: Pair<String, String>? = null

    fun select(
        candidates: List<Track>,
        limit: Int,
        library: Library,
        duration: String = "any",
        blocked: List<Track> = emptyList(),
    ): List<Track> {
        val range = when (duration) {
            "short" -> 30L..239L
            "long" -> 240L..600L
            else -> 30L..1800L
        }
        val blockedIds = blocked.mapTo(hashSetOf()) { it.id }
        val blockedKeys = blocked.mapTo(hashSetOf()) { Recording(it).identity }
        val recentlyPlayed = library.history.take(12).mapTo(hashSetOf()) { Recording(it).identity }
        val familiarArtists = (library.favorites.take(40) + library.history.take(30))
            .mapTo(hashSetOf()) { Recording(it).artist }
        val eligible = candidates.asSequence()
            .filter { !it.local && it.id.isNotBlank() && it.id !in blockedIds && it.duration in range }
            .distinctBy { it.id }
            .map(::Recording)
            .filter { it.title.isNotBlank() && it.identity !in blockedKeys }
            .groupBy { it.identity }
            .values.map { versions -> versions.minBy { it.variants.size } }

        val remaining = eligible.withIndex().toMutableList()
        val selected = mutableListOf<Recording>()
        val artistCounts = mutableMapOf<String, Int>()
        while (selected.size < limit && remaining.isNotEmpty()) {
            val lastArtist = selected.lastOrNull()?.artist
            val pick = remaining.minWith(compareBy<IndexedValue<Recording>> {
                lastShown[it.value.identity] ?: Int.MIN_VALUE
            }.thenByDescending { (index, recording) ->
                val artist = recording.artist
                2.0 / (1 + index * 0.12) +
                    (if (artist in familiarArtists) 0.35 else 0.0) -
                    (if (recording.identity in recentlyPlayed) 0.75 else 0.0) -
                    (if (selected.isEmpty() && recording.identity == lastOpener) 3.0 else 0.0) -
                    (if (artist == lastArtist) 1.5 else 0.0) -
                    (artistCounts[artist] ?: 0) * 0.6
            })
            remaining.remove(pick)
            selected += pick.value
            artistCounts.merge(pick.value.artist, 1, Int::plus)
        }
        if (selected.isNotEmpty()) {
            round++
            lastOpener = selected.first().identity
            selected.forEach { lastShown.remove(it.identity); lastShown[it.identity] = round }
            while (lastShown.size > 300) lastShown.remove(lastShown.keys.first())
        }
        return selected.map { it.track }
    }
}

internal suspend fun MusicSource.discoveryCandidates(query: String, expand: Boolean): List<Track> {
    val first = search(query)
    val more = if (expand) withTimeoutOrNull(7_000) {
        try {
            val next = first.next
            val alternate = if (query.endsWith(" songs", ignoreCase = true)) query.dropLast(6) else "$query songs"
            if (next != null) search(query, next = next).items else search(alternate).items
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }.orEmpty() else emptyList()
    return (first.items + more).mapNotNull { it.track }
}
