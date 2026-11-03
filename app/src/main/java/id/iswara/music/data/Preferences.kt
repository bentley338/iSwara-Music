package id.iswara.music.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.model.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("iswara_preferences")
data class Settings(
    val theme: String = "system", val language: String = "id", val quality: String = "medium",
    val dataSaver: Boolean = false, val searchHistory: List<String> = emptyList(),
    val preload: Boolean = true, val skipSilence: Boolean = false, val speed: String = "1",
    val autoplay: Boolean = true, val allowDuplicates: Boolean = false, val playerColors: Boolean = true,
    val equalizer: Boolean = false, val eqPreset: String = "Flat", val bass: String = "0", val reverb: String = "none",
    val eqBands: List<Int> = List(5) { 0 }, val normalize: Boolean = false, val skipUnavailable: Boolean = true,
    val remix: String = "off", val pitch: String = "0", val widgetMotion: Boolean = true, val lyricsMotion: Boolean = true, val lyricsStyle: String = "auto", val lyricsLarge: Boolean = false, val lyricsFollow: Boolean = true,
    val accent:String="orange", val animations:Boolean=true, val warmth:Int=0, val vinyl:Int=0, val spatial:Int=0, val sourceFallback:Boolean=true,
    val smartRomanize:Boolean=false,val smartTranslate:Boolean=false,val smartLanguage:String="auto",val translationWifi:Boolean=true,val translationConsent:Boolean=false,
    val smartTarget:String="id",val updateAuto:Boolean=true,val updateWifi:Boolean=true,val updateChannel:String="stable",val radioMode:String="balanced",
    val lyricsTheme:String="cover",
)
@Singleton
class Preferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val deletedOwners=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun raw(name: String) = context.dataStore.data.map { it[stringPreferencesKey(name)] }
    val settings = context.dataStore.data.map { p ->
        fun text(key: String, default: String) = p[stringPreferencesKey(key)] ?: default
        fun flag(key: String, default: Boolean) = p[booleanPreferencesKey(key)] ?: default
        Settings(theme = text("theme", "system"), language = text("language", "id"), quality = text("quality", "medium"),
            dataSaver = flag("dataSaver", false), searchHistory = runCatching { AppJson.decodeFromString<List<String>>(text("search", "[]")) }.getOrDefault(emptyList()),
            preload = flag("preload", true), skipSilence = flag("skipSilence", false), speed = text("speed", "1"),
            autoplay = flag("autoplay", true), allowDuplicates = flag("allowDuplicates", false), playerColors = flag("playerColors", true),
            equalizer = flag("equalizer", false), eqPreset = text("eqPreset", "Flat"), bass = text("bass", "0"), reverb = text("reverb", "none"),
            eqBands = runCatching { AppJson.decodeFromString<List<Int>>(text("eqBands", "[0,0,0,0,0]")) }.getOrDefault(List(5) { 0 }).takeIf { it.size == 5 } ?: List(5) { 0 }, normalize = flag("normalize", false), skipUnavailable = flag("skipUnavailable", true),
            remix = text("remix", "off"), pitch = text("pitch", "0"), widgetMotion = flag("widgetMotion", true), lyricsMotion = flag("lyricsMotion", true), lyricsStyle = text("lyricsStyle", "auto"), lyricsLarge = flag("lyricsLarge", false), lyricsFollow = flag("lyricsFollow", true),
            accent=text("accent","orange"),animations=flag("animations",true),warmth=text("warmth","0").toIntOrNull()?.coerceIn(0,100) ?: 0,vinyl=text("vinyl","0").toIntOrNull()?.coerceIn(0,100) ?: 0,spatial=text("spatial","0").toIntOrNull()?.coerceIn(0,100) ?: 0,sourceFallback=flag("sourceFallback",true),
            smartRomanize=flag("smartRomanize",false),smartTranslate=flag("smartTranslate",false),smartLanguage=text("smartLanguage","auto"),translationWifi=flag("translationWifi",true),translationConsent=flag("translationConsent",false),
            smartTarget=text("smartTarget","id"),updateAuto=flag("updateAuto",true),updateWifi=flag("updateWifi",true),updateChannel=text("updateChannel","stable"),radioMode=text("radioMode","balanced"),lyricsTheme=text("lyricsTheme","cover"))
    }
    suspend fun remix(mode:String) {require(mode in id.iswara.music.playback.RemixRules.presets.map {it.id});context.dataStore.edit {it[stringPreferencesKey("remix")]=mode}}
    suspend fun adjustPitch(delta:Int) {context.dataStore.edit {p->val key=stringPreferencesKey("pitch");p[key]=((p[key]?.toIntOrNull() ?: 0)+delta.coerceIn(-1,1)).coerceIn(-12,12).toString()}}
    suspend fun pitch(semitones:Int) {context.dataStore.edit {it[stringPreferencesKey("pitch")]=semitones.coerceIn(-12,12).toString()}}
    suspend fun resetRemix() {context.dataStore.edit {it[stringPreferencesKey("remix")]="off";it[stringPreferencesKey("pitch")]="0";it[stringPreferencesKey("speed")]="1"}}
    suspend fun resetAnalog(){context.dataStore.edit {for(key in listOf("warmth","vinyl","spatial"))it[stringPreferencesKey(key)]="0"}}
    suspend fun adjustLyricsOffset(owner: String, track: String, delta: Long, reset: Boolean = false) {
        context.dataStore.edit { p ->
            if(owner in deletedOwners)return@edit
            val key = stringPreferencesKey("lyricsOffset:$owner:$track")
            val old = (p[key]?.toLongOrNull() ?: 0L).coerceIn(-15_000L,15_000L)
            p[key] = (if (reset) 0L else (old + delta.coerceIn(-15_000L,15_000L)).coerceIn(-15_000L,15_000L)).toString()
        }
    }
    suspend fun clearLyricsOffsets(owner: String) {
        context.dataStore.edit { p -> p.asMap().keys.filter { it.name.startsWith("lyricsOffset:$owner:") }.forEach { p.remove(stringPreferencesKey(it.name)) } }
    }
    suspend fun toggle(name: String, value: Boolean) { context.dataStore.edit { it[booleanPreferencesKey(name)] = value } }
    suspend fun set(name: String, value: String) { context.dataStore.edit { it[stringPreferencesKey(name)] = value } }
    suspend fun setOwnerValue(owner:String,name:String,value:String) {context.dataStore.edit {if(owner !in deletedOwners)it[stringPreferencesKey(name)]=value}}
    suspend fun dataSaver(value: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("dataSaver")] = value } }
    suspend fun search(query: String) { context.dataStore.edit { p ->
        val old = runCatching { AppJson.decodeFromString<List<String>>(p[stringPreferencesKey("search")] ?: "[]") }.getOrDefault(emptyList())
        p[stringPreferencesKey("search")] = AppJson.encodeToString((listOf(query) + old).distinct().take(12))
    } }
    suspend fun saveQueue(scope: String, snapshot: QueueSnapshot) { context.dataStore.edit {if(scope !in deletedOwners)it[stringPreferencesKey("queue:$scope")]=AppJson.encodeToString(snapshot)} }
    suspend fun clearOwnerData(owner:String) {
        deletedOwners+=owner
        context.dataStore.edit {p -> p.asMap().keys.filter {it.name in setOf("queue:$owner","stats:$owner","lastTogether:$owner","spacePins:$owner","radioNickname:$owner") || it.name.startsWith("lyricsOffset:$owner:") || it.name.startsWith("sourceMatch:$owner:")}.forEach {p.remove(stringPreferencesKey(it.name))}}
    }
    fun queue(scope: String) = context.dataStore.data.map { p -> runCatching { AppJson.decodeFromString<QueueSnapshot>(p[stringPreferencesKey("queue:$scope")] ?: "{}") }.getOrDefault(QueueSnapshot()) }
}
