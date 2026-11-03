package id.iswara.music.data

import android.content.Context
import androidx.work.*
import id.iswara.music.IswaraApp
import id.iswara.music.model.AppJson
import id.iswara.music.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString

class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as IswaraApp
        if (app.library.scope.value != inputData.getString("owner")) return Result.failure()
        return try {
            val track = AppJson.decodeFromString<Track>(inputData.getString("track") ?: return Result.failure())
            app.offline.download(track, inputData.getString("quality") ?: "medium", inputData.getString("owner") ?: return Result.failure())
            Result.success()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.failure() }
    }
    companion object {
        fun enqueue(context: Context, owner: String, track: Track, quality: String) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("owner" to owner, "track" to AppJson.encodeToString(track), "quality" to quality))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("audio-downloads").build()
            WorkManager.getInstance(context).enqueueUniqueWork("download:$owner:${track.id}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
