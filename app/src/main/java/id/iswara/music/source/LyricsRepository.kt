package id.iswara.music.source

import id.iswara.music.data.IswaraDatabase
import id.iswara.music.data.LibraryRepository
import id.iswara.music.data.LyricsCacheRow
import id.iswara.music.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class Lyrics(val lines: List<LyricLine> = emptyList(), val plain: String = "", val instrumental: Boolean = false, val source: String = "") {
    val available get() = instrumental || lines.isNotEmpty() || plain.isNotBlank()
}
data class LyricCandidate(val id: String, val title: String, val artist: String, val duration: Long, val provider: String,
    val synced: String = "", val plain: String = "", val instrumental: Boolean = false, val accessKey: String = "")

object LyricsRules {
    fun cleanTitle(title: String, artist: String): String = title
        .replace(Regex("(?i)\\([^)]*(official|music video|lyrics?|visualizer|\\bhd\\b|\\b4k\\b)[^)]*\\)"), "")
        .replace(Regex("(?i)\\[[^]]*(official|music video|lyrics?|visualizer|\\bhd\\b|\\b4k\\b)[^]]*]"), "")
        .replace(Regex("(?i)^\\s*${Regex.escape(cleanArtist(artist))}\\s*[-–—|:]\\s*"), "").trim()
    fun cleanArtist(artist: String) = artist.replace(Regex("(?i)\\s*-\\s*Topic$|VEVO$|\\s+Official$"), "").trim()
    private fun normalize(text: String) = SourceMatchRules.clean(text)
    private fun similarity(a: String, b: String): Double {
        val x = normalize(a).split(' ').filter { it.isNotBlank() }.toSet()
        val y = normalize(b).split(' ').filter { it.isNotBlank() }.toSet()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size
    }
    fun score(track: Track, candidate: LyricCandidate): Double {
        val title = cleanTitle(track.title, track.artist)
        val artist = cleanArtist(track.artist)
        val versions = listOf("live", "remix", "acoustic", "instrumental", "karaoke", "cover", "sped", "slowed")
        if (versions.any { normalize(title).split(' ').contains(it) != normalize(candidate.title).split(' ').contains(it) }) return 0.0
        if (track.duration > 0 && candidate.duration > 0 && kotlin.math.abs(track.duration - candidate.duration) > maxOf(10, track.duration * 6 / 100)) return 0.0
        val titleScore = similarity(title, candidate.title)
        val artistScore = similarity(artist, candidate.artist)
        if (titleScore < .65 || artistScore < .4) return 0.0
        return titleScore * .7 + artistScore * .3
    }
}

