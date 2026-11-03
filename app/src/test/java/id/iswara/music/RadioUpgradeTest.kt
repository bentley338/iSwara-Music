@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import id.iswara.music.cloud.*
import id.iswara.music.model.Track
import id.iswara.music.playback.*
import id.iswara.music.source.*
import id.iswara.music.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class RadioUpgradeTest {
    @Test fun fallbackRejectsOtherVersionsArtistsAndUnknownDuration() {
        val original=Track("original","Langit","Penyanyi",duration=200,url="https://music.youtube.com/watch?v=original")
        assertTrue(SourceMatchRules.confidence(original,original.copy(id="backup",title="Penyanyi - Langit (Official Audio)",duration=202))>.95)
        for(other in listOf(original.copy(title="Langit live"),original.copy(artist="Orang lain"),original.copy(duration=0),original.copy(duration=230),original.copy(title="Lagu lain")))assertEquals(0.0,SourceMatchRules.confidence(original,other),0.0)
        assertNotEquals(SourceMatchRules.clean("か"),SourceMatchRules.clean("が"))
        assertEquals(SourceMatchRules.clean("Beyoncé"),SourceMatchRules.clean("Beyonce"))
        assertTrue(SourceMatchRules.validUrl("https://soundcloud.com/artist/track"))
        assertFalse(SourceMatchRules.validUrl("https://youtube.com.evil.example/watch"))
        assertFalse(SourceMatchRules.validUrl("https://name@youtube.com/watch"))
    }
    private fun buffer(samples:ShortArray)=ByteBuffer.allocateDirect(samples.size*2).order(ByteOrder.nativeOrder()).apply {samples.forEach {putShort(it)};flip()}
    private fun processor(channels:Int,sound:AnalogSound)=AnalogAudioProcessor().apply {this.sound=sound;configure(AudioProcessor.AudioFormat(44100,channels,C.ENCODING_PCM_16BIT));flush(AudioProcessor.StreamMetadata.DEFAULT)}
    @Test fun analogOffIsBitExactAndEmptyDrainSafe() {
        val p=processor(2,AnalogSound())
        repeat(3){p.queueInput(AudioProcessor.EMPTY_BUFFER);assertFalse(p.output.hasRemaining())}
        val samples=ShortArray(4096){(it*97-32768).toShort()};p.queueInput(buffer(samples))
        val out=p.output.order(ByteOrder.nativeOrder());assertEquals(samples.size*2,out.remaining())
        samples.forEach {assertEquals(it,out.short)}
    }
    @Test fun analogHeadphoneCrossfeedFlushAndMultichannelRemainBounded() {
        val p=processor(2,AnalogSound(space=1f))
        p.queueInput(buffer(ShortArray(8820){if(it%2==0)(sin(it*.1)*20000).toInt().toShort()else 0}))
        val output=p.output.order(ByteOrder.nativeOrder());var rightEnergy=0L
        while(output.hasRemaining()){output.short;rightEnergy+=abs(output.short.toInt())};assertTrue(rightEnergy>10000)
        p.flush(AudioProcessor.StreamMetadata.DEFAULT);p.queueInput(buffer(ShortArray(4096)))
        val silent=p.output;while(silent.hasRemaining())assertEquals(0,silent.get().toInt())
        for(channels in listOf(1,2,6,8)) {
            val all=processor(channels,AnalogSound(1f,1f,1f));val samples=ShortArray(channels*4410){if(it%2==0)Short.MAX_VALUE else Short.MIN_VALUE}
            all.queueInput(buffer(samples));val result=all.output.order(ByteOrder.nativeOrder());assertEquals(samples.size*2,result.remaining())
            while(result.hasRemaining())assertTrue(abs(result.short.toInt())<=32767)
        }
    }
    @Test fun localReadingsKeepOriginalTextAndWordOffsets() {
        val romanizer=LyricRomanizer()
        for((text,language) in listOf("私は音楽が好きです" to "ja","사랑해 음악" to "ko","我爱音乐" to "zh","Enjoy the music" to "en")) {
            val row=romanizer.line(text,language);assertEquals(text,row.text);assertTrue(row.words.isNotEmpty())
            row.words.forEach {assertEquals(it.text,text.substring(it.start,it.end))}
            if(language!="en"){assertTrue(row.reading.isNotBlank());assertNotEquals(text,row.reading)}
        }
        assertTrue(romanizer.line("寿司","ja").reading.contains("sushi"))
        assertNotEquals("사랑해",romanizer.smartLine("사랑해","ja","auto").reading)
    }
    @Test fun allThemeAccentTextPairsHaveReadableContrast() {
        fun contrast(a:Color,b:Color)=(max(a.luminance(),b.luminance())+.05)/(min(a.luminance(),b.luminance())+.05)
        for(theme in listOf("dark","light","midnight","forest","dawn"))for(accent in listOf("orange","ocean","mint","rose","lavender","gold")) {
            val c=iswaraColors(theme,accent,isDarkTheme(theme,false))
            val pairs=listOf(c.onPrimary to c.primary,c.onPrimaryContainer to c.primaryContainer,c.onSecondary to c.secondary,c.onSecondaryContainer to c.secondaryContainer,c.onSurface to c.surface,c.onSurfaceVariant to c.surfaceContainerHighest,c.primary to c.surfaceContainerHighest,c.onBackground to c.background)
            pairs.forEachIndexed {index,(a,b)->assertTrue("$theme $accent pair $index: ${contrast(a,b)}",contrast(a,b)>=4.5)}
        }
    }
    @Test fun radioMessagesMergeTombstonesWithoutDuplicatesAndBoundHistory() {
        val old=(1L..250).map {RadioMessage(it,"sender","Nama","comment","Pesan",false)}
        val tombstone=old.last().copy(removed=true,body="")
        val merged=RadioRules.merge(old,listOf(tombstone))
        assertEquals(200,merged.size);assertTrue(merged.last().removed);assertEquals("",merged.last().body)
        assertEquals(200,merged.map {it.id}.distinct().size)
        assertEquals("Nama",RadioRules.nickname("  Nama\u0000  "))
        assertEquals(280,RadioRules.message("a".repeat(400)).length)
    }
}
