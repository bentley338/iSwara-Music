package id.iswara.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import id.iswara.music.playback.PlaybackService
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = IswaraApp::class, qualifiers = "h851dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiFlowTest {
    val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var serviceController: ServiceController<PlaybackService>
    private val serviceBinding = object : ExternalResource() {
        override fun before() {
            val app = RuntimeEnvironment.getApplication()
            serviceController = Robolectric.buildService(PlaybackService::class.java).create()
            val service = serviceController.get()
            val binder = service.onBind(Intent("androidx.media3.session.MediaSessionService"))
            Shadows.shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app, PlaybackService::class.java), binder)
        }
        override fun after() {
            if (::serviceController.isInitialized) {
                serviceController.get().engine.release()
                serviceController.destroy()
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            }
        }
    }
    @get:Rule val rules = RuleChain.outerRule(serviceBinding).around(ui)
    @Test fun navigationPlaylistAndThemes() {
        ui.onNodeWithText("iSwara Music").assertExists()
        ui.onNodeWithText("Denger bareng").performScrollTo().performClick()
        ui.onNodeWithText("Masuk untuk denger bareng").assertExists()
        ui.onNodeWithContentDescription("Tutup denger bareng").performClick()
        capture("home")
        ui.onNodeWithText("Koleksi", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("Buat playlist").performClick()
        ui.onNodeWithText("Nama playlist").performTextInput("Uji perangkat")
        ui.onNodeWithText("Simpan").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Nama playlist").fetchSemanticsNodes().isEmpty() }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Uji perangkat").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Uji perangkat").performClick()
        ui.onNodeWithText("Playlist pribadi").assertExists()
        ui.onNodeWithText("Ubah nama").performClick()
        ui.onNodeWithText("Nama playlist").performTextClearance()
        ui.onNodeWithText("Nama playlist").performTextInput("Playlist perjalanan")
        ui.onNodeWithText("Simpan").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Nama playlist").fetchSemanticsNodes().isEmpty() }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Playlist perjalanan").fetchSemanticsNodes().isNotEmpty() }
        capture("playlist")
        ui.onNodeWithContentDescription("Kembali ke koleksi").performClick()
        ui.onNodeWithText("Cari", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Judul lagu atau nama artis").assertExists()
        capture("search")
        ui.onNodeWithText("Ruang", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("Buka akun").performClick()
        ui.onNodeWithText("Masuk ke iSwara Music").assertExists()
        ui.onNodeWithText("Panel admin").assertDoesNotExist()
        capture("settings-cloud")
        ui.onNodeWithText("Buka setelan").performScrollTo().performClick()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Gelap"))
        ui.onNodeWithText("Gelap").performClick()
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); androidx.lifecycle.ViewModelProvider(ui.activity)[id.iswara.music.ui.MusicViewModel::class.java].settings.value.theme == "dark" }
        ui.waitForIdle()
        capture("settings-dark")
        ui.onNodeWithText("Terang").performClick()
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); androidx.lifecycle.ViewModelProvider(ui.activity)[id.iswara.music.ui.MusicViewModel::class.java].settings.value.theme == "light" }
        ui.waitForIdle()
        capture("settings-light")
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("English"))
        ui.onNodeWithText("English").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Language").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("settings-list").performScrollToIndex(0)
        capture("settings-english")
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Indonesia"))
        ui.onNodeWithText("Indonesia").performClick()
        val vm = androidx.lifecycle.ViewModelProvider(ui.activity)[id.iswara.music.ui.MusicViewModel::class.java]
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.settings.value.language == "id" }
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Lewati bagian hening"))
        ui.onNodeWithContentDescription("Lewati bagian hening").performScrollTo().performClick()
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.settings.value.skipSilence }
        ui.onNodeWithContentDescription("Lewati bagian hening").assertIsOn()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("1.25x"))
        ui.onNodeWithText("1.25x").performScrollTo().performClick()
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.settings.value.speed == "1.25" }
        capture("settings-audio")
        ui.onNodeWithTag("settings-list").performScrollToIndex(0)
        ui.onNodeWithContentDescription("Tutup setelan").performClick()
        ui.onNodeWithText("Masuk ke iSwara Music").assertExists()
        val album = id.iswara.music.model.CatalogItem("ui-album", "Album uji", "Artis uji", "", "https://music.youtube.com/playlist?list=ui-test", "playlist")
        ui.runOnUiThread { vm.closeDetail(); vm.detail.value = id.iswara.music.ui.DetailState(album.title, album.url, loading = false, subtitle = album.subtitle) }
        ui.onNodeWithText("Album uji").assertExists()
        ui.onNodeWithContentDescription("Perbesar sampul").performClick()
        ui.onNodeWithText("Tutup sampul").performClick()
        capture("album-detail")
        ui.onNodeWithContentDescription("Kembali").performClick()
        ui.onNodeWithText("Beranda", useUnmergedTree = true).performClick()
        ui.onNodeWithContentDescription("Rekap dengar").performClick()
        ui.onNodeWithText("Bulan ini").assertExists()
        capture("recap", dialog = true)
        ui.onNodeWithContentDescription("Tutup rekap").performClick()
        ui.onNodeWithContentDescription("Setelan").performClick()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Equalizer & bass"))
        ui.onNodeWithText("Equalizer & bass").performClick()
        ui.onNodeWithText("Rock").performClick()
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.settings.value.eqPreset == "Rock" }
        capture("equalizer", dialog = true)
        ui.onNodeWithContentDescription("Tutup equalizer").performClick()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Mode berkendara"))
        ui.onNodeWithText("Mode berkendara").performClick()
        ui.onNodeWithContentDescription("Tutup mode berkendara").assertExists()
        capture("car-mode", dialog = true)
        ui.onNodeWithContentDescription("Tutup mode berkendara").performClick()
        ui.onNodeWithText("Koleksi", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Unduhan").performScrollTo().performClick()
        ui.onNodeWithText("Belum ada lagu").assertExists()
    }
    private fun capture(name: String, dialog: Boolean = false) {
        val directory = File("../docs/screenshots").apply { mkdirs() }
        ui.waitForIdle()
        ui.mainClock.advanceTimeBy(64)
        ui.waitForIdle()
        ui.runOnIdle {
            val view = if (dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView ?: error("Dialog window missing") else ui.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
