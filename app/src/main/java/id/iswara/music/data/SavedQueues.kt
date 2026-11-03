package id.iswara.music.data

import id.iswara.music.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

data class SavedQueue(val id: String, val title: String, val snapshot: QueueSnapshot, val modified: Long)
@Singleton class SavedQueues @Inject constructor(private val database: IswaraDatabase, private val library: LibraryRepository) {
    private val lock = Mutex()
    private val deletedOwners = mutableSetOf<String>()
    private val dao get() = database.experience()
    private fun decode(row: SavedQueueRow) = runCatching { SavedQueue(row.id,row.title,AppJson.decodeFromString<QueueSnapshot>(row.payload),row.modified) }.getOrNull()
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries = library.scope.flatMapLatest { owner -> dao.queues(owner).map { rows -> rows.mapNotNull(::decode) } }.flowOn(Dispatchers.IO)
    suspend fun create(title: String, snapshot: QueueSnapshot, owner: String = library.scope.value): SavedQueue = withContext(Dispatchers.IO) { lock.withLock {
        check(owner == library.scope.value && owner !in deletedOwners) { "Akun berubah." }
        require(title.isNotBlank()) { "Beri nama antrean." }
        require(snapshot.tracks.isNotEmpty() && snapshot.tracks.size <= 1000) { "Simpan 1–1.000 lagu per antrean." }
        check(owner !in deletedOwners)
        check(dao.queues(owner).first().size < 20) { "Maksimal 20 antrean tersimpan." }
        val row = SavedQueueRow(owner,java.util.UUID.randomUUID().toString(),title.trim().take(80),AppJson.encodeToString(snapshot),System.currentTimeMillis())
        dao.saveQueue(row); decode(row)!!
    } }
    suspend fun get(owner: String,id: String): SavedQueue? = withContext(Dispatchers.IO) { dao.queue(owner,id)?.let(::decode) }
    suspend fun update(owner: String,id: String,snapshot: QueueSnapshot) = withContext(Dispatchers.IO) { lock.withLock {
        if (owner in deletedOwners) return@withLock
        val old = dao.queue(owner,id) ?: return@withLock
        dao.saveQueue(old.copy(payload = AppJson.encodeToString(snapshot), modified = System.currentTimeMillis()))
    } }
    suspend fun rename(id: String,title: String, owner: String = library.scope.value) = withContext(Dispatchers.IO) { lock.withLock {
        check(owner == library.scope.value && owner !in deletedOwners) { "Akun berubah." }
        require(title.isNotBlank())
        val old = dao.queue(owner,id) ?: return@withLock
        dao.saveQueue(old.copy(title = title.trim().take(80)))
    } }
    suspend fun remove(id: String, owner: String = library.scope.value) = withContext(Dispatchers.IO) { lock.withLock { dao.deleteQueue(owner,id) } }
    suspend fun clearOwner(owner: String) = withContext(Dispatchers.IO) { lock.withLock { deletedOwners += owner; dao.clearQueues(owner) } }
}
