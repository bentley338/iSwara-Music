package id.iswara.music

import android.app.Application
import id.iswara.music.data.Preferences
import id.iswara.music.model.*
import id.iswara.music.ui.ListeningTools
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[26,36],application=Application::class)
class ListeningToolsTest {
    @Test fun lyricTimingHandlesIntroDuplicateTimestampsOffsetsAndSeekBounds() {
        val lines = listOf(LyricLine(0,"A"),LyricLine(1000,"B"),LyricLine(1000,"C"),LyricLine(2500,"D"))
        assertEquals(-1,ListeningTools.activeLine(lines,0,-500))
        assertEquals(0,ListeningTools.activeLine(lines,499,500))
        assertEquals(2,ListeningTools.activeLine(lines,500,500))
        assertEquals(2,ListeningTools.activeLine(lines,2999,-500))
        assertEquals(3,ListeningTools.activeLine(lines,3000,-500))
        assertEquals(0L,ListeningTools.lyricSeek(1000,1500,3000))
        assertEquals(3000L,ListeningTools.lyricSeek(5000,-500,3000))
        assertEquals(3,ListeningTools.activeLine(lines,Long.MAX_VALUE,15_000))
    }
    @Test fun queueFilteringKeepsShuffleOrderAndExplicitDuplicates() {
        val one = Track("a","Alpha","Group",url="https://example.com/a")
        val two = one.copy(id="b",title="Omega")
        val tracks = listOf(one,two,one)
        assertEquals(listOf(2,0),ListeningTools.queueMatches(tracks,listOf(2,1,0),"ALPHA"))
        assertEquals(listOf(1),ListeningTools.queueMatches(tracks,listOf(2,1,0),"group omega"))
        assertTrue(ListeningTools.queueMatches(tracks,listOf(99),"").isEmpty())
    }
    @Test fun concurrentTimingAdjustmentsAreAtomicOwnerIsolatedPersistentAndRemovable() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = Preferences(context)
        prefs.adjustLyricsOffset("guest","timing-fixture",0,true)
        coroutineScope { repeat(8) { launch(Dispatchers.Default) {prefs.adjustLyricsOffset("guest","timing-fixture",500)} } }
        assertEquals("4000",Preferences(context).raw("lyricsOffset:guest:timing-fixture").first())
        prefs.adjustLyricsOffset("other","timing-fixture",-500)
        prefs.clearLyricsOffsets("guest")
        assertNull(prefs.raw("lyricsOffset:guest:timing-fixture").first())
        assertEquals("-500",prefs.raw("lyricsOffset:other:timing-fixture").first())
        prefs.clearLyricsOffsets("other")
        prefs.clearOwnerData("deleted-fixture")
        prefs.saveQueue("deleted-fixture",QueueSnapshot(listOf(Track("a","A",url="https://example.com/a"))))
        prefs.adjustLyricsOffset("deleted-fixture","track",500)
        assertNull(prefs.raw("queue:deleted-fixture").first())
        assertNull(prefs.raw("lyricsOffset:deleted-fixture:track").first())
    }
}
