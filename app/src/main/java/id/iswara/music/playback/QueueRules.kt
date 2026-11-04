package id.iswara.music.playback

import id.iswara.music.model.Track
import id.iswara.music.source.LyricsRules
import id.iswara.music.source.SourceMatchRules

object QueueRules {
    val modes = listOf("balanced" to "Seimbang", "familiar" to "Lebih akrab", "discovery" to "Temukan baru")

    private val variantWords = "live|cover|remix|slowed|sped up|speed up|nightcore|karaoke|instrumental|acoustic|akustik|reverb|remaster(?:ed)?|edit|version"
    private val variant = Regex("""\b(?:$variantWords)\b""", RegexOption.IGNORE_CASE)
    private val suffix = Regex(
        """\s*(?:\(([^()]*)\)|\[([^\[\]]*)\]|\s+[-\u2013\u2014|:]\s+(.+))\s*$"""
    )
    private val bareVariant = Regex(
        """\s+(?:$variantWords)(?:\s*\+\s*reverb|\s+(?:mix|version|edit|20\d{2}))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val credits = Regex("""\s+(?:feat\.?|ft\.?)\s+.+$""", RegexOption.IGNORE_CASE)
    private val packaging = Regex(
        """^(?:(?:official|music|lyric|lyrics|video|audio|visualizer|hd|4k)\s*)+$""",
        RegexOption.IGNORE_CASE,
    )

    internal fun artistKey(artist: String): String = SourceMatchRules.clean(
        LyricsRules.cleanArtist(artist).replace(credits, "")
    )

    internal data class Recording(val track: Track) {
        val artist = artistKey(track.artist)
        private val cleanTitle = LyricsRules.cleanTitle(track.title, track.artist).replace(credits, "")
        private val edition = suffix.find(cleanTitle)
        private val trailingVariant = bareVariant.find(cleanTitle)
        private val editionText = edition?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }.orEmpty()
        private val titleWithoutSuffix = if (variant.containsMatchIn(editionText) || packaging.matches(editionText)) {
            cleanTitle.substring(0, requireNotNull(edition).range.first)
        } else {
            trailingVariant?.let { cleanTitle.substring(0, it.range.first) } ?: cleanTitle
        }
        val title = SourceMatchRules.clean(titleWithoutSuffix)
        val identity = artist to title
        val variants = variant.findAll(editionText.ifEmpty { trailingVariant?.value.orEmpty() })
            .map { it.value.lowercase(java.util.Locale.ROOT) }.toSet()
    }

    fun smart(
        seed: Track,
        existing: List<Track>,
        related: List<Track>,
        recent: List<String> = emptyList(),
        favorites: List<Track> = emptyList(),
        history: List<Track> = emptyList(),
        mode: String = "balanced",
        limit: Int = 15,
        topArtists: Set<String> = emptySet(),
    ): List<Track> {
        if (limit <= 0) return emptyList()
        val origin = Recording(seed)
        val recentIds = recent.toHashSet()
        val blocked = existing + seed + history.filter { it.id in recentIds }
        val recordings = blocked.map(::Recording)
        val blockedIds = blocked.mapTo(recentIds) { it.id }
        val used = recordings.mapTo(mutableSetOf()) { it.identity }
        val usedTitles = recordings.mapTo(hashSetOf()) { it.title }
        val favoriteKeys = favorites.mapTo(mutableSetOf()) { Recording(it).identity }
        val heardKeys = history.mapTo(mutableSetOf()) { Recording(it).identity }
        val affinity = (favorites.distinctBy { it.id } + history.take(40).distinctBy { it.id })
            .groupingBy { artistKey(it.artist) }.eachCount()
        val lastArtists = (existing.takeLast(2).ifEmpty { listOf(seed) }).map { artistKey(it.artist) }.toMutableList()
        val counts = mutableMapOf<String, Int>()
        val candidates = related.asSequence().distinctBy { it.id }
            .filter { !it.local && it.id !in blockedIds && it.duration in 30..1800 }
            .map(::Recording)
            .filter { it.title.isNotBlank() && it.title != origin.title && it.identity !in used }
            .filter { origin.variants.containsAll(it.variants) && (it.variants.isEmpty() || it.title !in usedTitles) }
            .take(80)
            .mapIndexed { index, recording ->
                val familiar = (affinity[recording.artist] ?: 0).coerceAtMost(5) / 5.0
                val known = recording.identity in heardKeys || recording.identity in favoriteKeys
                val preference = when (mode) {
                    "familiar" -> familiar * 1.2 + if (recording.identity in favoriteKeys) 0.5 else 0.0
                    "discovery" -> (if (known) -0.8 else 0.8) - familiar * 0.3
                    else -> familiar * 0.4 + if (known) 0.0 else 0.2
                }
                val listeningBoost = if (mode != "discovery" && recording.artist in topArtists) 0.35 else 0.0
                recording to (2.0 / (1 + index * 0.12) + preference + listeningBoost)
            }.toMutableList()
        val result = mutableListOf<Track>()
        repeat(limit.coerceAtMost(50)) {
            val lastArtist = lastArtists.lastOrNull()
            val choice = candidates.maxByOrNull { (recording, score) ->
                score - (counts[recording.artist] ?: 0) * 0.85 -
                    (if (recording.artist == lastArtist) 2.2 else 0.0) -
                    (if (recording.artist == lastArtists.firstOrNull()) 0.45 else 0.0)
            } ?: return result
            val recording = choice.first
            result += recording.track
            lastArtists += recording.artist
            if (lastArtists.size > 2) lastArtists.removeAt(0)
            val artistCount = (counts[recording.artist] ?: 0) + 1
            counts[recording.artist] = artistCount
            candidates.removeAll { (candidate, _) ->
                candidate.identity == recording.identity ||
                    (candidate.title == recording.title && (candidate.variants.isNotEmpty() || recording.variants.isNotEmpty())) ||
                    (candidate.artist == recording.artist && artistCount >= 3)
            }
        }
        return result
    }

    fun radio(existing: List<Track>, related: List<Track>, recent: List<String> = emptyList(), limit: Int = 15): List<Track> {
        val excluded = existing.map { it.id }.toSet() + recent
        return related.asSequence().filter { !it.local && it.id !in excluded && it.duration in 30..1800 }
            .distinctBy { it.id }.take(limit).toList()
    }
    fun append(existing: List<Track>, incoming: List<Track>, duplicates: Boolean): List<Track> =
        if (duplicates) incoming else incoming.distinctBy { it.id }.filter { track -> existing.none { it.id == track.id } }
}
