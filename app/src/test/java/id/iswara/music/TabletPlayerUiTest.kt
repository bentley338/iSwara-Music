package id.iswara.music

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import id.iswara.music.data.DeviceIdentity
import id.iswara.music.model.Track
import id.iswara.music.playback.PlaybackService
import id.iswara.music.ui.MusicViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = IswaraApp::class, qualifiers = "w800dp-h1024dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletPlayerUiTest {
    private val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var serviceController: ServiceController<PlaybackService>
    private val setup = object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setFontScale(1.5f)
            val app = RuntimeEnvironment.getApplication()
            serviceController = Robolectric.buildService(PlaybackService::class.java).create()
            val service = serviceController.get()
            Shadows.shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app, PlaybackService::class.java), service.onBind(Intent("androidx.media3.session.MediaSessionService")))
        }
        override fun after() {
            if (::serviceController.isInitialized) {
                serviceController.get().engine.release()
                serviceController.destroy()
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            }
        }
    }
    @get:Rule val rules = RuleChain.outerRule(setup).around(ui)
    @Test fun queueTimerFavoriteAndLargeTextOnTablet() {
        ui.onNodeWithText("Akun").performClick()
        ui.onNodeWithText("Buka setelan").performScrollTo().performClick()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Gelap"))
        ui.onNodeWithText("Gelap").performClick()
        val model = ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        val device = DeviceIdentity.id(ui.activity)
        // Missing local files deliberately exercise the recoverable player-error UI.
        val first = Track("ui-local-1", "Audio uji 1", "Fixture pengujian", duration = 30, url = "content://iswara-test/missing-1", local = true, device = device)
        val second = first.copy(id = "ui-local-2", title = "Audio uji 2", url = "content://iswara-test/missing-2")
        ui.runOnUiThread { model.playback.play(listOf(first, second)) }
        await { model.playback.state.value.queue.size == 2 }
        ui.onNodeWithText("Audio uji 1").performClick()
        ui.onNodeWithContentDescription("Opsi lagu").performClick()
        ui.onNodeWithText("Putar lagu").assertExists()
        captureMenu("track-menu-dark")
        ui.runOnUiThread { model.action { model.preferences.set("theme", "light") } }
        await { model.settings.value.theme == "light" }
        captureMenu("track-menu-light")
        ui.runOnUiThread { model.action { model.preferences.set("theme", "dark") } }
        await { model.settings.value.theme == "dark" }
        ui.activity.onBackPressedDispatcher.onBackPressed()
        ui.waitForIdle()
        ui.onNode(hasText("Antrean") and hasClickAction()).performClick()
        ui.onNodeWithText("2 lagu").assertExists()
        ui.onNodeWithText("Cari dalam antrean").performScrollTo().performTextInput("uji 2")
        ui.onNodeWithContentDescription("Geser ke bawah").assertIsNotEnabled()
        ui.onNodeWithContentDescription("Hapus pencarian antrean").performClick()
        ui.onAllNodesWithContentDescription("Geser ke bawah").onFirst().performClick()
        await { model.playback.state.value.queue.firstOrNull()?.id == second.id }
        ui.onAllNodesWithContentDescription("Hapus dari antrean").onFirst().performClick()
        await { model.playback.state.value.queue.size == 1 }
        ui.onAllNodesWithText("Urungkan").onLast().performClick()
        await { model.playback.state.value.queue.size == 2 }
        ui.onNodeWithText("Antrean tersimpan").performScrollTo().performClick()
        ui.onNodeWithText("Simpan antrean sekarang").performClick()
        ui.onNodeWithText("Nama antrean").performTextInput("Antrean perjalanan uji")
        ui.onNodeWithText("Simpan").performClick()
        await { model.savedQueues.value.any { it.title == "Antrean perjalanan uji" } }
        ui.onNodeWithContentDescription("Tutup antrean tersimpan").performClick()
        ui.onNode(hasText("Pemutar") and hasClickAction()).performClick()
        ui.onNodeWithContentDescription("Simpan atau hapus favorit").assertIsDisplayed().performClick()
        await { model.library.value.favorites.any { it.id == first.id } }
        ui.onNodeWithContentDescription("Timer tidur").performClick()
        ui.onNodeWithText("15 menit").performClick()
        await { model.playback.state.value.timerEnds != null }
        ui.onNodeWithContentDescription("Timer tidur").performClick()
        ui.onNodeWithText("Matikan timer").performClick()
        await { model.playback.state.value.timerEnds == null }
        ui.onNodeWithContentDescription("Studio suara & karaoke").performClick()
        ui.onAllNodesWithText("Slowed + Reverb").onLast().performClick()
        await {model.settings.value.remix=="slowed"}
        ui.onNodeWithTag("studio-list").performScrollToNode(hasContentDescription("Turunkan nada"))
        ui.onNodeWithContentDescription("Turunkan nada").performClick()
        await {model.settings.value.pitch=="-1"}
        ui.onNodeWithTag("studio-list").performScrollToNode(hasText("Nightcore"))
        ui.onNode(hasText("Nightcore") and hasAnyAncestor(hasTestTag("studio-list"))).performClick()
        await {model.settings.value.remix=="nightcore"}
        ui.onNodeWithTag("studio-list").performScrollToNode(hasText("Reset studio"))
        ui.onNodeWithText("Reset studio").performClick()
        await {model.settings.value.remix=="off" && model.settings.value.pitch=="0"}
        ui.onNodeWithContentDescription("Tutup studio suara").performClick()
        ui.onNode(hasText("Lirik") and hasClickAction()).performClick()
        ui.onNodeWithText("Cari / impor lirik").performClick()
        ui.onNodeWithText("Judul dan artis").assertExists()
        ui.onNodeWithText("Impor LRC").assertExists()
        ui.onNodeWithContentDescription("Tutup pencarian lirik").performClick()
        ui.runOnUiThread { model.chooseLyrics(first,id.iswara.music.source.LyricCandidate("lrclib:test","Audio uji 1","Fixture pengujian",30,"lrclib",synced="[00:00.00]Baris lirik pengujian\n[00:01.00]Baris kedua")) }
        await {model.lyrics.value?.lines?.size == 2 && !model.lyricsLoading.value}
        ui.onNodeWithText("Atur waktu").performClick()
        ui.onNodeWithText("Majukan 0,5 dtk").performClick()
        await {model.lyricOffset.value==500L}
        ui.onNodeWithText("Reset waktu").performClick()
        await {model.lyricOffset.value==0L}
        ui.onNodeWithText("Selesai").performClick()
        ui.onNodeWithText("Kartu lirik").performClick()
        ui.onAllNodesWithText("Baris lirik pengujian").onLast().performClick()
        ui.onAllNodesWithText("Baris kedua").onLast().performClick()
        ui.onNodeWithText("Lanjut").performClick()
        ui.waitUntil(15_000) {ui.onAllNodesWithContentDescription("Pratinjau kartu lirik").fetchSemanticsNodes().isNotEmpty() && ui.onAllNodesWithText("Bagikan").fetchSemanticsNodes().any {!it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)}}
        ui.onNodeWithText("Bagikan").assertIsEnabled()
        ui.onNodeWithText("Kertas").performClick()
        ui.onNodeWithText("Post 4:5").performClick()
        ui.waitUntil(15_000) {ui.onAllNodesWithContentDescription("Pratinjau kartu lirik").fetchSemanticsNodes().isNotEmpty() && ui.onAllNodesWithText("Bagikan").fetchSemanticsNodes().any {!it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)}}
        captureMenu("lyrics-card-studio")
        ui.onNodeWithText("Audio").performClick()
        ui.onNodeWithText("Buat potongan audio").assertExists()
        ui.onNodeWithText("Video + audio").performClick()
        ui.onNodeWithText("Buat video lirik").assertExists()
        ui.onNodeWithText("Lirik mengikuti audio").assertIsEnabled().performClick()
        ui.onNodeWithText("Lirik mengikuti audio").performClick()

        ui.onNodeWithText("Tutup").performClick()
        var displayedLyrics = false
        repeat(3) {
            ui.runOnUiThread {
                model.lyrics.value = id.iswara.music.source.Lyrics(lines = listOf(id.iswara.music.model.LyricLine(0, "Baris uji lirik"), id.iswara.music.model.LyricLine(1000, "Baris berikutnya")))
                model.lyricsLoading.value = false; model.lyricsError.value = null
            }
            ui.waitForIdle()
            displayedLyrics = displayedLyrics || ui.onAllNodesWithText("Baris uji lirik").fetchSemanticsNodes().isNotEmpty()
            ui.runOnUiThread { model.lyrics.value = null; model.lyricsLoading.value = true }
            ui.waitForIdle()
        }
        org.junit.Assert.assertTrue("Lyrics were visible before being cleared", displayedLyrics)
        ui.onNode(hasText("Antrean") and hasClickAction()).performClick()
        ui.onNodeWithText("Kosongkan").performClick()
        await { model.playback.state.value.queue.isEmpty() }
        ui.onAllNodesWithText("Urungkan").onFirst().performClick()
        await { model.playback.state.value.queue.size == 2 }
        org.junit.Assert.assertEquals(listOf(second.id, first.id), model.playback.state.value.queue.map { it.id })
    }
    private fun captureMenu(name: String) {
        ui.waitForIdle()
        ui.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val file = java.io.File("../docs/screenshots/$name.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
        }
    }
    private fun await(condition: () -> Boolean) = ui.waitUntil(15_000) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        condition()
    }
}
