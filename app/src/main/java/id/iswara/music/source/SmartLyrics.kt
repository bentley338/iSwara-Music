package id.iswara.music.source

import com.atilika.kuromoji.ipadic.Tokenizer
import com.ibm.icu.text.BreakIterator
import com.ibm.icu.text.Transliterator
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.model.AppJson
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import id.iswara.music.data.LibraryRepository
import id.iswara.music.data.Preferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LyricToken(val text:String,val start:Int,val end:Int,val reading:String="")
data class SmartLine(val text:String,val reading:String,val words:List<LyricToken>)
data class SmartLyricsState(val owner:String="",val track:String="",val key:String="",val language:String="",val rows:Map<String,SmartLine> = emptyMap(),val translations:Map<String,String> = emptyMap(),val busy:Boolean=false,val stage:String="",val percent:Int=0,val error:String?=null,val choice:String="auto",val target:String="id")
data class WordMeaning(val text:String="",val reading:String="",val meaning:String="",val busy:Boolean=false,val error:String?=null)

class LyricRomanizer @Inject constructor() {
    private val japanese by lazy {
        val runtime=Runtime.getRuntime();check(runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())>48L*1024*1024){"Memori belum cukup untuk kamus Jepang. Hentikan proses berat lalu coba lagi."}
        Tokenizer()
    }
    private val kana by lazy {Transliterator.getInstance("Katakana-Latin; Hiragana-Latin; Latin-ASCII")}
    private val korean by lazy {Transliterator.getInstance("Hangul-Latin")}
    private val chinese by lazy {Transliterator.getInstance("Han-Latin")}
    @Synchronized fun line(text:String,language:String):SmartLine {
        if(language=="ja") {
            val words=japanese.tokenize(text).map {token->
                val pronunciation=token.pronunciation?.takeUnless {it=="*"} ?: token.reading?.takeUnless {it=="*"}
                LyricToken(token.surface,token.position,token.position+token.surface.length,pronunciation?.let {kana.transliterate(it)} ?: if(token.surface.any {it in '\u3040'..'\u30ff'})kana.transliterate(token.surface)else token.surface)
            }
            return SmartLine(text,words.joinToString(" "){it.reading}.replace(Regex(" +")," ").trim(),words.filter {it.text.any(Char::isLetterOrDigit)})
        }
        val breaker=BreakIterator.getWordInstance(Locale.forLanguageTag(language.ifBlank {"en"}));breaker.setText(text)
        val words=mutableListOf<LyricToken>();var start=breaker.first();var end=breaker.next()
        while(end!=BreakIterator.DONE){val word=text.substring(start,end);if(word.any(Char::isLetterOrDigit))words+=LyricToken(word,start,end,when(language){"ko"->korean.transliterate(word);"zh"->chinese.transliterate(word);else->word});start=end;end=breaker.next()}
        val reading=when(language){"ko"->korean.transliterate(text);"zh"->chinese.transliterate(text);else->""}
        return SmartLine(text,reading,words)
    }
    fun smartLine(text:String,hint:String,choice:String):SmartLine {
        val script=scriptLanguage(text)
        return line(text,if(choice!="auto")choice else if(script=="zh" && hint=="ja")"ja"else script)
    }
    companion object {
        fun scriptLanguage(text:String)=when {text.any {it in '\u3040'..'\u30ff'}->"ja";text.any {it in '\uac00'..'\ud7af'}->"ko";text.any {it in '\u4e00'..'\u9fff'}->"zh";else->"en"}
        fun fingerprint(lines:List<String>)=MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray()).joinToString(""){"%02x".format(it)}
    }
}