@Singleton class LyricsRepository @Inject constructor(private val database: IswaraDatabase, private val library: LibraryRepository) {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<String, Deferred<Lyrics>>()
    private val lock = Mutex()
    private val deletedOwners = mutableSetOf<String>()
    private val missing=ConcurrentHashMap<String,Long>()
    private val dao get() = database.experience()
    private fun metadata(track: Track) = "${track.title}\n${track.artist}\n${track.duration}"
    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun request(host: String, path: String, params: Map<String, String>): JsonElement {
        val url = HttpUrl.Builder().scheme("https").host(host).addPathSegments(path).apply { params.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        val req = Request.Builder().url(url).header("User-Agent", "iSwara Music/0.9.0").apply { if (host.endsWith("kugou.com")) header("Referer", "https://www.kugou.com/");if(host=="music.163.com")header("Referer","https://music.163.com/") }.build()
        client.newCall(req).execute().use { response ->
            if (response.code == 404) return JsonNull
            if (!response.isSuccessful) throw IOException("Sumber lirik belum terhubung (${response.code})")
            val body = response.body ?: throw IOException("Lirik kosong")
            val output = java.io.ByteArrayOutputStream()
            body.byteStream().use { stream ->
                val buffer = ByteArray(8192)
                while (true) { val n = stream.read(buffer); if (n < 0) break; require(output.size() + n <= 1_500_000); output.write(buffer,0,n) }
            }
            return AppJson.parseToJsonElement(output.toString("UTF-8"))
        }
    }
    private fun fromLrcJson(value: JsonObject): Lyrics = Lyrics(MusicRules.parseLyrics(value.text("syncedLyrics")), value.text("plainLyrics"), value["instrumental"]?.jsonPrimitive?.booleanOrNull == true, "lrclib")
    private fun exact(track: Track): Lyrics? {
        val params = mutableMapOf("track_name" to LyricsRules.cleanTitle(track.title, track.artist), "artist_name" to LyricsRules.cleanArtist(track.artist))
        if (track.duration > 0) params["duration"] = track.duration.toString()
        return (request("lrclib.net", "api/get", params) as? JsonObject)?.let(::fromLrcJson)?.takeIf { it.available }
    }
    private fun searchLrc(query: String): List<LyricCandidate> = (request("lrclib.net", "api/search", mapOf("q" to query)) as? JsonArray).orEmpty().take(15).mapNotNull { element ->
        val value = element as? JsonObject ?: return@mapNotNull null
        LyricCandidate("lrclib:${value.text("id")}", value.text("trackName"), value.text("artistName"), value["duration"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0, "lrclib", value.text("syncedLyrics"), value.text("plainLyrics"), value["instrumental"]?.jsonPrimitive?.booleanOrNull == true)
    }
    private fun searchKugou(query: String): List<LyricCandidate> {
        val value = request("lyrics.kugou.com", "search", mapOf("ver" to "1", "man" to "yes", "keyword" to query)) as? JsonObject ?: return emptyList()
        return (value["candidates"] as? JsonArray).orEmpty().take(20).mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            if (item.text("accesskey").isBlank() || item.text("id").isBlank()) return@mapNotNull null
            LyricCandidate("kugou:${item.text("id")}", item.text("song"), item.text("singer"), (item["duration"]?.jsonPrimitive?.longOrNull ?: 0) / 1000, "kugou", accessKey = item.text("accesskey"))
        }
    }
    private suspend fun <T> optional(fallback: T, block: () -> T): T = try { currentCoroutineContext().ensureActive(); block() } catch (e: CancellationException) { throw e } catch (_: Exception) { fallback }
    private fun searchNetease(query:String):List<LyricCandidate> {
        val data=request("music.163.com","api/search/get",mapOf("s" to query,"type" to "1","limit" to "15","offset" to "0")) as? JsonObject ?: return emptyList()
        val rows=(data["result"] as? JsonObject)?.get("songs") as? JsonArray ?: return emptyList()
        return rows.take(15).mapNotNull {row->val song=row as? JsonObject ?: return@mapNotNull null
            val artist=(song["artists"] as? JsonArray).orEmpty().mapNotNull {(it as? JsonObject)?.text("name")}.joinToString(" ")
            val id=song.text("id");if(id.toLongOrNull()==null)return@mapNotNull null
            LyricCandidate("netease:$id",song.text("name"),artist,(song.text("duration").toLongOrNull() ?: 0)/1000,"netease")
        }
    }
    suspend fun search(query: String): List<LyricCandidate> = withContext(Dispatchers.IO) {
        require(query.trim().length >= 2) { "Isi judul atau artis terlebih dahulu." }
        val text = query.trim().take(200)
        coroutineScope { listOf(async { optional(emptyList()) { searchLrc(text) } }, async { optional(emptyList()) { searchKugou(text) } },async {optional(emptyList()){searchNetease(text)}}).awaitAll().flatten().distinctBy { it.id }.take(45) }
    }
    private fun fetch(candidate: LyricCandidate): Lyrics {
        if (candidate.provider == "lrclib") return Lyrics(MusicRules.parseLyrics(candidate.synced), candidate.plain, candidate.instrumental, "lrclib")
        if(candidate.provider=="netease") {
            val value=request("music.163.com","api/song/lyric",mapOf("id" to candidate.id.substringAfter(':'),"lv" to "-1","kv" to "-1","tv" to "-1")) as? JsonObject ?: return Lyrics()
            val raw=(value["lrc"] as? JsonObject)?.text("lyric").orEmpty();val lines=MusicRules.parseLyrics(raw)
            return Lyrics(lines,if(lines.isEmpty())raw else lines.joinToString("\n"){it.text},source="netease")
        }
        val value = request("lyrics.kugou.com", "download", mapOf("ver" to "1", "client" to "pc", "id" to candidate.id.substringAfter(':'), "accesskey" to candidate.accessKey, "fmt" to "lrc", "charset" to "utf8")) as? JsonObject ?: return Lyrics()
        val encoded = value.text("content")
        require(encoded.length <= 1_000_000)
        val raw = String(Base64.getDecoder().decode(encoded.filterNot(Char::isWhitespace)), Charsets.UTF_8).removePrefix("\uFEFF")
        val lines = MusicRules.parseLyrics(raw)
        return Lyrics(lines, if (lines.isEmpty()) raw else lines.joinToString("\n") { it.text }, source = "kugou")
    }
    suspend fun get(track: Track, refresh: Boolean = false): Lyrics {
        val owner = library.scope.value
        val cached = withContext(Dispatchers.IO) { dao.lyrics(owner, track.id) }
        if (cached != null && (cached.manual || (!refresh && cached.metadata == metadata(track) && System.currentTimeMillis() - cached.modified < 30L * 86400_000)))
            runCatching { AppJson.decodeFromString<Lyrics>(cached.payload) }.getOrNull()?.let { return it }
        val key = "$owner:${track.id}:${metadata(track)}"
        if(!refresh && (missing[key] ?: 0)>System.currentTimeMillis())return Lyrics()
        val task = pending.computeIfAbsent(key) { scope.async(start = CoroutineStart.LAZY) {
            val direct = optional<Lyrics?>(null) { exact(track) }
            val result = direct?.takeIf {it.lines.isNotEmpty() || it.instrumental} ?: run {
                val query = "${LyricsRules.cleanTitle(track.title, track.artist)} ${LyricsRules.cleanArtist(track.artist)}"
                val candidates = search(query).map { it to LyricsRules.score(track,it) }.filter { it.second >= .82 }.sortedWith(compareByDescending<Pair<LyricCandidate,Double>> {it.second}.thenByDescending {it.first.synced.isNotBlank()})
                var found: Lyrics? = null
                for ((candidate, _) in candidates.take(5)) { currentCoroutineContext().ensureActive(); val fetched = optional<Lyrics?>(null) { fetch(candidate) }; if(fetched?.available==true && found==null)found=fetched;if(fetched?.lines?.isNotEmpty()==true || fetched?.instrumental==true){found=fetched;break} }
                found ?: direct ?: Lyrics()
            }
            currentCoroutineContext().ensureActive()
            if (result.available) store(owner, track, result, false)
            else {if(missing.size>128)missing.clear();missing[key]=System.currentTimeMillis()+5*60_000}
            result
        } }
        task.invokeOnCompletion { pending.remove(key, task) }
        return task.await()
    }
    private suspend fun store(owner: String, track: Track, lyrics: Lyrics, manual: Boolean) = lock.withLock {
        if (owner in deletedOwners) return@withLock
        val old = dao.lyrics(owner, track.id)
        if (old?.manual == true && !manual) return@withLock
        dao.saveLyrics(LyricsCacheRow(owner, track.id, metadata(track), AppJson.encodeToString(lyrics), manual, System.currentTimeMillis()))
        dao.pruneLyrics(owner)
    }
    suspend fun choose(track: Track, candidate: LyricCandidate, owner: String = library.scope.value): Lyrics = withContext(Dispatchers.IO) {
        val result = fetch(candidate)
        require(result.available) { "Lirik pilihan ini belum tersedia. Pilih hasil lain." }
        check(owner == library.scope.value) { "Akun berubah." }
        store(owner,track,result,true); result
    }
    suspend fun import(track: Track, raw: String, owner: String = library.scope.value): Lyrics = withContext(Dispatchers.IO) {
        require(raw.length <= 500_000) { "Berkas lirik terlalu besar." }
        val lines = MusicRules.parseLyrics(raw.removePrefix("\uFEFF"))
        require(lines.isNotEmpty() && lines.size <= 5000) { "Berkas LRC harus berisi timestamp [mm:ss.xx]." }
        check(owner == library.scope.value) { "Akun berubah." }
        Lyrics(lines, lines.joinToString("\n") { it.text }, source = "import").also { store(owner, track, it, true) }
    }
    suspend fun reset(track: Track, owner: String = library.scope.value) = withContext(Dispatchers.IO) { lock.withLock { check(owner == library.scope.value); dao.deleteLyrics(owner, track.id) } }
    suspend fun clearOwner(owner: String) = withContext(Dispatchers.IO) { lock.withLock {
        deletedOwners += owner
        pending.filterKeys { it.startsWith("$owner:") }.forEach { (k,v) -> v.cancel(); pending.remove(k,v) }
        dao.clearLyrics(owner)
    } }
}
