package id.iswara.music.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String = "",
    val artwork: String = "",
    val duration: Long = 0,
    val url: String,
    val local: Boolean = false,
    val device: String = "",
)

enum class SearchKind(val filter: String) { SONGS("music_songs"), ALBUMS("music_albums"), PLAYLISTS("music_playlists"), ARTISTS("channels") }
@Serializable
data class CatalogItem(val id: String, val title: String, val subtitle: String, val artwork: String, val url: String, val kind: String, val track: Track? = null)
data class CatalogPage(val items: List<CatalogItem>, val next: String? = null)
data class ResolvedAudio(val url: String, val mime: String? = null, val bitrate: Int = 0)
@Serializable
data class Playlist(val id: String, val title: String, val tracks: List<Track> = emptyList())
@Serializable
data class Library(val favorites: List<Track> = emptyList(), val playlists: List<Playlist> = emptyList(), val history: List<Track> = emptyList())
@Serializable
data class Backup(val version: Int = 1, val library: Library)
@Serializable
data class QueueSnapshot(val tracks: List<Track> = emptyList(), val index: Int = 0, val position: Long = 0, val shuffle: Boolean = false, val repeat: Int = 0, val order: List<Int> = emptyList())
@Serializable data class LyricLine(val time: Long, val text: String)

object MusicRules {
    fun move(queue: List<Track>, from: Int, to: Int): List<Track> {
        if (from !in queue.indices || to !in queue.indices) return queue
        return queue.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun decodeBackup(raw: String): Backup {
        require(raw.length <= 10_000_000) { "Backup terlalu besar" }
        val backup = AppJson.decodeFromString<Backup>(raw)
        require(backup.version == 1) { "Versi backup belum didukung" }
        val all = backup.library.favorites + backup.library.playlists.flatMap { it.tracks } + backup.library.history
        require(all.size <= 50_000) { "Terlalu banyak lagu" }
        require(all.all { it.id.isNotBlank() && it.title.isNotBlank() && (it.url.startsWith("https://") || (it.local && it.url.startsWith("content://"))) }) { "Isi backup tidak valid" }
        require(backup.library.playlists.map { it.id }.distinct().size == backup.library.playlists.size) { "ID playlist duplikat" }
        return backup
    }
    fun parseLyrics(raw: String): List<LyricLine> {
        val stamp = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
        return raw.lines().flatMap { line -> stamp.findAll(line).map { m ->
            LyricLine(((m.groupValues[1].toDouble() * 60 + m.groupValues[2].toDouble()) * 1000).toLong(), line.replace(stamp, "").trim())
        }.toList() }.sortedBy { it.time }
    }
}
