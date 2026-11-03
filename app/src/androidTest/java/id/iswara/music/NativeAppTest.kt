package id.iswara.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.iswara.music.source.YoutubeMusicSource
import id.iswara.music.ui.MusicViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeAppTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun createAndRenamePlaylistOnDevice() {
        val title = "Uji native ${System.currentTimeMillis()}"
        ui.onNodeWithText("Koleksi").performClick()
        ui.onNodeWithContentDescription("Buat playlist").performClick()
        ui.onNodeWithText("Nama playlist").performTextInput(title)
        ui.onNodeWithText("Simpan").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Nama playlist").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText(title).performClick()
        ui.onNodeWithText("Ubah nama").performClick()
        ui.onNodeWithText("Nama playlist").performTextClearance()
        ui.onNodeWithText("Nama playlist").performTextInput("$title selesai")
        ui.onNodeWithText("Simpan").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Nama playlist").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("$title selesai").assertExists()
    }
    @Test fun liveAudioContinuesWithScreenOff() = runBlocking {
        assumeTrue("Pass -Pandroid.testInstrumentationRunnerArguments.live=true", InstrumentationRegistry.getArguments().getString("live") == "true")
        val track = YoutubeMusicSource().search("Tulus Hati Hati di Jalan").items.firstNotNullOf { it.track }
        lateinit var model: MusicViewModel
        ui.runOnUiThread { model = ViewModelProvider(ui.activity)[MusicViewModel::class.java]; model.playback.play(listOf(track)) }
        ui.waitUntil(60_000) { model.playback.state.value.let { it.playing && it.position > 1000 } }
        val before = model.playback.state.value.position
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeShellCommand("input keyevent 223").close()
            Thread.sleep(2500)
            assertTrue("Background audio advances with screen off", model.playback.state.value.position > before + 500)
        } finally {
            automation.executeShellCommand("input keyevent 224").close()
            automation.executeShellCommand("input keyevent 82").close()
            ui.runOnUiThread { model.playback.clear() }
        }
    }
}
