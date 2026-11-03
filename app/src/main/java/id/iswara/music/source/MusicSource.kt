package id.iswara.music.source

import id.iswara.music.model.*

interface MusicSource {
    suspend fun search(query: String, kind: SearchKind = SearchKind.SONGS, next: String? = null): CatalogPage
    suspend fun collection(url: String, next: String? = null): CatalogPage
    suspend fun resolve(track: Track, quality: String): ResolvedAudio
    suspend fun resolveFallback(track: Track, quality: String): ResolvedAudio = resolve(track, quality)
    suspend fun fromUrl(url: String): Track
    suspend fun related(track: Track): List<Track>
}
