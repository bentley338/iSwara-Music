package id.iswara.music.data

import android.content.Context
import android.content.Intent
import id.iswara.music.IswaraApp
import id.iswara.music.R
import androidx.core.content.FileProvider
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.model.*
import id.iswara.music.playback.*
import id.iswara.music.ui.ArtworkTools
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import javax.inject.*

@Serializable data class LyricExport(val track:Track,val lines:List<String>,val timed:List<LyricLine>,val start:Long,val duration:Long,val video:Boolean,val motion:Boolean=true)
data class LyricExportStatus(val track:String,val running:Boolean,val percent:Int,val stage:String,val file:File?,val error:String?,val fingerprint:String)

@Singleton class LyricMediaStore @Inject constructor(@ApplicationContext private val context:Context,private val library:LibraryRepository,private val stems:StemStore,private val artwork:ArtworkTools) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val root=File(context.cacheDir,"lyric-media").apply {mkdirs()}
    val status=WorkManager.getInstance(context).getWorkInfosByTagFlow("lyric-media").combine(library.scope) {infos,owner ->
        infos.filter {"export-owner:${StemStore.hash(owner)}" in it.tags}.map {info ->
            val result=info.outputData.getString("file")?.let(::File)?.takeIf {it.isFile && it.canonicalFile.parentFile==root.canonicalFile}
            LyricExportStatus(info.tags.firstOrNull {it.startsWith("track:")}?.removePrefix("track:").orEmpty(),info.state==WorkInfo.State.RUNNING || info.state==WorkInfo.State.ENQUEUED,info.progress.getInt("percent",0),info.progress.getString("stage") ?: "Menunggu",result,if(info.state==WorkInfo.State.FAILED)info.outputData.getString("error") ?: "Ekspor belum berhasil." else null,info.tags.firstOrNull {it.startsWith("spec:")}?.removePrefix("spec:").orEmpty())
        }
    }.stateIn(scope,SharingStarted.Eagerly,emptyList())
    init {scope.launch {root.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}?.forEach {it.delete()}};scope.launch {var old=library.scope.value;library.scope.collect {owner->if(old!=owner){WorkManager.getInstance(context).cancelAllWorkByTag("export-owner:${StemStore.hash(old)}");old=owner}}}}
    suspend fun enqueue(request:LyricExport) = withContext(Dispatchers.IO) {
        require(request.duration in 1000..60_000 && request.start>=0){"Pilih potongan 1–60 detik."}
        val owner=library.scope.value;val token=java.util.UUID.randomUUID().toString()
        val spec=File(root,"${StemStore.hash(owner)}-$token.json");spec.writeText(AppJson.encodeToString(request))
        val work=OneTimeWorkRequestBuilder<LyricMediaWorker>().setInputData(workDataOf("owner" to owner,"request" to token)).addTag("lyric-media").addTag("spec:${fingerprint(request)}").addTag("track:${request.track.id}").addTag("export-owner:${StemStore.hash(owner)}").build()
        WorkManager.getInstance(context).enqueueUniqueWork("lyric-export:${StemStore.hash(owner)}",ExistingWorkPolicy.REPLACE,work)
    }
    fun fingerprint(request:LyricExport)=StemStore.hash(AppJson.encodeToString(request))
    suspend fun clearOwner(owner:String)=withContext(Dispatchers.IO){WorkManager.getInstance(context).cancelAllWorkByTag("export-owner:${StemStore.hash(owner)}");root.listFiles()?.filter {it.name.contains(StemStore.hash(owner))}?.forEach {it.delete()}}
    fun cancel(){WorkManager.getInstance(context).cancelUniqueWork("lyric-export:${StemStore.hash(library.scope.value)}")}
    suspend fun render(owner:String,token:String,progress:suspend (String,Int)->Unit):File=withContext(Dispatchers.IO) {
        require(token.matches(Regex("[0-9a-f-]{36}")))
        val requestFile=File(root,"${StemStore.hash(owner)}-$token.json")
        val request=AppJson.decodeFromString<LyricExport>(requestFile.readText())
        val job=currentCoroutineContext()
        fun checkpoint(){job.ensureActive();check(owner==library.scope.value){"Akun berubah."}}
        val output=File(root,"iswara-${StemStore.hash(owner)}-$token.${if(request.video)"mp4" else "m4a"}")
        try {
            checkpoint();progress("Menyiapkan audio",1)
            stems.withSource(owner,request.track) {input ->
                val pcm=File(input.parentFile,"export.pcm")
                try {
                    progress("Membaca audio",8);StemDecoder.decode(context,input,pcm,::checkpoint);input.delete();checkpoint()
                    val cover=if(request.video)artwork.bitmap(request.track.artwork)else null
                    val painter=if(request.video)LyricVideoPainter(context,request.track,request.lines,request.timed,cover,request.motion)else null
                    try {
                        var previous=-1
                        LyricMediaEncoder.encode(pcm,output,request.start,request.duration,painter,::checkpoint) {percent ->
                            if(percent!=previous){previous=percent;runBlocking {progress(if(request.video)"Membuat video lirik" else "Membuat potongan audio",10+percent*89/100)}}
                        }
                    } finally {painter?.close()}
                } finally {pcm.delete()}
            }
            checkpoint();check(output.length()>100){"Ekspor kosong."};progress("Siap dibagikan",100);output
        } catch(e:Throwable){output.delete();throw e} finally {requestFile.delete()}
    }
    fun validate(file:File) {check(file.isFile && file.canonicalFile.parentFile==root.canonicalFile && file.name.startsWith("iswara-${StemStore.hash(library.scope.value)}-")){"Hasil ekspor tidak tersedia pada akun ini."}}
    fun share(file:File):Intent {
        validate(file)
        val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
        return Intent(Intent.ACTION_SEND).setType(if(file.extension=="mp4")"video/mp4" else "audio/mp4").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {clipData=android.content.ClipData.newRawUri("Lirik iSwara",uri)}
    }
}

class LyricMediaWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        val owner=inputData.getString("owner") ?: return Result.failure()
        val token=inputData.getString("request") ?: return Result.failure()
        val app=applicationContext as IswaraApp
        return try {
            var last=-1
            setForeground(notification("Menyiapkan ekspor",0))
            val file=app.lyricMedia.render(owner,token) {stage,percent ->setProgress(workDataOf("stage" to stage,"percent" to percent));if(percent/5!=last){last=percent/5;setForeground(notification(stage,percent))}}
            Result.success(workDataOf("file" to file.path))
        }catch(e:CancellationException){throw e}catch(e:OutOfMemoryError){Result.failure(workDataOf("error" to "Memori belum cukup untuk ekspor."))}
        catch(e:Exception){Result.failure(workDataOf("error" to (e.message?.takeIf {it.length<160 && !it.contains(Regex("(?i)https?://|bearer|token"))} ?: "Ekspor belum berhasil. Coba bagian lebih singkat.")))}
    }
    private fun notification(stage:String,percent:Int):ForegroundInfo {
        val manager=applicationContext.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel("lyric-export","Ekspor lirik",android.app.NotificationManager.IMPORTANCE_LOW))
        val notification=androidx.core.app.NotificationCompat.Builder(applicationContext,"lyric-export").setSmallIcon(R.drawable.ic_logo).setContentTitle("iSwara · Ekspor lirik").setContentText(stage).setProgress(100,percent,false).setOngoing(true).setOnlyAlertOnce(true).addAction(0,"Batal",WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return if(android.os.Build.VERSION.SDK_INT>=29)ForegroundInfo(7302,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)else ForegroundInfo(7302,notification)
    }
}
