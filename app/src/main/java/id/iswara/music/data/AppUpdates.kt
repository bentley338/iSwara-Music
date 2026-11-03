package id.iswara.music.data

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.*
import id.iswara.music.R
import id.iswara.music.cloud.AuthRepository
import id.iswara.music.model.AppJson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable data class AppRelease(val versionCode:Int,val versionName:String,val url:String,val sha256:String,val bytes:Long,val notes:String="",val channel:String="stable",val minSdk:Int=26)
data class UpdateState(val release:AppRelease?=null,val checking:Boolean=false,val downloading:Boolean=false,val percent:Int=0,val ready:Boolean=false,val error:String?=null,val checked:Long=0)
object UpdateRules {
    const val RELEASE_CERT="6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe"
    fun github(url:String)=runCatching {val u=url.toHttpUrl();u.scheme=="https" && u.host=="github.com" && u.port==443 && u.username.isEmpty() && u.password.isEmpty() && u.query==null && u.fragment==null && u.encodedPath.matches(Regex("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/[^?#]+\\.apk"))}.getOrDefault(false)
    fun redirect(url:String)=runCatching {val u=url.toHttpUrl();u.scheme=="https" && u.port==443 && u.username.isEmpty() && u.password.isEmpty() && u.host in setOf("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com")}.getOrDefault(false)
    fun valid(r:AppRelease)=r.versionCode>0 && r.versionName.length in 1..32 && r.url.length<=2048 && github(r.url) && r.sha256.matches(Regex("[a-f0-9]{64}")) && r.bytes in 1..524288000L && r.notes.length<=12000 && r.channel in setOf("stable","beta") && r.minSdk in 26..1000
    fun digest(file:File):String {val hash=MessageDigest.getInstance("SHA-256");file.inputStream().use {input->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;hash.update(buffer,0,n)}};return hash.digest().joinToString(""){"%02x".format(it)}}
    fun decode(data:JsonObject):AppRelease?=runCatching {
        fun text(k:String):String = data[k]?.jsonPrimitive?.content.orEmpty()
        AppRelease(text("version_code").toInt(),text("version_name"),text("download_url"),text("sha256"),text("bytes").toLong(),text("notes"),text("channel"),text("min_sdk").toInt()).takeIf(::valid)
    }.getOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton class AppUpdates @Inject constructor(@ApplicationContext private val context:Context,private val auth:AuthRepository,private val preferences:Preferences) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val work=WorkManager.getInstance(context)
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(10,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(20,TimeUnit.MINUTES).build()
    private val directory=File(context.filesDir,"updates").apply {mkdirs()}
    val state=MutableStateFlow(UpdateState())
    private val checkLock=kotlinx.coroutines.sync.Mutex()
    private val local=context.getSharedPreferences("app-updates",Context.MODE_PRIVATE)
    private val activeWork=MutableStateFlow(runCatching {java.util.UUID.fromString(local.getString("work",null))}.getOrNull())
    private var downloadJob:Job?=null
    init {
        scope.launch {directory.listFiles()?.filter {it.extension=="part" && it.lastModified()<System.currentTimeMillis()-86400_000}?.forEach {it.delete()}}
        scope.launch {runCatching {AppJson.decodeFromString<AppRelease>(File(directory,"ready.json").readText())}.getOrNull()?.takeIf {UpdateRules.valid(it) && it.versionCode>BuildConfig.VERSION_CODE && apk(it).exists()}?.let {ready->state.update {if(it.release==null)it.copy(release=ready,ready=true)else it}}}
        scope.launch {activeWork.filterNotNull().flatMapLatest {work.getWorkInfoByIdFlow(it)}.filterNotNull().collect {info->
            val release=state.value.release
            val downloading=info.state in setOf(WorkInfo.State.RUNNING,WorkInfo.State.ENQUEUED,WorkInfo.State.BLOCKED)
            state.update {it.copy(downloading=downloading,percent=info.progress.getInt("percent",0),ready=release!=null && apk(release).exists(),error=if(info.state==WorkInfo.State.FAILED)info.outputData.getString("error") ?: "Unduhan belum berhasil. Coba lagi." else it.error)}
        }}
    }
    fun schedule(enabled:Boolean){if(enabled)work.enqueueUniquePeriodicWork("iswara-update-check",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<UpdateCheckWorker>(24,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())else work.cancelUniqueWork("iswara-update-check")}
    suspend fun check(notify:Boolean=false) {
        if(!checkLock.tryLock())return
        state.update {it.copy(checking=true,error=null)}
        try {
            val settings=preferences.settings.first()
            val raw=auth.request("/rest/v1/rpc/check_app_update",buildJsonObject {put("p_current",BuildConfig.VERSION_CODE);put("p_sdk",Build.VERSION.SDK_INT);put("p_channel",settings.updateChannel)}).jsonObject
            val release=UpdateRules.decode(raw)?.takeIf {it.versionCode>BuildConfig.VERSION_CODE && it.minSdk<=Build.VERSION.SDK_INT && it.channel==settings.updateChannel}
            state.update {it.copy(release=release,checking=false,checked=System.currentTimeMillis(),ready=release!=null && apk(release).exists())}
            if(notify && release!=null && preferences.raw("updateSeen").first()!=release.versionCode.toString() && notice(release,false))preferences.set("updateSeen",release.versionCode.toString())
        }catch(e:CancellationException){throw e}catch(_:Exception){state.update {it.copy(checking=false,error="Info pembaruan belum tersambung. Coba lagi nanti.")}}
        finally {state.update {it.copy(checking=false)};checkLock.unlock()}
    }
    fun download(release:AppRelease,allowMetered:Boolean=false) {
        require(UpdateRules.valid(release) && release.versionCode>BuildConfig.VERSION_CODE)
        if(state.value.downloading)return
        state.update {it.copy(release=release,error=null,ready=false,downloading=true,percent=0)}
        downloadJob=scope.launch {try {
            val wifi=preferences.settings.first().updateWifi && !allowMetered
            val request=OneTimeWorkRequestBuilder<UpdateDownloadWorker>().setInputData(workDataOf("release" to AppJson.encodeToString(release.copy(notes="")))).setConstraints(Constraints.Builder().setRequiredNetworkType(if(wifi)NetworkType.UNMETERED else NetworkType.CONNECTED).build()).build()
            local.edit().putString("work",request.id.toString()).apply();activeWork.value=request.id
            currentCoroutineContext().ensureActive();val scheduling=currentCoroutineContext().job
            val operation=work.enqueueUniqueWork("iswara-update-download",ExistingWorkPolicy.REPLACE,request)
            operation.result.addListener({try{operation.result.get();if(scheduling.isCancelled)work.cancelWorkById(request.id)}catch(_:Exception){state.update {it.copy(downloading=false,error="Unduhan belum dapat dijadwalkan. Coba lagi.")}}},java.util.concurrent.Executor {it.run()})
        }catch(e:CancellationException){state.update {it.copy(downloading=false)};throw e}catch(_:Exception){state.update {it.copy(downloading=false,error="Unduhan belum dapat dimulai. Coba lagi.")}}}
    }
    fun cancel(){downloadJob?.cancel();work.cancelUniqueWork("iswara-update-download");state.update {it.copy(downloading=false)}}
    private fun apk(r:AppRelease)=File(directory,"iswara-${r.versionCode}.apk")
    @Suppress("DEPRECATION") private fun certificate(info:android.content.pm.PackageInfo):String {
        val signatures=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
        check(signatures?.size==1){"Tanda tangan APK tidak cocok."}
        return MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()).joinToString(""){"%02x".format(it)}
    }
    @Suppress("DEPRECATION") private fun verify(file:File,r:AppRelease) {
        check(file.length()==r.bytes && UpdateRules.digest(file)==r.sha256){"Berkas pembaruan tidak utuh. Unduh ulang."}
        val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info=context.packageManager.getPackageArchiveInfo(file.absolutePath,flags) ?: error("APK tidak dapat diperiksa.")
        val version=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
        check(info.packageName==context.packageName && info.versionName==r.versionName && version==r.versionCode.toLong() && version>BuildConfig.VERSION_CODE && info.applicationInfo?.minSdkVersion?.let {it<=Build.VERSION.SDK_INT}==true){"APK bukan pembaruan yang sesuai perangkat ini."}
        check(certificate(info)==UpdateRules.RELEASE_CERT && certificate(context.packageManager.getPackageInfo(context.packageName,flags))==UpdateRules.RELEASE_CERT){"Tanda tangan berbeda. Pembaruan hanya untuk aplikasi release iSwara."}
    }
    suspend fun fetch(r:AppRelease,progress:suspend (Int)->Unit)=withContext(Dispatchers.IO) {
        require(UpdateRules.valid(r));check(directory.usableSpace>r.bytes+20_000_000){"Ruang penyimpanan belum cukup."}
        val partial=File.createTempFile("update-",".part",directory);var url=r.url;var response:Response?=null
        try {
            repeat(6) {step->if(response==null){currentCoroutineContext().ensureActive();check(UpdateRules.redirect(url));val res=client.newCall(Request.Builder().url(url).build()).execute()
                if(res.code in 300..399){val next=res.header("Location")?.let {res.request.url.resolve(it)?.toString()};res.close();check(next!=null && UpdateRules.redirect(next) && step<5){"Tautan unduhan tidak sesuai."};url=next}else response=res}}
            val res=response ?: error("Tautan unduhan belum tersedia.")
            res.use {check(it.isSuccessful){"Unduhan belum tersedia (${it.code})."};val body=it.body ?: error("Unduhan kosong.");var bytes=0L;var last=-1
                body.byteStream().use {input->partial.outputStream().use {output->val buffer=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;bytes+=n;check(bytes<=r.bytes){"Ukuran unduhan tidak sesuai."};output.write(buffer,0,n);val percent=(bytes*100/r.bytes).toInt();if(percent!=last){last=percent;progress(percent)}}}}
            };currentCoroutineContext().ensureActive();verify(partial,r);currentCoroutineContext().ensureActive();check(partial.renameTo(apk(r))){"Pembaruan tidak dapat disimpan."}
            directory.listFiles()?.filter {it.extension=="apk" && it.name!=apk(r).name}?.forEach {it.delete()}
            val display=state.value.release?.takeIf {it.versionCode==r.versionCode && it.sha256==r.sha256} ?: r
            File(directory,"ready.json").writeText(AppJson.encodeToString(display));state.update {it.copy(release=display,ready=true,downloading=false,percent=100,error=null)};notice(display,true)
        }finally {response?.close();partial.delete()}
    }
    suspend fun installIntent(r:AppRelease):Intent=withContext(Dispatchers.IO){val file=apk(r);verify(file,r);Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context,"${context.packageName}.files",file),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
    fun notice(r:AppRelease,ready:Boolean):Boolean {
        val manager=context.getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("app-updates","Pembaruan iSwara",NotificationManager.IMPORTANCE_DEFAULT))
        if(!manager.areNotificationsEnabled() || manager.getNotificationChannel("app-updates")?.importance==NotificationManager.IMPORTANCE_NONE)return false
        val intent=PendingIntent.getActivity(context,9302,Intent(context,MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("iswara://updates")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,"app-updates").setSmallIcon(R.drawable.ic_logo).setContentTitle("iSwara ${r.versionName} ${if(ready)"siap dipasang" else "tersedia"}").setContentText(if(ready)"Ketuk untuk melanjutkan pemasangan."else "Buka untuk melihat yang baru.").setContentIntent(intent).setAutoCancel(true).build()
        return try {manager.notify(9302,notification);true}catch(_:SecurityException){false}
    }
}
class UpdateCheckWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {val app=applicationContext as IswaraApp;if(!app.preferences.settings.first().updateAuto)return Result.success();app.updates.check(true);return Result.success()}
}
class UpdateDownloadWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        val r=runCatching {AppJson.decodeFromString<AppRelease>(inputData.getString("release").orEmpty())}.getOrNull() ?: return Result.failure()
        if(r.versionCode<=BuildConfig.VERSION_CODE)return Result.success()
        return try {setForeground(foreground(0));(applicationContext as IswaraApp).updates.fetch(r){percent->setProgress(workDataOf("percent" to percent));if(percent%5==0)setForeground(foreground(percent))};Result.success()}
        catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(workDataOf("error" to (e.message?.takeIf {it.length<=180 && !it.contains("https:")} ?: "Unduhan pembaruan gagal. Coba lagi.")))}
    }
    private fun foreground(percent:Int):ForegroundInfo {
        val manager=applicationContext.getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("update-download","Unduhan pembaruan",NotificationManager.IMPORTANCE_LOW))
        val notification=NotificationCompat.Builder(applicationContext,"update-download").setSmallIcon(R.drawable.ic_logo).setContentTitle("Mengunduh iSwara").setProgress(100,percent,false).setOnlyAlertOnce(true).setOngoing(true).addAction(0,"Batal",WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return if(Build.VERSION.SDK_INT>=29)ForegroundInfo(9301,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)else ForegroundInfo(9301,notification)
    }
}
