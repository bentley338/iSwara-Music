package id.iswara.music

import android.app.Application
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.room.Room
import id.iswara.music.data.*
import id.iswara.music.model.*
import id.iswara.music.source.*
import id.iswara.music.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36], application = Application::class)
class ExperienceTest {
    private val track = Track("fixture", "Yellow", "Coldplay", duration = 266, url = "https://www.youtube.com/watch?v=fixture")
    @Test fun paletteUsesPopulousCoverColorAndKeepsControlsReadableInBothThemes() {
        val blue = Color.rgb(35, 95, 160)
        val palette = ArtworkColors.extract(IntArray(1000) { if (it < 850) blue else if (it < 990) Color.BLACK else Color.RED })!!
        assertEquals(blue, palette.dominant)
        assertNull(ArtworkColors.extract(intArrayOf(Color.TRANSPARENT)))
        assertNotNull(ArtworkColors.extract(intArrayOf(Color.WHITE, Color.BLACK)))
        for (surface in listOf(Color.rgb(20,21,24), Color.rgb(255,248,242))) {
            for (hue in 0..350 step 10) {
                val main = ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(),.8f,.45f))
                val result = ArtworkColors.player(ArtworkPalette(main, Color.WHITE),surface,Color.RED)
                assertTrue(ColorUtils.calculateContrast(result.accent,result.background) >= 4.5)
                assertTrue(ColorUtils.calculateContrast(result.accent,result.glow) >= 4.5)
                assertTrue(ColorUtils.calculateContrast(result.onAccent,result.accent) >= 4.5)
            }
        }
    }
    @Test fun lyricsRejectWrongArtistVersionAndDurationAndCleanCatalogTitles() {
        val correct = LyricCandidate("1","Yellow","Coldplay",266,"test")
        assertEquals(1.0, LyricsRules.score(track, correct), .001)
        assertEquals(1.0, LyricsRules.score(track.copy(title="Coldplay - Yellow (Official Video)", artist="Coldplay - Topic"),correct), .001)
        assertEquals(0.0,LyricsRules.score(track,correct.copy(artist="Other artist")), .001)
        assertEquals(0.0,LyricsRules.score(track,correct.copy(title="Yellow Live")), .001)
        assertEquals(0.0,LyricsRules.score(track,correct.copy(duration=100)), .001)
        assertTrue(lyricStyle(track.id,"auto") in LyricsVisuals.styles.map {it.first})
        assertEquals("slide",lyricStyle(track.id,"slide"))
    }
    @Test fun savedQueuesAndManualLyricsSurviveRestartRemainOwnerIsolatedAndRespectDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),IswaraDatabase::class.java).build()
        try {
            val library = LibraryRepository(db.documents())
            val banks = SavedQueues(db,library)
            val snapshot = QueueSnapshot(listOf(track, track.copy(id="second")),1,12345,true,2,listOf(1,0))
            val saved = banks.create("Perjalanan",snapshot)
            val restarted = SavedQueues(db,library)
            assertEquals(snapshot,restarted.get("guest",saved.id)!!.snapshot)
            library.scope.value = "account-b"
            assertTrue(restarted.entries.first().isEmpty())
            assertNull(restarted.get("account-b",saved.id))
            try { restarted.create("Stale",snapshot,"guest"); fail("stale owner must not write") } catch (_: IllegalStateException) { }
            val lyrics = LyricsRepository(db,library)
            val raw = "[00:01.00]Test fixture\n[00:04.50]Second line"
            lyrics.import(track,raw)
            assertEquals(4500L,LyricsRepository(db,library).get(track).lines.last().time)
            library.scope.value = "guest"
            assertNull(db.experience().lyrics("guest",track.id))
            banks.clearOwner("guest")
            banks.update("guest",saved.id,snapshot)
            assertNull(banks.get("guest",saved.id))
            lyrics.clearOwner("account-b")
            assertNull(db.experience().lyrics("account-b",track.id))
        } finally { db.close() }
    }
}
