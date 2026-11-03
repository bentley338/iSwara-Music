package id.iswara.music

import android.app.Application
import androidx.room.Room
import id.iswara.music.data.*
import id.iswara.music.model.Track
import id.iswara.music.source.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class LyricsProviderLiveTest {
    @Test fun additionalProviderReturnsMatchingMetadataAndRealTimedLyrics()=runBlocking {
        assumeTrue(System.getenv("ISWARA_LIVE_TEST")=="1")
        val database=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),IswaraDatabase::class.java).build()
        try {
            val library=LibraryRepository(database.documents());val repo=LyricsRepository(database,library)
            val track=Track("lyrics-live-fixture","夜空中最亮的星","逃跑计划",url="https://www.youtube.com/watch?v=fixture")
            val candidates=repo.search("逃跑计划 夜空中最亮的星").filter {it.provider=="netease" && LyricsRules.score(track,it)>=.82}
            assertTrue("Additional provider returned matching catalog metadata",candidates.isNotEmpty())
            val lyrics=repo.choose(track,candidates.first());assertTrue(lyrics.available);assertTrue(lyrics.lines.isNotEmpty());assertTrue(lyrics.lines.any {it.time>0})
            assertEquals("netease",lyrics.source)
        }finally{database.close()}
    }
}
