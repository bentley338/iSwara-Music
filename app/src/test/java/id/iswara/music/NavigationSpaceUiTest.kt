package id.iswara.music

import android.content.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import id.iswara.music.model.Track
import id.iswara.music.playback.PlaybackService
import id.iswara.music.ui.*
import org.junit.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = IswaraApp::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationSpaceUiTest {
    private val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var service: ServiceController<PlaybackService>
    private val binding = object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setFontScale(1.3f)
            val app = RuntimeEnvironment.getApplication()
            service = Robolectric.buildService(PlaybackService::class.java).create()
            Shadows.shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app, PlaybackService::class.java), service.get().onBind(Intent("androidx.media3.session.MediaSessionService")))
        }
        override fun after() { service.get().engine.release(); service.destroy(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(binding).around(ui)
    private fun nav(label: String) {
        if(label=="Jelajah") {
            nav("Beranda")
            ui.onNodeWithTag("home-list").performScrollToNode(hasText("Jelajah musik"))
            ui.onNodeWithText("Jelajah musik").performClick()
        } else ui.onNode(hasText(label) and hasAnyAncestor(hasTestTag("bottom-navigation"))).performClick()
    }
    private fun feature(id: String, query: String) {
        ui.onNodeWithText("Cari fitur").performTextReplacement(query)
        ui.onNodeWithTag("space-list").performScrollToNode(hasTestTag("feature-$id"))
        ui.onNodeWithTag("feature-$id").performClick()
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val file = java.io.File("../docs/screenshots/$name.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
    }
    @Test fun tappingAnIndieShelfTrackStartsOnlyThatTrack() {
        val vm = ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        val tracks = (1..3).map {
            Track("indie-$it", "Lagu indie $it", "Artis $it", duration = 180, url = "content://test/indie-$it", local = true)
        }
        ui.runOnUiThread { vm.shelves.value = listOf(Shelf("Indie pilihan", "indie fixture songs", tracks, false)) }
        ui.onNodeWithTag("home-list").performScrollToNode(hasText("Indie pilihan"))
        ui.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText(tracks.first().title)) and
            hasAnyAncestor(hasTestTag("home-list"))).performScrollToIndex(1)
        ui.onNodeWithText(tracks[1].title).performScrollTo().performClick()
        ui.waitUntil(10_000) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            vm.playback.state.value.queue.map { it.id } == listOf(tracks[1].id)
        }
        Assert.assertEquals(tracks[1].id, vm.playback.state.value.track?.id)
    }

    @Test fun phoneMenusFeatureSearchShortcutsDialogsAndMixPreview() {
        val vm = ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        ui.onNodeWithTag("bottom-navigation").assertIsDisplayed()
        nav("Jelajah")
        ui.onNodeWithText("Genre").performClick()
        ui.onNodeWithTag("explore-list").performScrollToNode(hasText("Dangdut"))
        ui.onNodeWithText("Dangdut").performClick()
        ui.onNodeWithText("Judul lagu atau nama artis").assertExists()
        Assert.assertEquals("dangdut Indonesia", vm.search.value.query)
        nav("Jelajah")
        ui.onNodeWithTag("explore-list").performScrollToIndex(0)
        ui.onNodeWithText("Mulai dari suasana").performClick()
        ui.onNodeWithText("Susun mix").assertIsNotEnabled()
        ui.onNodeWithText("Lagi ingin dengar apa?").performTextInput("akustik hujan")
        ui.onNodeWithText("Susun mix").assertIsEnabled()
        val tracks = listOf(Track("menu-mix-a", "Lagu mix uji pertama", "Artis uji", duration = 180, url = "content://test/missing-a", local = true), Track("menu-mix-b", "Lagu mix uji kedua", "Artis uji", duration = 200, url = "content://test/missing-b", local = true))
        val before = vm.playback.state.value.track
        ui.runOnUiThread { vm.mixPreview.value = MixPreview("akustik hujan", tracks, owner = vm.libraryRepository.scope.value) }
        ui.onNodeWithTag("mix-list").performScrollToNode(hasText("Hasil mix"))
        Assert.assertEquals(before, vm.playback.state.value.track)
        ui.onNodeWithTag("mix-list").performScrollToNode(hasText("Keluarkan dari mix"))
        ui.onAllNodesWithText("Keluarkan dari mix").onFirst().performClick()
        Assert.assertEquals(listOf(tracks.last()), vm.mixPreview.value.tracks)
        ui.runOnUiThread { vm.saveMix("Mix navigasi uji") }
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); vm.library.value.playlists.any { it.title == "Mix navigasi uji" } }
        capture("mix-preview-phone")
        ui.onNodeWithContentDescription("Tutup pembuat mix").performScrollTo().performClick()
        nav("Ruang")
        ui.onNodeWithTag("feature-admin").assertDoesNotExist()
        for (theme in listOf("light", "dark")) {
            ui.runOnUiThread { vm.action { vm.preferences.set("theme", theme) } }
            ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); vm.settings.value.theme == theme }
            ui.onNodeWithText("Cari fitur").performTextClearance()
            ui.onNodeWithTag("space-list").performScrollToIndex(0)
            capture("space-$theme-phone")
        }
        ui.onNodeWithText("Cari fitur").performTextReplacement("tidak-ada-fitur")
        ui.onNodeWithText("Fitur tidak ditemukan").assertExists()
        ui.onNodeWithText("Cari fitur").performTextReplacement("Studio audio")
        ui.onNodeWithTag("space-list").performScrollToNode(hasTestTag("feature-studio"))
        ui.onNodeWithContentDescription("Pintasan: Studio audio").performClick()
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); ui.onAllNodesWithContentDescription("Pintasan: Studio audio").fetchSemanticsNodes().single().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.Off }
        ui.onNodeWithContentDescription("Pintasan: Studio audio").performClick()
        feature("studio", "Studio audio")
        ui.onNodeWithContentDescription("Tutup studio suara").assertIsDisplayed().performClick()
        feature("eq", "Equalizer")
        ui.onNodeWithContentDescription("Tutup equalizer").performClick()
        feature("banks", "Antrean tersimpan")
        ui.onNodeWithContentDescription("Tutup antrean tersimpan").performClick()
        feature("recap", "Rekap")
        ui.onNodeWithContentDescription("Tutup rekap").performClick()
        feature("car", "Mode berkendara")
        ui.onNodeWithContentDescription("Tutup mode berkendara").performClick()
        feature("widgets", "Widget layar")
        ui.onAllNodesWithText("Widget layar utama").onLast().assertExists()
        ui.onNodeWithText("Tutup").performClick()
        feature("together", "Denger Bareng")
        ui.onNodeWithText("Masuk untuk denger bareng").assertExists()
        ui.onNodeWithContentDescription("Tutup denger bareng").performClick()
        feature("offline", "Pusat offline")
        ui.onNodeWithText("0 lagu siap offline").assertExists()
        ui.onNodeWithText("Cari unduhan").performTextInput("kosong")
        ui.onNodeWithTag("offline-list").performScrollToNode(hasText("Tidak ada hasil"))
        capture("offline-dark-phone")
        ui.onNodeWithContentDescription("Tutup pusat offline").performScrollTo().performClick()
        feature("local", "Musik perangkat")
        ui.onNodeWithText("Pindai musik").assertExists()
        nav("Ruang")
        ui.onNodeWithContentDescription("Buka akun").performScrollTo().performClick()
        ui.onNodeWithText("Masuk ke iSwara Music").assertExists()
    }
    // Robolectric #8460: explicit window width freezes Dialog + TextField layout.
    @Test @Config(qualifiers = "h851dp-xxhdpi") fun linkDialogDocumentActionsAndDirectPlayerTabs() {
        val vm = ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        nav("Ruang")
        feature("link", "Buka tautan")
        ui.onNodeWithText("Tautan lagu atau playlist").performTextInput("bukan-tautan")
        ui.onNodeWithText("Simpan").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Tautan musik tidak ditemukan").fetchSemanticsNodes().isNotEmpty() }
        feature("backup", "Ekspor backup")
        Assert.assertEquals(Intent.ACTION_CREATE_DOCUMENT, Shadows.shadowOf(ui.activity).nextStartedActivityForResult.intent.action)
        feature("restore", "Impor backup")
        Assert.assertEquals(Intent.ACTION_OPEN_DOCUMENT, Shadows.shadowOf(ui.activity).nextStartedActivityForResult.intent.action)
        feature("settings", "Pengaturan")
        ui.onNodeWithText("Gelap").assertExists()
        ui.onNodeWithContentDescription("Tutup setelan").performClick()
        val track = Track("direct-menu-fixture", "Lagu pengujian menu", "Artis uji", duration = 180, url = "content://iswara-test/missing", local = true, device = id.iswara.music.data.DeviceIdentity.id(ui.activity))
        ui.runOnUiThread { vm.playback.play(listOf(track)) }
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); vm.playback.state.value.track != null }
        feature("lyrics", "Studio lirik")
        ui.onNodeWithText("Lirik").assertIsSelected()
        ui.onNodeWithContentDescription("Tutup pemutar").performClick()
        feature("queue", "Antrean sekarang")
        ui.onNodeWithText("Antrean").assertIsSelected()
        ui.onNodeWithText("Cari dalam antrean").assertExists()
        ui.onNodeWithContentDescription("Tutup pemutar").performClick()
    }
    @Test fun realMixSearchDoesNotAutoplayAndClearsOnAccountSwitch() {
        org.junit.Assume.assumeTrue(System.getenv("ISWARA_LIVE_TEST") == "1")
        val vm = ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        val previous = vm.playback.state.value.track
        ui.runOnUiThread { vm.previewMix("pop Indonesia", 10) }
        ui.waitUntil(45_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); !vm.mixPreview.value.loading }
        Assert.assertNull(vm.mixPreview.value.error)
        Assert.assertTrue(vm.mixPreview.value.tracks.size in 1..10)
        Assert.assertTrue(vm.mixPreview.value.tracks.all { it.duration in 30..1800 && it.url.startsWith("https://") })
        Assert.assertEquals(previous, vm.playback.state.value.track)
        ui.runOnUiThread { vm.libraryRepository.scope.value = "another-test-owner" }
        ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); vm.mixPreview.value.tracks.isEmpty() }
    }
    @Test @Config(qualifiers = "w1024dp-h768dp-xhdpi") fun tabletUsesSideNavigationAndBothThemes() {
        val vm = ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        ui.onNodeWithTag("side-navigation").assertIsDisplayed()
        ui.onNodeWithTag("bottom-navigation").assertDoesNotExist()
        ui.onNodeWithText("Ruang").performClick()
        ui.onNodeWithText("Ruang musikmu").assertExists()
        for (theme in listOf("dark", "light")) {
            ui.runOnUiThread { vm.action { vm.preferences.set("theme", theme) } }
            ui.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); vm.settings.value.theme == theme }
            capture("space-$theme-tablet")
        }
        ui.onNodeWithText("Akun").performScrollTo().performClick()
        ui.onNodeWithText("Masuk ke iSwara Music").assertExists()
        ui.onNodeWithText("Jelajah").performScrollTo().performClick()
        ui.onNodeWithText("Genre").performClick()
        capture("explore-light-tablet")
    }
}
