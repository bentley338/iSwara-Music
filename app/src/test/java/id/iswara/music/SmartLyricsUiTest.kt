package id.iswara.music

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import id.iswara.music.source.LyricRomanizer
import id.iswara.music.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartLyricsUiTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test fun realJapaneseReadingsAndTranslationsKeepSeekSeparateAndRenderAcrossThemes() {
        val row=LyricRomanizer().line("私は音楽が好きです","ja")
        var theme by mutableStateOf("midnight");var romanize by mutableStateOf(true);var seeks=0
        ui.setContent {
            IswaraTheme(theme,"id",accent="ocean",animations=false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        Text("Lirik pintar",style=MaterialTheme.typography.headlineLarge)
                        Text("Teks pengujian cara baca",style=MaterialTheme.typography.bodySmall)
                        SmartLyricLine(row,"Saya suka musik",true,romanize,{}, {seeks++})
                        TranslationAttribution()
                        Text("Terjemahan mesin dapat kurang tepat sesuai konteks.",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        ui.onNodeWithText(row.text).assertExists();ui.onNodeWithText(row.reading).assertExists()
        ui.onNodeWithText("Saya suka musik").assertExists()
        ui.onNodeWithContentDescription("Putar dari baris lirik").performClick();assertEquals(1,seeks)
        for(palette in listOf("midnight","forest","dawn")) {
            ui.runOnUiThread {theme=palette};ui.waitForIdle()
            ui.runOnIdle {
                val view=ui.activity.window.decorView;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap));val file=File("../docs/screenshots/smart-lyrics-$palette.png");file.parentFile!!.mkdirs()
                file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
        }
        ui.runOnUiThread {romanize=false};ui.onNodeWithText(row.reading).assertDoesNotExist()
        ui.onNodeWithText(row.text).assertExists();ui.onNodeWithText("Saya suka musik").assertExists()
    }
}
