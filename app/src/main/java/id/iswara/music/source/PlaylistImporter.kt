package id.iswara.music.source

import id.iswara.music.data.LibraryRepository
import id.iswara.music.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

data class ImportSong(val title:String,val artist:String="",val duration:Long=0,val direct:Track?=null)
data class ImportList(val title:String,val songs:List<ImportSong>,val warning:String="")
data class ImportRow(val song:ImportSong,val candidates:List<Track> = emptyList(),val chosen:Track?=null,val selected:Boolean=false,val done:Boolean=false,val error:String?=null)
data class ImportState(val owner:String="",val title:String="",val rows:List<ImportRow> = emptyList(),val loading:Boolean=false,val matching:Boolean=false,val completed:Int=0,val warning:String="",val error:String?=null,val saved:String?=null)
object ImportRules {
    fun duration(value:String,milliseconds:Boolean=false):Long {
        val raw=value.trim();return if(':' in raw)raw.split(':').fold(0L){total,part->total*60+(part.toLongOrNull() ?: 0)}.coerceIn(0,86400)else ((raw.toDoubleOrNull() ?: 0.0)/(if(milliseconds)1000 else 1)).toLong().coerceIn(0,86400)
    }
    fun csv(text:String):List<List<String>> {
        val rows=mutableListOf<List<String>>();val row=mutableListOf<String>();val field=StringBuilder();var quoted=false;var i=0
        val first=text.lineSequence().firstOrNull().orEmpty();val delimiter=if(first.count {it==';'}>first.count {it==','})';'else ','
        while(i<text.length){val c=text[i++];when {c=='"'->if(quoted && i<text.length && text[i]=='"'){field.append('"');i++}else quoted=!quoted
            c==delimiter && !quoted->{row+=field.toString();field.clear();require(row.size<=256)}
            (c=='\n' || c=='\r') && !quoted->{if(c=='\r' && i<text.length && text[i]=='\n')i++;row+=field.toString();field.clear();if(row.any {it.isNotBlank()})rows+=row.toList();row.clear();require(rows.size<=5000)}
            else->field.append(c)};require(field.length<=12000)}
        require(!quoted){"Kutipan CSV belum ditutup."};if(field.isNotEmpty() || row.isNotEmpty()){row+=field.toString();rows+=row.toList()};return rows
    }
    private fun jsonSongs(value:JsonElement,depth:Int=0):List<ImportSong> {
        if(depth>10)return emptyList()
        if(value is JsonArray)return value.take(2000).flatMap {jsonSongs(it,depth+1)}.take(2000)
        val data=value as? JsonObject ?: return emptyList()
        fun text(vararg keys:String)=keys.firstNotNullOfOrNull {(data[it] as? JsonPrimitive)?.contentOrNull}.orEmpty()
        val title=text("title","trackName","Track Name","name")
        val artist=text("artist","artistName","Artist Name(s)","creator").ifBlank {(data["artists"] as? JsonArray).orEmpty().mapNotNull {(it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull}.joinToString(" ")}
        if(title.isNotBlank() && (artist.isNotBlank() || data.keys.none {it in listOf("tracks","items","songs","data")}))return listOf(ImportSong(title.take(300),artist.take(300),duration(text("duration_ms","duration"),data.containsKey("duration_ms"))))
        return listOf("tracks","items","songs","data","track","attributes").flatMap {key->data[key]?.let {jsonSongs(it,depth+1)}.orEmpty()}.take(2000)
    }
    fun parse(raw:String):ImportList {
        require(raw.toByteArray().size<=2_000_000){"Daftar terlalu besar. Batas berkas 2 MB."}
        val text=raw.removePrefix("\uFEFF").trim();require(text.isNotBlank()){ "Isi daftar lagu terlebih dahulu." }
        if(text.startsWith('[') || text.startsWith('{'))return ImportList("Playlist impor",jsonSongs(AppJson.parseToJsonElement(text)))
        val rows=csv(text)
        fun normalize(s:String)=s.lowercase().replace(Regex("[^a-z]"),"")
        val headers=rows.firstOrNull().orEmpty().map(::normalize)
        val titleColumn=headers.indexOfFirst {it in setOf("trackname","title","song","songname","track","name")}
        if(titleColumn>=0) {
            val artist=headers.indexOfFirst {it in setOf("artist","artists","artistname","artistnames","creator")};val length=headers.indexOfFirst {it.startsWith("duration")}
            return ImportList("Playlist impor",rows.drop(1).mapNotNull {row->val title=row.getOrNull(titleColumn)?.trim().orEmpty();if(title.isBlank())null else ImportSong(title.take(300),row.getOrNull(artist)?.trim().orEmpty().take(300),duration(row.getOrNull(length).orEmpty(),headers.getOrNull(length)?.contains("ms")==true))})
        }
        val songs=mutableListOf<ImportSong>();var ext:String?=null
        text.lines().forEach {line->val clean=line.trim();when {
            clean.startsWith("#EXTINF:")->ext=clean.substringAfter(',').trim()
            clean.isBlank() || clean.startsWith('#')->{}
            ext!=null->{val parts=ext!!.split(Regex("\\s+[–—-]\\s+"),limit=2);songs+=if(parts.size==2)ImportSong(parts[1],parts[0])else ImportSong(ext!!);ext=null}
            clean.startsWith("http") || clean.startsWith("file:") || clean.startsWith('/') || clean.matches(Regex("^[A-Za-z]:.*")) -> {}
            else->{val parts=clean.replace(Regex("^\\d+[.)]\\s*"),"").split(Regex("\\s+[–—|-]\\s+"),limit=2);songs+=if(parts.size==2)ImportSong(parts[1].take(300),parts[0].take(300))else ImportSong(parts[0].take(300))}
        }}
        return ImportList("Playlist impor",songs)
    }
    fun score(song:ImportSong,candidate:Track):Double {
        fun similarity(a:String,b:String):Double {val x=SourceMatchRules.clean(a).split(' ').filter(String::isNotBlank).toSet();val y=SourceMatchRules.clean(b).split(' ').filter(String::isNotBlank).toSet();return if(x.isEmpty() || y.isEmpty())0.0 else 2.0*x.intersect(y).size/(x.size+y.size)}
        val versions=Regex("(?i)\\b(live|cover|remix|slowed|sped|karaoke|instrumental|acoustic|akustik)\\b")
        if(versions.findAll(song.title).map {it.value.lowercase()}.toSet()!=versions.findAll(candidate.title).map {it.value.lowercase()}.toSet())return 0.0
        if(song.duration>0 && candidate.duration>0 && abs(song.duration-candidate.duration)>max(8,song.duration/20))return 0.0
        val title=similarity(song.title,LyricsRules.cleanTitle(candidate.title,candidate.artist));val artist=similarity(song.artist,LyricsRules.cleanArtist(candidate.artist))
        if(title<.8 || song.artist.isNotBlank() && artist<.4)return 0.0
        return title*.7+artist*.3
    }
}

@Singleton class PlaylistImporter @Inject constructor(private val source:MusicSource,private val library:LibraryRepository) {
    val state=MutableStateFlow(ImportState())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val client=OkHttpClient.Builder().followRedirects(false).callTimeout(20,TimeUnit.SECONDS).build()
    private var job:Job?=null
    @Volatile private var generation=0
    private val saveLock=kotlinx.coroutines.sync.Mutex()
    init {scope.launch {library.scope.collect {clear()}}}
    fun clear(){generation++;job?.cancel();state.value=ImportState(owner=library.scope.value)}
    fun cancel(){generation++;job?.cancel();state.update {it.copy(loading=false,matching=false)}}
    private fun current(owner:String,token:Int)=owner==library.scope.value && token==generation
    private fun read(url:String):String {client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0 iSwaraMusic/0.9.0").build()).execute().use {r->check(r.isSuccessful){"Playlist publik belum dapat dibaca. Gunakan berkas ekspor atau daftar teks."};val body=r.body ?: error("Daftar kosong.");val output=java.io.ByteArrayOutputStream();body.byteStream().use {input->val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;check(output.size()+n<=3_000_000){"Halaman terlalu besar. Gunakan berkas ekspor."};output.write(buffer,0,n)}};return output.toString("UTF-8")}}
    suspend fun loadLink(raw:String,limit:Int):ImportList=withContext(Dispatchers.IO) {
        val value=raw.trim().let {if(it.startsWith("spotify:playlist:"))"https://open.spotify.com/playlist/${it.substringAfterLast(':')}"else it}
        val uri=value.toHttpUrl();require(uri.scheme=="https" && uri.username.isEmpty() && uri.password.isEmpty() && uri.port==443)
        when(uri.host) {
            "youtube.com","www.youtube.com","music.youtube.com","m.youtube.com"->{require(uri.queryParameter("list")!=null){"Gunakan tautan playlist, bukan satu lagu."};val first=source.collection(value);val tracks=first.items.mapNotNull {it.track}.toMutableList();var next=first.next;val seen=mutableSetOf<String>();while(next!=null && tracks.size<limit && seen.add(next)){val page=source.collection(value,next);tracks+=page.items.mapNotNull {it.track};next=page.next};ImportList("Playlist impor",tracks.take(limit).map {ImportSong(it.title,it.artist,it.duration,it)},if(next!=null || tracks.size>limit)"Daftar dibatasi $limit lagu. Impor bertahap atau gunakan berkas ekspor."else "")}
            "open.spotify.com"->{val id=Regex("/playlist/([A-Za-z0-9]{22})").find(uri.encodedPath)?.groupValues?.get(1) ?: error("Gunakan tautan playlist publik Spotify.")
                val html=read("https://open.spotify.com/embed/playlist/$id");val json=Regex("<script[^>]+id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>",RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: error("Daftar publik tidak tersedia. Gunakan CSV atau teks.")
                val entity=AppJson.parseToJsonElement(json).jsonObject["props"]!!.jsonObject["pageProps"]!!.jsonObject["state"]!!.jsonObject["data"]!!.jsonObject["entity"]!!.jsonObject
                val songs=(entity["trackList"] as? JsonArray).orEmpty().map {row->val track=row.jsonObject;ImportSong(track["title"]?.jsonPrimitive?.content.orEmpty(),track["subtitle"]?.jsonPrimitive?.content.orEmpty(),(track["duration"]?.jsonPrimitive?.longOrNull ?: 0)/1000)}.filter {it.title.isNotBlank()}
                ImportList(entity["title"]?.jsonPrimitive?.content ?: "Playlist impor",songs.take(limit),"Hanya lagu yang diberikan halaman publik yang terbaca; daftar bisa dibatasi. Gunakan CSV untuk mencocokkan seluruh ekspor.")}
            "soundcloud.com","www.soundcloud.com"->{require("/sets/" in uri.encodedPath);val extractor=ServiceList.SoundCloud.getPlaylistExtractor(value).apply {fetchPage()};val first=extractor.initialPage;val songs=first.items.filterIsInstance<StreamInfoItem>().map {ImportSong(it.name,it.uploaderName.orEmpty(),it.duration)}.take(limit);ImportList(extractor.name,songs,"Daftar publik yang terbaca saja. Gunakan CSV/teks bila jumlahnya belum lengkap.")}
            "deezer.com","www.deezer.com"->{val id=Regex("/playlist/(\\d+)").find(uri.encodedPath)?.groupValues?.get(1) ?: error("Gunakan tautan playlist publik.");val data=AppJson.parseToJsonElement(read("https://api.deezer.com/playlist/$id")).jsonObject;val rows=(data["tracks"] as? JsonObject)?.get("data") as? JsonArray ?: error("Daftar publik tidak tersedia.");ImportList(data["title"]?.jsonPrimitive?.content ?: "Playlist impor",rows.take(limit).map {r->val song=r.jsonObject;ImportSong(song["title"]!!.jsonPrimitive.content,song["artist"]!!.jsonObject["name"]!!.jsonPrimitive.content,song["duration"]!!.jsonPrimitive.long)},"Gunakan berkas ekspor jika daftar publik belum lengkap.")}
            else->error("Platform ini belum menyediakan impor tautan di iSwara. Ekspor CSV/JSON/M3U atau tempel daftar Artis - Judul.")
        }
    }
    fun preview(input:String,link:Boolean,limit:Int=100) {
        cancel();val token=generation;val owner=library.scope.value
        state.value=ImportState(owner=owner,loading=true)
        job=scope.launch {try {
            val list=if(link)loadLink(input,limit.coerceIn(1,500))else ImportRules.parse(input)
            require(list.songs.isNotEmpty()){ "Tidak ada judul lagu yang terbaca. Gunakan CSV dengan kolom title dan artist, atau Artis - Judul per baris." }
            if(!current(owner,token))return@launch
            val songs=list.songs.take(limit.coerceIn(1,500));state.value=ImportState(owner,list.title,songs.map {ImportRow(it)},matching=true,warning=list.warning+if(list.songs.size>songs.size)" Impor dibatasi ${songs.size} lagu."else "")
            val permits=Semaphore(3)
            coroutineScope {songs.mapIndexed {index,song->async {permits.withPermit {
                val row=try {if(song.direct!=null)ImportRow(song,listOf(song.direct),song.direct,true,true)else {
                    val candidates=withTimeout(20_000){source.search("${song.artist} ${song.title}").items.mapNotNull {it.track}.distinctBy {it.id}.sortedByDescending {ImportRules.score(song,it)}.take(5)}
                    val chosen=candidates.firstOrNull()?.takeIf {song.artist.isNotBlank() && ImportRules.score(song,it)>=.88}
                    ImportRow(song,candidates,chosen,chosen!=null,true)
                }}catch(e:TimeoutCancellationException){ImportRow(song,done=true,error="Pencarian terlalu lama.")}catch(e:CancellationException){throw e}catch(_:Exception){ImportRow(song,done=true,error="Belum ditemukan. Pilih manual.")}
                if(current(owner,token))state.update {it.copy(rows=it.rows.toMutableList().apply {this[index]=row},completed=it.completed+1)}
            }}}.awaitAll()}
            if(current(owner,token))state.update {it.copy(matching=false)}
        }catch(e:CancellationException){throw e}catch(e:Exception){if(current(owner,token))state.update {it.copy(loading=false,matching=false,error=e.message?.take(200) ?: "Impor belum berhasil.")}}}
    }
    fun select(index:Int,selected:Boolean){state.update {it.copy(rows=it.rows.mapIndexed {i,row->if(i==index)row.copy(selected=selected && row.chosen!=null)else row})}}
    fun choose(index:Int,track:Track){state.update {it.copy(rows=it.rows.mapIndexed {i,row->if(i==index)row.copy(chosen=track,selected=true)else row})}}
    suspend fun search(query:String)=withTimeout(20_000){source.search(query).items.mapNotNull {it.track}.take(15)}
    suspend fun save(title:String) {
        check(saveLock.tryLock()){ "Playlist sedang disimpan." }
        try {val snapshot=state.value;val token=generation;check(snapshot.owner==library.scope.value && !snapshot.loading && !snapshot.matching && snapshot.saved==null);val tracks=snapshot.rows.filter {it.selected}.mapNotNull {it.chosen}.distinctBy {it.id};require(tracks.isNotEmpty()){ "Pilih lagu yang cocok dahulu." };val playlist=library.createPlaylistWithTracks(title,tracks,snapshot.owner);if(current(snapshot.owner,token))state.update {it.copy(saved=playlist.title)}}finally{saveLock.unlock()}
    }
}
