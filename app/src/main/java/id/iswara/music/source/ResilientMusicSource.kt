package id.iswara.music.source

import id.iswara.music.data.LibraryRepository
import id.iswara.music.data.Preferences
import id.iswara.music.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URI
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

data class SourceCandidate(val track:Track,val provider:String,val confidence:Double)
data class SourceSubstitution(val original:String,val track:Track,val provider:String,val automatic:Boolean)

object SourceMatchRules {
    private val ignored=Regex("\\b(official|video|audio|lyrics?|visualizer|hd|4k|topic|vevo)\\b",RegexOption.IGNORE_CASE)
    private val versions=Regex("\\b(live|cover|remix|instrumental|acoustic|akustik|karaoke|nightcore|slowed|sped|reverb)\\b",RegexOption.IGNORE_CASE)
    fun clean(value:String)=Normalizer.normalize(Normalizer.normalize(value,Normalizer.Form.NFKD).replace(Regex("(?<=\\p{IsLatin})\\p{M}+"),""),Normalizer.Form.NFC).lowercase(java.util.Locale.ROOT).replace(ignored," ").replace(Regex("[^\\p{L}\\p{N}\\p{M}]+")," ").trim().replace(Regex(" +")," ")
    private fun similarity(a:String,b:String):Double {
        if(a.isBlank() || b.isBlank())return 0.0
        if(a==b)return 1.0
        val first=a.split(' ').toSet();val second=b.split(' ').toSet()
        return 2.0*first.intersect(second).size/(first.size+second.size)
    }
    private fun title(track:Track):String {val name=clean(track.title);val artist=clean(track.artist);return if(artist.isNotBlank() && name.startsWith("$artist "))name.removePrefix("$artist ")else name}
    fun confidence(original:Track,candidate:Track):Double {
        if(original.local || candidate.local || original.duration<=0 || candidate.duration<=0)return 0.0
        if(versions.findAll(original.title).map {it.value.lowercase()}.toSet()!=versions.findAll(candidate.title).map {it.value.lowercase()}.toSet())return 0.0
        val difference=abs(original.duration-candidate.duration)
        if(difference>min(10L,max(4L,(original.duration*.04).toLong())))return 0.0
        val title=similarity(title(original),title(candidate))
        val artist=max(similarity(clean(original.artist),clean(candidate.artist)),if(clean(candidate.title).contains(clean(original.artist)) && original.artist.isNotBlank())1.0 else 0.0)
        if(title<.85 || artist<.5)return 0.0
        return title*.65+artist*.25+(1-difference.toDouble()/max(1,original.duration))*.1
    }
    fun validUrl(url:String):Boolean=runCatching {val uri=URI(url);uri.scheme=="https" && uri.userInfo==null && uri.host in setOf("youtube.com","www.youtube.com","music.youtube.com","m.youtube.com","youtu.be","soundcloud.com","www.soundcloud.com","m.soundcloud.com")}.getOrDefault(false)
}