data class TranslationBatch(val language:String,val lines:Map<String,String>)
@Singleton class LyricsTranslation @Inject constructor(@ApplicationContext private val context:Context) {
    private val client=OkHttpClient.Builder().retryOnConnectionFailure(false).connectTimeout(6,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).callTimeout(16,TimeUnit.SECONDS).build()
    @Volatile private var limitedUntil=0L
    private suspend fun request(text:String,language:String,wifi:Boolean,target:String):Pair<String,String> {
        if(wifi){val manager=context.getSystemService(ConnectivityManager::class.java);check(manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true){"Sambungkan Wi-Fi atau matikan pilihan terjemahan hanya lewat Wi-Fi."}}
        check(System.currentTimeMillis()>=limitedUntil) {"Layanan terjemahan sedang membatasi permintaan. Coba lagi sebentar."}
        val url="https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder().addQueryParameter("client","gtx").addQueryParameter("sl",language).addQueryParameter("tl",target).addQueryParameter("dt","t").addQueryParameter("q",text).build()
        val raw=suspendCancellableCoroutine<String> {continuation->
            val call=client.newCall(Request.Builder().url(url).header("User-Agent","iSwaraMusic/0.8.0").build())
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(IOException("Terjemahan belum tersambung."))}
                override fun onResponse(call:Call,response:Response) {response.use {
                    try {
                        if(it.code==429){limitedUntil=System.currentTimeMillis()+60_000;throw IOException("Layanan membatasi permintaan. Coba lagi sebentar.")}
                        check(it.isSuccessful){"Layanan terjemahan belum tersedia (${it.code})."}
                        val body=it.peekBody(256_000).string()
                        if(continuation.isActive)continuation.resume(body)
                    }catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}
                }}
            })
        }
        val data=AppJson.parseToJsonElement(raw).jsonArray
        val translated=data.getOrNull(0)?.jsonArray.orEmpty().joinToString(""){row->(row as? JsonArray)?.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty()}
        check(translated.isNotBlank()){ "Layanan tidak mengembalikan terjemahan." }
        val detected=data.getOrNull(2)?.jsonPrimitive?.contentOrNull?.substringBefore('-') ?: language
        return detected to translated
    }
    suspend fun translate(lines:List<String>,language:String,wifi:Boolean,target:String="id",progress:(String,Int)->Unit):Map<String,String> {
        return batch(lines,language,wifi,target,progress).lines
    }
    suspend fun batch(lines:List<String>,language:String,wifi:Boolean,target:String="id",progress:(String,Int)->Unit):TranslationBatch {
        require(TranslationLanguages.valid(target)){"Bahasa tujuan tidak valid."}
        require(language.matches(Regex("[a-z]{2,4}(?:-[A-Za-z]{2,4})?"))) {"Pilihan bahasa tidak valid."}
        if(wifi){val manager=context.getSystemService(ConnectivityManager::class.java);check(manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true){"Sambungkan Wi-Fi atau matikan pilihan terjemahan hanya lewat Wi-Fi."}}
        val unique=lines.distinct();require(unique.size<=600 && unique.sumOf {it.length}<=100_000)
        if(language==target)return TranslationBatch(language,emptyMap())
        val groups=mutableListOf<MutableList<String>>();var size=0
        for(line in unique){if(groups.isEmpty() || size+line.length>1200 || groups.last().size>=12){groups+=mutableListOf<String>();size=0};groups.last()+=line;size+=line.length+1}
        val result=linkedMapOf<String,String>();var source=language;var allTarget=true;val detected=linkedMapOf<String,Int>()
        groups.forEach {group->
            currentCoroutineContext().ensureActive()
            progress("Menerjemahkan lirik…",result.size*100/unique.size.coerceAtLeast(1))
            val response=request(group.joinToString("\n"){it.replace('\n',' ').replace('\r',' ')},language,wifi,target)
            source=response.first
            detected[source]=(detected[source] ?: 0)+group.size;allTarget=allTarget && source==target
            val translated=response.second.split('\n')
            if(translated.size==group.size)group.zip(translated).forEach {(text,meaning)->result[text]=meaning}
            else for(line in group){result[line]=request(line,language,wifi,target).second;delay(180)}
            delay(180)
        }
        progress("Terjemahan siap",100)
        source=detected.maxByOrNull {it.value}?.key ?: source
        return TranslationBatch(if(allTarget)target else source,if(allTarget)emptyMap()else result)
    }
}

