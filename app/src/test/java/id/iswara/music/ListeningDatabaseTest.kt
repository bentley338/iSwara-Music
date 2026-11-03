package id.iswara.music

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import id.iswara.music.data.*
import id.iswara.music.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ListeningDatabaseTest {
    @Test fun versionOneLibrarySurvivesDatabaseUpgrade() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE documents (scope TEXT NOT NULL, id TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, revision INTEGER NOT NULL, dirty INTEGER NOT NULL, deleted INTEGER NOT NULL, operation TEXT NOT NULL, modified INTEGER NOT NULL, PRIMARY KEY(scope,id))")
            old.execSQL("INSERT INTO documents VALUES ('guest','saved','playlist','{}',7,1,0,'operation',42)")
            old.version = 1
        }
        val database = Room.databaseBuilder(context, IswaraDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        try {
            assertEquals(7L, database.documents().get("guest", "saved")!!.revision)
            database.listening().increment(ListeningRecord("guest", "2026-11-02", "a", "{}", 20))
            assertEquals(20L, database.listening().get("guest", "2026-11-02", "a")!!.seconds)
            assertNull(database.documents().get("other-owner", "saved"))
        } finally { database.close(); context.deleteDatabase(name) }
    }
    @Test fun legacyRecapMigrationIsIdempotentConcurrentAndOwnerIsolated() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, IswaraDatabase::class.java).build()
        val preferences = Preferences(context)
        val library = LibraryRepository(database.documents())
        val owner = "recap-owner-a"; val other = "recap-owner-b"
        val track = Track("test", "Test fixture", url = "https://www.youtube.com/watch?v=test")
        val date = LocalDate.now().toString()
        val legacy = AppJson.encodeToString(listOf(ListeningDay(date, track, 40)))
        preferences.set("stats:$owner", legacy)
        val statistics = ListeningStats(preferences, library, database)
        try {
            statistics.record(owner, track, 10)
            coroutineScope { repeat(30) { launch(Dispatchers.Default) { statistics.record(owner, track, 1) } } }
            assertEquals(80L, database.listening().get(owner, date, track.id)!!.seconds)
            // Simulate a process dying after database commit but before clearing old preferences.
            preferences.set("stats:$owner", legacy)
            val restarted = ListeningStats(preferences, library, database)
            library.scope.value = owner
            assertEquals(80L, restarted.entries.first().single().seconds)
            assertEquals("[]", preferences.raw("stats:$owner").first())
            library.scope.value = other
            assertTrue(restarted.entries.first().isEmpty())
            restarted.record(other, track, 5)
            restarted.clearOwner(owner, deleting = true)
            restarted.record(owner, track, 10)
            assertNull(database.listening().get(owner, date, track.id))
            assertEquals(5L, database.listening().get(other, date, track.id)!!.seconds)
        } finally { database.close() }
    }
}
