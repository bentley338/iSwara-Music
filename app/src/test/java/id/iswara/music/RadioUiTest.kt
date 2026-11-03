package id.iswara.music

import android.content.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import id.iswara.music.playback.PlaybackService
import id.iswara.music.ui.MusicViewModel
import org.junit.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ServiceController

// Own worker: DataStore must not retain a coroutine scheduler from another Compose runTest.
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=IswaraApp::class,qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadioUiTest {
    private val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var service:ServiceController<PlaybackService>
    private val binding=object:ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setFontScale(1.3f)
            val app=RuntimeEnvironment.getApplication()
            service=Robolectric.buildService(PlaybackService::class.java).create()
            Shadows.shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app,PlaybackService::class.java),service.get().onBind(Intent("androidx.media3.session.MediaSessionService")))
        }
        override fun after(){service.get().engine.release();service.destroy();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
    }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(binding).around(ui)
    private fun nav(label:String)=ui.onNode(hasText(label) and hasAnyAncestor(hasTestTag("bottom-navigation"))).performClick()
    @Test fun guestRoutesDeepLinkAndEveryThemeRemainUsable() {
        val vm=ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        nav("Radio");ui.onNodeWithText("Go Live").assertIsDisplayed().performClick()
        ui.onNodeWithText("Masuk ke iSwara Music").assertExists()
        nav("Radio");ui.onNodeWithText("Masukkan kode").performClick()
        ui.onNodeWithText("Masuk untuk bersiaran").assertExists()
        ui.onNodeWithContentDescription("Tutup formulir siaran").performClick()
        ui.runOnUiThread {vm.shared("iswara://radio/ABCDEFGHIJKL")};ui.waitForIdle()
        ui.onNodeWithText("Gabung siaran").assertExists();Assert.assertNull(vm.together.state.value.room)
        ui.onNodeWithContentDescription("Tutup formulir siaran").performClick()
        for(theme in listOf("dark","light","midnight","forest","dawn")) {
            ui.runOnUiThread {vm.action {vm.preferences.set("theme",theme)}}
            ui.waitUntil(15_000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));vm.settings.value.theme==theme}
            ui.waitForIdle()
            ui.runOnIdle {
                val view=ui.activity.window.decorView
                val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap));val file=java.io.File("../docs/screenshots/radio-$theme-phone.png");file.parentFile!!.mkdirs()
                file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
        }
    }
}
