package id.iswara.music

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import id.iswara.music.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricsMotionUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun activeLyricsAnimateOnEntryWithArtworkPalette() = entry(true,true,"dark")
    @Test fun activeLyricsAnimateWithArtworkColorsDisabled() = entry(false,true,"dark")
    @Test fun activeLyricsAnimateInLightTheme() = entry(true,true,"light")
    @Test fun disabledMotionHasNoIntermediateFrames() = entry(true,false,"dark")
    private fun entry(artwork: Boolean, motion: Boolean, theme: String) {
        ui.mainClock.autoAdvance = false
        ui.setContent {
            IswaraTheme(theme, "id") {
                val base = MaterialTheme.colorScheme
                val colors = ArtworkColors.player(if(artwork) ArtworkPalette(0xff888888.toInt(), 0xffaaaaaa.toInt()) else null,base.surface.toArgb(),base.primary.toArgb())
                MaterialTheme(colorScheme = base.copy(primary = Color(colors.accent))) {
                    Surface(color = Color(colors.background)) {
                        Box(Modifier.width(320.dp).padding(24.dp)) {
                            AnimatedLyricLine("Baris lirik pengujian",true,false,motion,"glow",false) { }
                        }
                    }
                }
            }
        }
        ui.mainClock.advanceTimeBy(32)
        val first = frameHash()
        ui.mainClock.advanceTimeBy(160)
        val middle = frameHash()
        ui.mainClock.advanceTimeBy(600)
        val final = frameHash()
        if (motion) {
            assertNotEquals("The opening active line must animate even when the artwork is gray",first,middle)
            assertNotEquals("The transition must have intermediate frames",middle,final)
        } else { assertEquals(first,middle); assertEquals(middle,final) }
    }
    @Test fun paletteChangesDoNotRestartTheLyricMovement() {
        ui.mainClock.autoAdvance = false
        var artwork by mutableStateOf(true)
        ui.setContent {
            IswaraTheme("dark","id") {
                val base = MaterialTheme.colorScheme
                val palette = ArtworkColors.player(if(artwork) ArtworkPalette(0xff775533.toInt(),0xff663399.toInt()) else null,base.surface.toArgb(),base.primary.toArgb())
                MaterialTheme(colorScheme=base.copy(primary=Color(palette.accent))) {
                    Surface(Modifier.fillMaxSize(),color=Color(palette.background)) {Column(Modifier.padding(24.dp)) {AnimatedLyricLine("Tekstur gerak",true,false,true,"slide",false) {}}}
                }
            }
        }
        ui.mainClock.advanceTimeBy(112)
        val before = glyphLeft()
        ui.runOnUiThread {artwork=false}
        ui.mainClock.advanceTimeBy(96)
        val after = glyphLeft()
        assertTrue("Changing artwork colors must not move an active line backwards",after>=before)
        ui.mainClock.advanceTimeBy(600)
        val end = glyphLeft()
        assertTrue("The slide must continue after the palette switch",end>before+2)
    }
    private fun glyphLeft(): Int {
        var result=Int.MAX_VALUE
        ui.runOnIdle {
            val view=ui.activity.window.decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            for(y in 0 until image.height) for(x in 0 until image.width) {
                val pixel=image.getPixel(x,y);val r=android.graphics.Color.red(pixel);val g=android.graphics.Color.green(pixel);val b=android.graphics.Color.blue(pixel)
                if(r>170 && g>170 && b>170 && kotlin.math.abs(r-g)<25 && kotlin.math.abs(g-b)<25) result=minOf(result,x)
            }
            image.recycle()
        }
        assertNotEquals("Visible lyric glyphs must be rendered",Int.MAX_VALUE,result)
        return result
    }
    private fun frameHash(): Int {
        var result = 0
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val image = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val pixels = IntArray(image.width*image.height)
            image.getPixels(pixels,0,image.width,0,0,image.width,image.height)
            result = pixels.contentHashCode(); image.recycle()
        }
        return result
    }
}