@Singleton class ResilientMusicSource @Inject constructor(private val youtube:YoutubeMusicSource,private val preferences:Preferences,private val library:LibraryRepository):MusicSource {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).callTimeout(7,TimeUnit.SECONDS).build()
    val substitutions=MutableStateFlow<Map<String,SourceSubstitution>>(emptyMap())
    private val probeFailed=java.util.concurrent.ConcurrentHashMap<String,Long>()
    init {scope.launch {library.scope.collect {substitutions.value=emptyMap();probeFailed.clear()}}}
    override suspend fun search(query:String,kind:SearchKind,next:String?)=youtube.search(query,kind,next)
    override suspend fun collection(url:String,next:String?)=youtube.collection(url,next)
    override suspend fun fromUrl(url:String)=youtube.fromUrl(url)
    override suspend fun related(track:Track):List<Track> = try {youtube.related(track)}catch(e:CancellationException){throw e}catch(_:Exception){youtube.search("${track.artist} ${track.title}").items.mapNotNull {it.track}.filterNot {it.id==track.id}}
    private fun key(owner:String,id:String)="sourceMatch:$owner:$id"
    private suspend fun saved(track:Track):Track? = try {preferences.raw(key(library.scope.value,track.id)).first()?.let {AppJson.decodeFromString<Track>(it)}?.takeIf {SourceMatchRules.validUrl(it.url)}}catch(e:CancellationException){throw e}catch(_:Exception){null}
    suspend fun choose(original:Track,candidate:Track) {
        require(SourceMatchRules.validUrl(candidate.url) && !original.local)
        val owner=library.scope.value
        val audio=resolveCandidate(candidate,"medium")
        check(probe(audio)) {"Sumber pilihan belum bisa diputar. Coba pilihan lain."}
        check(owner==library.scope.value) {"Akun berubah. Pilih sumber lagi."}
        preferences.setOwnerValue(owner,key(owner,original.id),AppJson.encodeToString(candidate))
        substitutions.update {it+(original.id to SourceSubstitution(original.id,candidate,provider(candidate.url),false))}
    }
    suspend fun original(track:Track) {preferences.setOwnerValue(library.scope.value,key(library.scope.value,track.id),"");substitutions.update {it-track.id}}
    private fun provider(url:String)=if(runCatching {URI(url).host.endsWith("soundcloud.com")}.getOrDefault(false))"alternative" else "video"
    override suspend fun resolve(track:Track,quality:String):ResolvedAudio {
        if(track.local)return ResolvedAudio(track.url)
        saved(track)?.let {alternative->try {return resolveCandidate(alternative,quality).also {remember(track,alternative,false)}}catch(e:CancellationException){throw e}catch(_:Exception){}}
        return try {youtube.resolve(track,quality).also {substitutions.update {it-track.id}}}catch(e:CancellationException){throw e}catch(original:Exception) {
            if(!preferences.settings.first().sourceFallback)throw original
            rescue(track,quality)
        }
    }
    override suspend fun resolveFallback(track:Track,quality:String):ResolvedAudio {
        saved(track)?.let {alternative->try {val audio=resolveCandidate(alternative,quality);if(probe(audio))return audio.also {remember(track,alternative,false)}}catch(e:CancellationException){throw e}catch(_:Exception){}}
        try {val audio=youtube.resolveFallback(track,quality);if(probe(audio))return audio.also {substitutions.update {it-track.id}}}catch(e:CancellationException){throw e}catch(_:Exception){}
        check(preferences.settings.first().sourceFallback) {"Audio asli belum tersedia. Aktifkan sumber cadangan atau coba lagu lain."}
        return rescue(track,quality)
    }
    private fun remember(original:Track,chosen:Track,automatic:Boolean) {substitutions.update {old->(old+(original.id to SourceSubstitution(original.id,chosen,provider(chosen.url),automatic))).entries.toList().takeLast(64).associate {it.toPair()}}}
    private suspend fun rescue(track:Track,quality:String):ResolvedAudio=withTimeout(45_000) {
        check(track.title.isNotBlank() && track.artist.isNotBlank()) {"Lagu belum tersedia dan metadata belum cukup untuk mencari cadangan."}
        val candidates=candidates(track).filter {it.confidence>=.88}.take(4)
        for(candidate in candidates) {
            currentCoroutineContext().ensureActive()
            try {val audio=resolveCandidate(candidate.track,quality);if(probe(audio))return@withTimeout audio.also {remember(track,candidate.track,true)}}catch(e:CancellationException){throw e}catch(_:Exception){}
        }
        error("Belum ada sumber cadangan yang cocok dan dapat diputar. Koleksimu tetap tersimpan. Coba pilih sumber secara manual.")
    }
    suspend fun candidates(original:Track,query:String="${original.artist} ${original.title}"):List<SourceCandidate> = withContext(Dispatchers.IO) {
        require(query.trim().isNotBlank()) {"Masukkan judul dan artis."}
        supervisorScope {
            val providers=listOf(ServiceList.YouTube to "video",ServiceList.SoundCloud to "alternative")
            providers.map {(service,label)->async {
                try {
                    withTimeout(16_000) {
                        val extractor=service.getSearchExtractor(query.take(200),if(label=="alternative")listOf("tracks")else emptyList(),"").apply {fetchPage()}
                        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().take(12).map {row->
                            val id=if(label=="video")runCatching {ServiceList.YouTube.streamLHFactory.fromUrl(row.url).id}.getOrDefault(row.url)else "sc:"+row.url.hashCode().toUInt().toString(16)
                            val track=Track(id,row.name,row.uploaderName.orEmpty(),row.thumbnails.lastOrNull()?.url.orEmpty(),row.duration.coerceAtLeast(0),row.url)
                            SourceCandidate(track,label,SourceMatchRules.confidence(original,track))
                        }.filter {it.track.url!=original.url && SourceMatchRules.validUrl(it.track.url)}
                    }
                }catch(e:TimeoutCancellationException){emptyList()}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()}
            }}.awaitAll().flatten().distinctBy {it.track.url}.sortedByDescending {it.confidence}
        }
    }
    suspend fun candidateFromUrl(url:String):Track=withContext(Dispatchers.IO) {
        require(SourceMatchRules.validUrl(url)) {"Tautan sumber tidak didukung."}
        if(provider(url)=="video")youtube.fromUrl(url)else {
            val extractor=ServiceList.SoundCloud.getStreamExtractor(url).apply {fetchPage()}
            Track("sc:"+extractor.id,extractor.name,extractor.uploaderName,extractor.thumbnails.lastOrNull()?.url.orEmpty(),extractor.length,url)
        }
    }
    private suspend fun resolveCandidate(track:Track,quality:String):ResolvedAudio=withContext(Dispatchers.IO) {
        require(SourceMatchRules.validUrl(track.url))
        if(provider(track.url)=="video")return@withContext youtube.resolve(track,quality)
        val extractor=ServiceList.SoundCloud.getStreamExtractor(track.url).apply {fetchPage()}
        val streams=extractor.audioStreams.filter {it.isUrl && it.deliveryMethod==DeliveryMethod.PROGRESSIVE_HTTP}.sortedBy {it.averageBitrate}
        val audio=when(quality){"low"->streams.firstOrNull();"high"->streams.lastOrNull();else->streams.minByOrNull {abs(it.averageBitrate-128)}} ?: error("Format sumber ini belum mendukung pemutaran langsung.")
        ResolvedAudio(audio.content,audio.format?.mimeType,audio.averageBitrate)
    }
    private suspend fun probe(audio:ResolvedAudio):Boolean=withContext(Dispatchers.IO) {
        if((probeFailed[audio.url] ?: 0)>System.currentTimeMillis())return@withContext false
        val okay=runCatching {client.newCall(Request.Builder().url(audio.url).header("Range","bytes=0-0").build()).execute().use {response->response.isSuccessful && response.body?.byteStream()?.read()?.let {it>=0}==true}}.getOrDefault(false)
        if(!okay){if(probeFailed.size>32)probeFailed.clear();probeFailed[audio.url]=System.currentTimeMillis()+30_000}
        okay
    }
}
