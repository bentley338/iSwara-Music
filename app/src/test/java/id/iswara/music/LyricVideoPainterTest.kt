package id.iswara.music

import android.app.Application
import id.iswara.music.model.*
import id.iswara.music.playback.LyricVideoPainter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricVideoPainterTest {
    @Test fun lyricRegionChangesAtRealLineTimestampWithoutUsingProgressBarPixels(){
        val painter=LyricVideoPainter(RuntimeEnvironment.getApplication(),Track("video-test","Judul video","Artis uji",url="content://fixture"),emptyList(),listOf(LyricLine(1000,"Baris pertama untuk pengujian"),LyricLine(3000,"Baris kedua mengikuti audio")),null,false)
        try {
            val before=IntArray(720*300);painter.draw(1500,500,15000).getPixels(before,0,720,0,470,720,300)
            val after=IntArray(720*300);val image=painter.draw(3500,2500,15000);image.getPixels(after,0,720,0,470,720,300)
            assertTrue(before.indices.count {before[it]!=after[it]}>500)
            val file=java.io.File("../docs/verification/lyric-video-frame.png");file.parentFile!!.mkdirs();file.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        } finally {painter.close()}
    }
}
