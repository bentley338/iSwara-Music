package id.iswara.music

import android.app.Application
import android.appwidget.AppWidgetManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import id.iswara.music.model.Track
import id.iswara.music.playback.*
import id.iswara.music.ui.ArtworkTools
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharingWidgetTest {
    @Test fun lyricCardProducesReadablePngInItsPrivateShareDirectory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val track = Track("test-card", "Lagu pengujian", "Artis pengujian", url = "https://www.youtube.com/watch?v=test")
        val file = ArtworkTools(context).lyricCardFile(track, listOf("Baris khusus pengujian satu", "Baris khusus pengujian dua"))
        assertEquals(java.io.File(context.cacheDir, "lyric-cards").canonicalPath, file.parentFile!!.canonicalPath)
        val bitmap = file.inputStream().use { BitmapFactory.decodeStream(it) }
        assertEquals(1080, bitmap.width); assertEquals(1350, bitmap.height)
    }
    @Test fun cardStylesFitLongLyricsAndBothFormatsWithoutCropping()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val art=ArtworkTools(context)
        val track=Track("card-long","Judul yang panjang untuk memeriksa susunan sampul dan tipografi", "Artis pengujian",url="content://iswara-test/card")
        val lines=listOf("Satu baris lirik yang cukup panjang untuk menguji pembungkusan teks secara otomatis.","Baris kedua tetap terlihat rapi ketika dibagikan ke status.","Baris ketiga menemani lagu.","Baris keempat menutup pilihan.")
        for(style in listOf("charcoal","paper","cover"))for(story in listOf(false,true)) {
            val file=art.lyricCardFile(track,lines,style,story)
            val image=BitmapFactory.decodeFile(file.path)
            assertEquals(1080,image.width);assertEquals(if(story)1920 else 1350,image.height)
            val output=java.io.File("../docs/verification/card-$style-${if(story)"story" else "post"}.png")
            output.parentFile.mkdirs();file.copyTo(output,true);image.recycle()
        }
        assertTrue(art.lyricCardFile(track,listOf("Satu")).isFile)
        try {art.lyricCardFile(track,listOf(" ","Dua"));fail("Blank lines must be rejected")}catch(_:IllegalArgumentException){}
    }
    @Test fun manySelectedLinesArePaginatedWithoutLosingAnyText() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val art=ArtworkTools(context);val lines=List(100){"Baris lirik pengujian nomor $it, panjangnya cukup untuk dibagi otomatis."}
        for(story in listOf(false,true)){val pages=art.lyricPages(lines,story);assertTrue(pages.size>1);assertEquals(lines,pages.flatten())}
    }
    @Test fun vinylLayoutAnimatesOnlyWhilePlayingAndHonorsReducedMotion() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        android.provider.Settings.Global.putFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        MusicWidgets.motion(context,true)
        assertEquals(R.layout.music_widget_vinyl_play,MusicWidgets.views(context,2,playing=true).layoutId)
        assertEquals(R.layout.music_widget_vinyl,MusicWidgets.views(context,2,playing=false).layoutId)
        MusicWidgets.motion(context,false)
        assertEquals(R.layout.music_widget_vinyl,MusicWidgets.views(context,2,playing=true).layoutId)
        MusicWidgets.motion(context,true)
        android.provider.Settings.Global.putFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        assertEquals(R.layout.music_widget_vinyl,MusicWidgets.views(context,2,playing=true).layoutId)
    }
    @Test fun allWidgetStylesRenderTrackAndWorkingControlViews() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = AppWidgetManager.getInstance(context)
        val shadow = Shadows.shadowOf(manager)
        listOf(MusicWidget::class.java, RetroMusicWidget::class.java, VinylMusicWidget::class.java).forEach { type ->
            val id = shadow.createWidget(type, R.layout.music_widget)
            MusicWidgets.render(context, Track("widget-test", "Judul widget", "Artis widget", url = "https://example.org"), true)
            val view = shadow.getViewFor(id)
            val style=MusicWidgets.types.indexOf(type)
            val bitmap=android.graphics.Bitmap.createBitmap(360,if(style==1)280 else if(style==2)180 else 144,android.graphics.Bitmap.Config.ARGB_8888)
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(bitmap.width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(bitmap.height,android.view.View.MeasureSpec.EXACTLY));view.layout(0,0,bitmap.width,bitmap.height);view.draw(android.graphics.Canvas(bitmap))
            val output=java.io.File("../docs/verification/widget-$style.png");output.parentFile.mkdirs();output.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            assertEquals("Judul widget", view.findViewById<TextView>(R.id.widget_title).text.toString())
            assertNotNull(view.findViewById<android.view.View>(R.id.widget_toggle))
            assertNotNull(view.findViewById<android.view.View>(R.id.widget_next))
        }
    }
}
