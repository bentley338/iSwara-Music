package id.iswara.music.source

import id.iswara.music.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ExtractorDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
        request.headers().forEach { (key, values) -> builder.header(key, values.joinToString(", ")) }
        builder.method(request.httpMethod(), request.dataToSend()?.toRequestBody())
        return client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw java.io.IOException("Sumber membatasi permintaan. Coba lagi nanti.")
            Response(response.code, response.message, response.headers.toMultimap(), response.body?.string().orEmpty(), response.request.url.toString())
        }
    }
}

@Singleton
class YoutubeMusicSource @Inject constructor() : MusicSource {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(18, TimeUnit.SECONDS).build()
    private val pages = ConcurrentHashMap<String, Page>()
    private val browse = InnerTubeBrowse()
    private val youtube = ServiceList.YouTube
    init { NewPipe.init(ExtractorDownloader(client), Localization("en", "US"), ContentCountry("ID")) }

    private fun InfoItem.item(): CatalogItem? = when (this) {
        is StreamInfoItem -> {
            val t = Track(url.substringAfter("v=", url).substringBefore('&'), name, uploaderName.orEmpty(), artwork(thumbnails.lastOrNull()?.url.orEmpty()), duration.coerceAtLeast(0), url)
            CatalogItem(t.id, t.title, t.artist, t.artwork, url, "song", t)
        }
        is PlaylistInfoItem -> CatalogItem(url, name, uploaderName.orEmpty(), artwork(thumbnails.lastOrNull()?.url.orEmpty()), url, "playlist")
        is ChannelInfoItem -> CatalogItem(url, name, "Artist / channel", artwork(thumbnails.lastOrNull()?.url.orEmpty()), url, "artist")
        else -> null
    }
    private fun remember(page: Page?): String? = page?.takeIf { Page.isValid(it) }?.let { p -> java.util.UUID.randomUUID().toString().also { pages[it] = p; if (pages.size > 100) pages.keys.firstOrNull()?.let(pages::remove) } }
    override suspend fun search(query: String, kind: SearchKind, next: String?): CatalogPage = withContext(Dispatchers.IO) {
        require(query.isNotBlank())
        val extractor = youtube.getSearchExtractor(query, listOf(kind.filter), "")
        if (next == null) { extractor.fetchPage(); CatalogPage(extractor.initialPage.items.mapNotNull { it.item() }, remember(extractor.initialPage.nextPage)) }
        else { val p = extractor.getPage(pages[next] ?: error("Halaman kedaluwarsa. Ulangi pencarian.")); CatalogPage(p.items.mapNotNull { it.item() }, remember(p.nextPage)) }
    }
    override suspend fun collection(url: String, next: String?): CatalogPage = withContext(Dispatchers.IO) {
        if (url.contains("music.youtube.com/browse/")) return@withContext browse.browse(url.substringAfter("/browse/").substringBefore('?'))
        if (url.contains("/channel/") || url.contains("/@")) {
            val channel = youtube.getChannelExtractor(url).apply { fetchPage() }
            val tab = channel.tabs.firstOrNull { it.contentFilters.contains("videos") } ?: channel.tabs.firstOrNull() ?: return@withContext CatalogPage(emptyList())
            val extractor = youtube.getChannelTabExtractor(tab).apply { fetchPage() }
            val p = if (next == null) extractor.initialPage else extractor.getPage(pages[next] ?: error("Halaman kedaluwarsa"))
            CatalogPage(p.items.mapNotNull { it.item() }, remember(p.nextPage))
        } else {
            val extractor = youtube.getPlaylistExtractor(url).apply { fetchPage() }
            val p = if (next == null) extractor.initialPage else extractor.getPage(pages[next] ?: error("Halaman kedaluwarsa"))
            CatalogPage(p.items.mapNotNull { it.item() }, remember(p.nextPage))
        }
    }
    override suspend fun resolve(track: Track, quality: String): ResolvedAudio = withContext(Dispatchers.IO) {
        if (track.local) return@withContext ResolvedAudio(track.url)
        // The full extractor also fetches video clients, subtitles, and recommendations.
        // Reuse its Android player helper for audio, with the complete extractor as fallback.
        try {
            val id = youtube.getStreamLHFactory().fromUrl(track.url).id
            val cpn = YoutubeParsingHelper.generateContentPlaybackNonce()
            val response = YoutubeStreamHelper.getAndroidReelPlayerResponse(ContentCountry("ID"), Localization("en", "US"), id, cpn)
            if (response.getObject("playabilityStatus").getString("status") == "OK" && response.getObject("videoDetails").getString("videoId") == id) {
                val streams = response.getObject("streamingData").getArray("adaptiveFormats").mapNotNull { value ->
                    val format = value as? com.grack.nanojson.JsonObject ?: return@mapNotNull null
                    val mime = format.getString("mimeType").orEmpty()
                    val url = format.getString("url").orEmpty()
                    if (!mime.startsWith("audio/") || !url.startsWith("https://")) null else
                        ResolvedAudio(url.toHttpUrl().newBuilder().addQueryParameter("cpn", cpn).build().toString(), mime.substringBefore(';'), format.getInt("bitrate") / 1000)
                }.sortedBy { it.bitrate }
                val audio = when (quality) { "low" -> streams.firstOrNull(); "medium" -> streams.minByOrNull { kotlin.math.abs(it.bitrate - 128) }; else -> streams.lastOrNull() }
                if (audio != null) return@withContext audio
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Source changes fall back to the supported extractor. */ }
        resolveFallback(track, quality)
    }
    override suspend fun resolveFallback(track: Track, quality: String): ResolvedAudio = withContext(Dispatchers.IO) {
        val extractor = youtube.getStreamExtractor(track.url).apply { fetchPage() }
        val streams = extractor.audioStreams.filter { it.isUrl }.sortedBy { it.averageBitrate }
        val stream = when (quality) { "low" -> streams.firstOrNull(); "medium" -> streams.minByOrNull { kotlin.math.abs(it.averageBitrate - 128) }; else -> streams.lastOrNull() } ?: error("Audio tidak tersedia untuk lagu ini.")
        ResolvedAudio(stream.content, stream.format?.mimeType, stream.averageBitrate)
    }
    override suspend fun fromUrl(url: String): Track = withContext(Dispatchers.IO) {
        val uri = java.net.URI(url)
        require(uri.host in setOf("youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com", "youtu.be")) { "Tautan YouTube tidak valid" }
        val info = StreamInfo.getInfo(youtube, url)
        Track(info.id, info.name, info.uploaderName.orEmpty(), artwork(info.thumbnails.lastOrNull()?.url.orEmpty()), info.duration, info.url)
    }
    override suspend fun related(track: Track): List<Track> = withContext(Dispatchers.IO) {
        if (track.local) emptyList() else StreamInfo.getInfo(youtube, track.url).relatedItems.orEmpty().mapNotNull { it.item()?.track }.distinctBy { it.id }
    }
    private fun artwork(url: String): String {
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        return if (host.endsWith(".googleusercontent.com") || host.endsWith(".ggpht.com")) url.replace(Regex("=w\\d+-h\\d+"), "=w512-h512").replace(Regex("=s\\d+"), "=s512") else url
    }
}
