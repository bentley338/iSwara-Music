package id.iswara.music

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import id.iswara.music.ui.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class LyricsSelectionUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun selectAllCanReturnMoreThanFourLinesInOriginalOrder() {
        var result=emptyList<Int>()
        ui.setContent {IswaraTheme("dark","id"){LyricsExcerptPicker(List(12){"Baris pengujian $it"},{}) {result=it}}}
        ui.onNodeWithText("Pilih semua").performClick()
        ui.onNodeWithText("12 baris").assertExists()
        ui.onNodeWithText("Lanjut").performClick()
        Assert.assertEquals((0 until 12).toList(),result)
    }
}
