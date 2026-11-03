@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.data

import android.content.Context
import android.net.Uri
import android.security.keystore.*
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.model.*
import id.iswara.music.playback.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.*
import javax.inject.*

data class StemSelection(val owner:String="",val track:String="",val mode:String="original",val revision:Int=0)
data class StemJob(val track:String,val stage:String,val percent:Int,val running:Boolean,val error:String?)
@Serializable data class StemEntry(val track:Track,val duration:Long,val bytes:Long,val generation:String)

@Singleton class StemStore @Inject constructor(@ApplicationContext private val context:Context,private val library:LibraryRepository,private val offline:OfflineStore,private val streams:StreamResolver) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Mutex()
    private val root=File(context.filesDir,"stems").apply {mkdirs()}
    private val temporary=File(context.cacheDir,"stem-work").apply {mkdirs()}
    private val models=File(context.filesDir,"stem-models").apply {mkdirs()}
    val selection=MutableStateFlow(StemSelection())
    val notice=MutableStateFlow<Pair<String,String>?>(null)
    val revision=MutableStateFlow(0)
    val entries=MutableStateFlow<List<StemEntry>>(emptyList())
    val jobs=WorkManager.getInstance(context).getWorkInfosByTagFlow("stem-separation").combine(library.scope) {infos,owner ->
        val tag="owner:${hash(owner)}"
        infos.filter {tag in it.tags}.map {info->StemJob(info.tags.firstOrNull {it.startsWith("track:")}?.removePrefix("track:").orEmpty(),info.progress.getString("stage") ?: "Menunggu",info.progress.getInt("percent",0),info.state==WorkInfo.State.RUNNING || info.state==WorkInfo.State.ENQUEUED,if(info.state==WorkInfo.State.FAILED)info.outputData.getString("error") ?: "Pemisahan belum berhasil." else null)}
    }.stateIn(scope,SharingStarted.Eagerly,emptyList())
    init {scope.launch {temporary.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}?.forEach {dir->dir.listFiles()?.forEach {it.delete()};dir.delete()} };scope.launch {var old=library.scope.value;library.scope.collectLatest {owner->
        if(old!=owner){WorkManager.getInstance(context).cancelAllWorkByTag("owner:${hash(old)}");original();old=owner}
        refresh(owner)
    }}}
    private fun ownerFolder(owner:String)=File(root,hash(owner)).apply {mkdirs()}
    private fun base(owner:String,track:String)=File(ownerFolder(owner),hash(track))
    private fun meta(owner:String,track:String)=File(base(owner,track).path+".json")
    private fun entry(owner:String,track:String)=runCatching {AppJson.decodeFromString<StemEntry>(meta(owner,track).readText()).also {require(it.track.id==track && it.generation.matches(Regex("[0-9a-f-]{36}")))}}.getOrNull()
    private fun audio(owner:String,track:String,mode:String,generation:String)=File(base(owner,track).path+".$generation.$mode.enc")
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return store.getKey("iswara.stems",null) as? SecretKey ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply {init(KeyGenParameterSpec.Builder("iswara.stems",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
    }
    private fun refresh(owner:String=library.scope.value) {
        val values=ownerFolder(owner).listFiles().orEmpty().filter {it.extension=="json"}.mapNotNull {file ->runCatching {AppJson.decodeFromString<StemEntry>(file.readText())}.getOrNull()}.filter {entry->listOf("vocal","instrumental").all {audio(owner,entry.track.id,it,entry.generation).isFile}}
        if(owner==library.scope.value){entries.value=values;revision.value++}
    }
    fun available(track:String):Boolean {val owner=library.scope.value;val saved=entry(owner,track) ?: return false;return listOf("vocal","instrumental").all {audio(owner,track,it,saved.generation).isFile}}
    fun mode(track:String):String=selection.value.takeIf {it.owner==library.scope.value && it.track==track && available(track)}?.mode ?: "original"
    fun select(track:String,mode:String) {notice.value=null;require(mode in setOf("original","vocal","instrumental"));if(mode!="original")check(available(track)){"Proses AI lagu ini dahulu."};selection.value=StemSelection(library.scope.value,track,mode,selection.value.revision+1)}
    fun original(){selection.value=StemSelection()}
    fun fallback(track:String){notice.value=track to "Hasil AI belum dapat diputar. Kembali ke audio asli.";original()}
    fun ownerTag()=hash(library.scope.value)
    fun enqueue(track:Track) {
        require(track.duration<=8*60) {"Pemisahan maksimal 8 menit per lagu."}
        val owner=library.scope.value
        val request=OneTimeWorkRequestBuilder<StemWorker>().setInputData(workDataOf("owner" to owner,"track" to AppJson.encodeToString(track))).addTag("stem-separation").addTag("owner:${hash(owner)}").addTag("track:${track.id}").build()
        WorkManager.getInstance(context).enqueueUniqueWork("stem:${hash(owner)}:${hash(track.id)}",ExistingWorkPolicy.KEEP,request)
    }
    fun cancel(track:String){WorkManager.getInstance(context).cancelUniqueWork("stem:${hash(library.scope.value)}:${hash(track)}")}
    fun reader(track:String,mode:String,ownerTag:String=this.ownerTag()):StemCipher.Reader {check(ownerTag==this.ownerTag()){ "Akun berubah." };require(mode in setOf("vocal","instrumental"));val owner=library.scope.value;val saved=entry(owner,track) ?: error("Hasil AI tidak tersedia.");return StemCipher.Reader(audio(owner,track,mode,saved.generation),key(),"$owner:$track:$mode:${saved.generation}")}
    suspend fun remove(track:String)=lock.withLock {withContext(Dispatchers.IO) {
        val owner=library.scope.value;cancel(track);if(selection.value.track==track)original()
        ownerFolder(owner).listFiles()?.filter {it.name.startsWith(hash(track)+".")}?.forEach {it.delete()};refresh(owner)
    }}
    suspend fun clearOwner(owner:String) {WorkManager.getInstance(context).cancelAllWorkByTag("owner:${hash(owner)}");lock.withLock {withContext(Dispatchers.IO){ownerFolder(owner).listFiles()?.forEach {it.delete()};if(owner==library.scope.value){original();refresh(owner)}}}}
    suspend fun process(owner:String,track:Track,progress:suspend (String,Int)->Unit)=lock.withLock {withContext(Dispatchers.IO) {
        val coroutine=currentCoroutineContext()
        fun checkpoint(){coroutine.ensureActive();check(library.scope.value==owner){"Akun berubah; proses dihentikan."}}
        checkpoint()
        val manager=context.getSystemService(android.app.ActivityManager::class.java)
        val memory=android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(memory)
        check(memory.availMem>384L*1024*1024){"RAM kosong belum cukup. Tutup aplikasi berat lalu coba lagi."}
        check(!manager.isLowRamDevice){"Pemisahan AI membutuhkan perangkat dengan RAM lebih besar."}
        check(temporary.usableSpace>350L*1024*1024){"Sediakan sedikitnya 350 MB ruang kosong untuk proses AI."}
        require(track.duration<=8*60){"Pemisahan maksimal 8 menit per lagu."}
        val work=File(temporary,java.util.UUID.randomUUID().toString()).apply {mkdirs()}
        val partials=mutableListOf<File>()
        val generation=java.util.UUID.randomUUID().toString()
        var committed=false
        try {
            progress("Menyiapkan audio",2)
            val source=File(work,"input.audio");copyInput(track,source,::checkpoint)
            progress("Membaca audio",12)
            val pcm=File(work,"input.pcm")
            val frames=StemDecoder.decode(context,source,pcm,::checkpoint)
            source.delete()
            check(frames>0){"Audio kosong."}
            val modelFiles=prepareModels(::checkpoint)
            val vocal=File(work,"vocal.wav");val instrument=File(work,"instrumental.wav")
            StemModel(modelFiles[0],modelFiles[1]).use {model ->
                java.io.RandomAccessFile(pcm,"r").use {input ->StemDsp.WavWriter(vocal).use {vout ->StemDsp.WavWriter(instrument).use {iout ->
                    var start=0L;var tail:Array<FloatArray>?=null
                    while(start<frames) {
                        checkpoint();val block=StemDsp.block(input,frames,start-StemDsp.CONTEXT)
                        val separated=model.separate(block,::checkpoint)
                        val count=minOf(StemDsp.CORE.toLong(),frames-start).toInt()
                        val voice=Array(2){ch->separated[ch].copyOfRange(StemDsp.CONTEXT,StemDsp.CONTEXT+count)}
                        tail?.let {old ->for(ch in 0..1)for(i in 0 until minOf(count,old[ch].size)){val t=(.5-.5*kotlin.math.cos(Math.PI*i/StemDsp.OVERLAP)).toFloat();voice[ch][i]=old[ch][i]*(1-t)+voice[ch][i]*t}}
                        val backing=Array(2){ch->FloatArray(count){i->block[ch][StemDsp.CONTEXT+i]-voice[ch][i]}}
                        vout.write(voice[0],voice[1]);iout.write(backing[0],backing[1])
                        tail=Array(2){ch->separated[ch].copyOfRange(StemDsp.CONTEXT+StemDsp.CORE,StemDsp.CONTEXT+StemDsp.CORE+StemDsp.OVERLAP)}
                        start+=count;progress("Memisahkan vokal dan instrumen",15+(start*75/frames).toInt())
                    }
                }}}
            }
            pcm.delete()
            val outputBytes=vocal.length()+instrument.length()
            checkpoint();progress("Menyimpan hasil",93)
            listOf("vocal" to vocal,"instrumental" to instrument).forEach {(mode,file)->
                val temp=File(audio(owner,track.id,mode,generation).path+".part");partials+=temp
                StemCipher.encrypt(file,temp,key(),"$owner:${track.id}:$mode:$generation",::checkpoint)
                file.delete()
            }
            checkpoint()
            listOf("vocal","instrumental").forEachIndexed {index,mode->java.nio.file.Files.move(partials[index].toPath(),audio(owner,track.id,mode,generation).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)}
            val entry=StemEntry(track,frames*1000/StemDsp.RATE,outputBytes,generation)
            val manifest=File(meta(owner,track.id).path+".part");partials+=manifest;manifest.writeText(AppJson.encodeToString(entry));checkpoint()
            java.nio.file.Files.move(manifest.toPath(),meta(owner,track.id).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            committed=true
            ownerFolder(owner).listFiles()?.filter {it.name.startsWith(hash(track.id)+".") && it.name.endsWith(".enc") && !it.name.contains(generation)}?.forEach {it.delete()}
            val selected=selection.value
            if(selected.owner==owner && selected.track==track.id && selected.mode!="original")selection.value=selected.copy(revision=selected.revision+1)
            notice.value=notice.value?.takeUnless {it.first==track.id}
            refresh(owner);progress("Siap diputar",100)
        } finally {if(!committed)listOf("vocal","instrumental").forEach {audio(owner,track.id,it,generation).delete()};partials.forEach {it.delete()};work.listFiles()?.forEach {it.delete()};work.delete()}
    }}
    suspend fun <T> withSource(owner:String,track:Track,block:suspend (File)->T):T=lock.withLock {withContext(Dispatchers.IO) {
        val job=currentCoroutineContext()
        fun checkpoint(){job.ensureActive();check(owner==library.scope.value){"Akun berubah."}}
        checkpoint();check(temporary.usableSpace>350L*1024*1024){"Sediakan sedikitnya 350 MB ruang kosong."}
        val work=File(temporary,java.util.UUID.randomUUID().toString()).apply {mkdirs()}
        try {val input=File(work,"input.audio");copyInput(track,input,::checkpoint);block(input)}finally {work.listFiles()?.forEach {it.delete()};work.delete()}
    }}
    private suspend fun copyInput(track:Track,target:File,checkpoint:()->Unit) {
        if(track.local){require(track.device==DeviceIdentity.id(context)){"Pilih file lokal di perangkat ini dahulu."};context.contentResolver.openInputStream(Uri.parse(track.url))?.use {input->target.outputStream().use {out->val bytes=ByteArray(32768);var total=0L;while(true){checkpoint();val n=input.read(bytes);if(n<0)break;total+=n;require(total<=64L*1024*1024){"Ukuran audio maksimal 64 MB."};out.write(bytes,0,n)}}} ?: error("Berkas lokal tidak ditemukan.");return}
        if(offline.available(track.id)){target.writeBytes(offline.read(track.id));return}
        val stream=streams.resolve(track,"high")
        val source=ChunkedAudioDataSource(OkHttpDataSource.Factory(OkHttpClient.Builder().readTimeout(25,java.util.concurrent.TimeUnit.SECONDS).build()))
        try {source.open(DataSpec(Uri.parse(stream.url)));target.outputStream().use {out->val buffer=ByteArray(32768);var total=0L;while(true){checkpoint();val n=source.read(buffer,0,buffer.size);if(n<0)break;total+=n;require(total<=64L*1024*1024){"Ukuran audio maksimal 64 MB."};out.write(buffer,0,n)}}} catch(e:kotlinx.coroutines.CancellationException){throw e} catch(e:Exception){streams.invalidate(track,"high");throw e}finally {source.close()}
    }
    private fun prepareModels(checkpoint:()->Unit):List<File> = listOf("vocals","accompaniment").map {name ->
        val target=File(models,"$name.onnx")
        val expected=MODEL_HASHES.getValue(name)
        if(!target.isFile || target.inputStream().use {digest(it)}!=expected){
            val temporary=File(models,"$name.part")
            try {context.assets.open("stem-models/$name.onnx").use {input->temporary.outputStream().use {out->val b=ByteArray(65536);while(true){checkpoint();val n=input.read(b);if(n<0)break;out.write(b,0,n)}}};check(temporary.inputStream().use {digest(it)}==expected){"Model AI rusak; pasang ulang APK."};java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)}finally {temporary.delete()}
        };target
    }
    companion object {
        val MODEL_HASHES=mapOf("vocals" to "489a6ef810271fa8a491c06f5e17fcc6f23219370d92912bf6c6aad272b7fc48","accompaniment" to "43b89efbefb3b1db74ee70d073a8193c2b43da432f9085f7a6bc00e280043580")
        fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
        private fun digest(input:java.io.InputStream):String {val h=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;h.update(b,0,n)};return h.digest().joinToString(""){"%02x".format(it)}}
    }
}
