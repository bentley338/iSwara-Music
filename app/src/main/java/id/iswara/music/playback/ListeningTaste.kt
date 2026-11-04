package id.iswara.music.playback

import id.iswara.music.model.Track

internal data class ListeningTaste(val rules: Map<String, Long> = emptyMap()) {
    fun hidden(track: Track, now: Long = System.currentTimeMillis()): Boolean =
        (rules[recordingKey(track)] ?: 0L) > now

    fun less(track: Track): Boolean = rules[artistKey(track)] == -1L

    companion object {
        fun recordingKey(track: Track): String {
            val (artist, title) = QueueRules.Recording(track).identity
            return if (artist.isBlank() || title.isBlank()) "id:${track.id}" else "recording:$artist\n$title"
        }
        fun artistKey(track: Track) = "artist:" + QueueRules.artistKey(track.artist)
    }
}

internal fun freshShuffleOrder(tracks: List<Track>, history: List<Track>, previousArtist: String = ""): List<Int> {
    val recent = history.take(25).mapTo(hashSetOf(), ListeningTaste::recordingKey)
    val artists = tracks.map { QueueRules.artistKey(it.artist) }
    val groups = tracks.indices.shuffled().partition { ListeningTaste.recordingKey(tracks[it]) !in recent }
    var lastArtist = previousArtist
    return buildList {
        for (group in listOf(groups.first, groups.second)) {
            val remaining = group.toMutableList()
            while (remaining.isNotEmpty()) {
                val next = remaining.indexOfFirst { artists[it] != lastArtist }.coerceAtLeast(0)
                val index = remaining.removeAt(next)
                add(index)
                lastArtist = artists[index]
            }
        }
    }
}
