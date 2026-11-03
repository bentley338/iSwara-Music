package id.iswara.music

import id.iswara.music.source.LyricsTranslation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class SmartLyricsLiveTest {
    @Test fun actualTranslationPreservesBatchMappingAndDetectsLanguage()=runBlocking {
        assumeTrue(System.getenv("ISWARA_LIVE_TEST")=="1")
        val translator=LyricsTranslation(RuntimeEnvironment.getApplication())
        val texts=listOf("Hello world","Good night","Enjoy the music")
        val result=translator.batch(texts,"auto",false){_,_->}
        assertEquals("en",result.language);assertEquals(texts.toSet(),result.lines.keys)
        assertTrue(result.lines.values.all {it.isNotBlank()})
        assertTrue(result.lines.getValue("Good night").lowercase().contains("malam"))
        val japanese=translator.batch(listOf("音楽が好きです"),"ja",false){_,_->}
        assertEquals("ja",japanese.language);assertTrue(japanese.lines.values.first().lowercase().contains("musik"))
        val french=translator.batch(listOf("Good night"),"en",false,"fr"){_,_->}
        assertTrue(french.lines.values.single().lowercase().contains("nuit"))
        val mixed=translator.batch((1..12).map {"Enjoy the music $it"}+"音楽が好きです","auto",false){_,_->}
        assertTrue(mixed.lines.getValue("音楽が好きです").lowercase().contains("musik"))
    }
}
