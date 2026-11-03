package id.iswara.music.cloud

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.IswaraApp
import id.iswara.music.data.*
import id.iswara.music.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor(private val auth: AuthRepository, private val dao: DocumentDao, private val library: LibraryRepository) {
    val status = MutableStateFlow("")
    private val mutex = Mutex()
    suspend fun sync() = mutex.withLock {
        val owner = auth.account.value?.id ?: return@withLock
        status.value = "Menyinkronkan…"
        try {
            val token = auth.token()
            for (doc in dao.all(owner).filter { it.dirty }) {
                check(auth.account.value?.id == owner) { "Akun berubah" }
                val result = auth.request("/rest/v1/rpc/sync_document", buildJsonObject {
                    put("p_id", doc.id); put("p_kind", doc.kind); put("p_payload", AppJson.parseToJsonElement(doc.payload))
                    put("p_base_revision", doc.revision); put("p_deleted", doc.deleted); put("p_operation", doc.operation)
                }, token).jsonObject
                check(auth.account.value?.id == owner) { "Akun berubah" }
                val conflict = result["conflict"]!!.jsonPrimitive.boolean
                val remote = result["document"]!!.jsonObject
                if (conflict && doc.kind == "playlist" && !doc.deleted) {
                    val p = AppJson.decodeFromString<Playlist>(doc.payload)
                    val copy = p.copy(id = UUID.randomUUID().toString(), title = "${p.title} (salinan konflik)")
                    dao.put(Document(owner, copy.id, "playlist", AppJson.encodeToString(copy)))
                }
                val latest = dao.get(owner, doc.id)
                if (latest?.operation == doc.operation) {
                    if (conflict && (doc.kind != "playlist" || doc.deleted)) {
                        dao.put(doc.copy(revision = remote["revision"]!!.jsonPrimitive.long, operation = UUID.randomUUID().toString()))
                    } else dao.put(remote.toDocument(owner))
                } else if (!conflict) dao.advanceRevision(owner, doc.id, doc.revision, remote["revision"]!!.jsonPrimitive.long)
            }
            var offset = 0
            do {
                val rows = auth.request("/rest/v1/documents?select=*&owner=eq.$owner&order=id.asc&limit=1000&offset=$offset", token = token, method = "GET").jsonArray
                check(auth.account.value?.id == owner) { "Akun berubah" }
                for (row in rows) {
                    val doc = row.jsonObject.toDocument(owner)
                    if (dao.get(owner, doc.id)?.dirty != true) dao.put(doc)
                }
                offset += rows.size
            } while (rows.size == 1000)
            status.value = if (dao.all(owner).any { it.dirty }) "Perubahan menunggu sinkronisasi berikutnya" else "Koleksi tersinkron"
        } catch (e: Exception) { status.value = "Sinkronisasi tertunda: ${e.message}"; throw e }
    }
    private fun JsonObject.toDocument(owner: String) = Document(owner, getValue("id").jsonPrimitive.content, getValue("kind").jsonPrimitive.content, getValue("payload").toString(), getValue("revision").jsonPrimitive.long, false, getValue("deleted").jsonPrimitive.boolean, getValue("operation").jsonPrimitive.content, get("modified")?.jsonPrimitive?.longOrNull ?: 0)
}

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try { (applicationContext as IswaraApp).sync.sync(); Result.success() } catch (_: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("cloud-sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
        }
        fun now(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("cloud-sync-now", ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