@Singleton class SmartLyrics @Inject constructor(private val library:LibraryRepository,private val romanizer:LyricRomanizer,val translator:LyricsTranslation,preferences:Preferences) {
    val state=MutableStateFlow(SmartLyricsState())
    val word=MutableStateFlow(WordMeaning())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private var prepareJob:Job?=null;private var translationJob:Job?=null;private var wordJob:Job?=null
    private var desired=""
    @Volatile private var requestConfig:Pair<Boolean,String>?=null
    private val cache=java.util.Collections.synchronizedMap(object:LinkedHashMap<String,TranslationBatch>(24,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,TranslationBatch>?)=size>24})
    init {scope.launch {library.scope.collect {prepareJob?.cancel();translationJob?.cancel();wordJob?.cancel();desired="";cache.clear();state.value=SmartLyricsState();word.value=WordMeaning()}}}
    init {scope.launch {preferences.settings.map {Triple(it.smartTranslate && it.translationConsent,it.translationWifi,it.smartTarget)}.distinctUntilChanged().collect {config->if(!config.first || requestConfig?.let {it.first!=config.second || it.second!=config.third}==true)cancelTranslation()}}}
    private fun current(owner:String,key:String)=library.scope.value==owner && desired==key
    fun prepare(track:String,lines:List<String>,language:String="auto",target:String="id") {
        val clean=lines.filter {it.isNotBlank()}
        val owner=library.scope.value
        val key="$owner:$track:$language:$target:${LyricRomanizer.fingerprint(clean)}"
        if(desired==key && state.value.rows.isNotEmpty())return
        prepareJob?.cancel();translationJob?.cancel();wordJob?.cancel();desired=key
        state.value=SmartLyricsState(owner,track,key,busy=true,stage="Menyiapkan lirik pintar…");word.value=WordMeaning()
        prepareJob=scope.launch {
            try {
                require(clean.size<=600 && clean.sumOf {it.length}<=100_000 && clean.all {it.length<=2000}) {"Lirik terlalu panjang untuk mode pintar. Gunakan tampilan lirik biasa."}
                val saved=cache[key]
                val detected=if(language=="auto")LyricRomanizer.scriptLanguage(clean.joinToString("\n"))else language
                val rows=linkedMapOf<String,SmartLine>()
                for(text in clean.distinct()){currentCoroutineContext().ensureActive();rows[text]=romanizer.smartLine(text,detected,language)}
                if(current(owner,key))state.value=SmartLyricsState(owner,track,key,saved?.language ?: detected,rows,translations=saved?.lines.orEmpty(),stage="Siap",choice=language,target=target,percent=if(saved!=null)100 else 0)
            }catch(e:CancellationException){throw e}catch(_:OutOfMemoryError){if(current(owner,key))state.update {it.copy(rows=emptyMap(),busy=false,error="Memori belum cukup. Gunakan lirik biasa atau coba lagi setelah proses berat selesai.")}}catch(e:Exception){if(current(owner,key))state.update {it.copy(busy=false,error=e.message ?: "Lirik pintar belum tersedia.")}}
        }
    }
    fun translate(wifi:Boolean,target:String=state.value.target) {
        if(!TranslationLanguages.valid(target))return
        if(state.value.target!=target){translationJob?.cancel();desired=state.value.key+":$target";state.update {it.copy(key=desired,target=target,translations=emptyMap(),busy=false,percent=0)}}
        val snapshot=state.value
        if(snapshot.rows.isEmpty() || snapshot.busy || !current(snapshot.owner,snapshot.key))return
        requestConfig=wifi to target
        translationJob?.cancel();state.update {it.copy(busy=true,error=null,percent=0)}
        translationJob=scope.launch {
            try {
                val results=translator.batch(snapshot.rows.keys.toList(),snapshot.choice,wifi,snapshot.target){stage,percent->if(current(snapshot.owner,snapshot.key))state.update {it.copy(stage=stage,percent=percent)}}
                if(current(snapshot.owner,snapshot.key)){cache[snapshot.key]=results;state.update {it.copy(translations=results.lines,language=results.language,busy=false,stage=if(results.lines.isEmpty() && results.language==snapshot.target)"Lirik sudah memakai bahasa tujuan."else "Terjemahan siap",percent=100)}}
            }catch(e:TimeoutCancellationException){if(current(snapshot.owner,snapshot.key))state.update {it.copy(busy=false,error="Terjemahan terlalu lama. Coba lagi.")}}
            catch(e:CancellationException){throw e}catch(e:Exception){if(current(snapshot.owner,snapshot.key))state.update {it.copy(busy=false,error=e.message?.take(160) ?: "Terjemahan belum tersedia. Periksa koneksi atau pilih bahasa lirik secara manual.")}}
        }
    }
    fun cancel(){translationJob?.cancel();prepareJob?.cancel();state.update {it.copy(busy=false,stage="Dibatalkan")};desired=if(state.value.rows.isEmpty())"" else desired}
    fun cancelTranslation(){translationJob?.cancel();wordJob?.cancel();if(state.value.rows.isNotEmpty())state.update {it.copy(busy=false)};word.update {it.copy(busy=false)}}
    fun selectWord(token:LyricToken){wordJob?.cancel();word.value=WordMeaning(token.text,token.reading)}
    fun clearWord(){wordJob?.cancel();word.value=WordMeaning()}
    fun translateWord(wifi:Boolean,target:String=state.value.target) {
        val snapshot=state.value;val selected=word.value
        if(selected.text.isBlank() || snapshot.language.isBlank() || !current(snapshot.owner,snapshot.key))return
        requestConfig=wifi to target
        wordJob?.cancel();word.update {it.copy(busy=true,error=null)}
        wordJob=scope.launch {
            try {
                val result=translator.batch(listOf(selected.text),if(snapshot.choice=="auto")"auto"else snapshot.language,wifi,target){_,_->}
                if(current(snapshot.owner,snapshot.key) && word.value.text==selected.text)word.update {it.copy(busy=false,meaning=if(result.language==target)selected.text else result.lines[selected.text].orEmpty())}
            }catch(e:TimeoutCancellationException){if(current(snapshot.owner,snapshot.key))word.update {it.copy(busy=false,error="Terjemahan terlalu lama. Coba lagi.")}}
            catch(e:CancellationException){throw e}catch(e:Exception){if(current(snapshot.owner,snapshot.key))word.update {it.copy(busy=false,error=e.message?.take(160) ?: "Arti kata belum tersedia. Coba lagi.")}}
        }
    }
}
