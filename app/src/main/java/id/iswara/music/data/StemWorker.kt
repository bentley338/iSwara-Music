package id.iswara.music.data

import android.app.*
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import id.iswara.music.IswaraApp
import id.iswara.music.R
import id.iswara.music.model.*
import kotlinx.coroutines.CancellationException

class StemWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        val app=applicationContext as IswaraApp
        val owner=inputData.getString("owner") ?: return Result.failure()
        if(owner!=app.library.scope.value)return Result.failure()
        val track=runCatching {AppJson.decodeFromString<Track>(inputData.getString("track").orEmpty())}.getOrNull() ?: return Result.failure()
        return try {
            setForeground(notification("Menyiapkan pemisahan audio",0))
            var previous=-1
            app.stems.process(owner,track) {stage,percent ->
                setProgress(workDataOf("stage" to stage,"percent" to percent))
                if(percent/5!=previous){previous=percent/5;setForeground(notification(stage,percent))}
            };Result.success()
        } catch(e:CancellationException){throw e} catch(e:OutOfMemoryError){Result.failure(workDataOf("error" to "Memori belum cukup. Tutup aplikasi berat lalu coba lagi."))}
        catch(e:Exception){val message=e.message?.takeIf {it.length<=160 && !it.contains(Regex("(?i)https?://|token|bearer|googlevideo"))} ?: "Pemisahan belum berhasil. Periksa koneksi atau coba berkas lain.";Result.failure(workDataOf("error" to message))}
    }
    private fun notification(stage:String,percent:Int):ForegroundInfo {
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("stem-processing","Pemisahan audio",NotificationManager.IMPORTANCE_LOW))
        val cancel=WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification=NotificationCompat.Builder(applicationContext,"stem-processing").setSmallIcon(R.drawable.ic_logo).setContentTitle("iSwara Studio").setContentText(stage).setProgress(100,percent,false).setOngoing(true).setOnlyAlertOnce(true).addAction(0,"Batal",cancel).build()
        return if(android.os.Build.VERSION.SDK_INT>=29)ForegroundInfo(7301,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)else ForegroundInfo(7301,notification)
    }
}
