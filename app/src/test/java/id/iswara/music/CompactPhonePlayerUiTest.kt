package id.iswara.music

import android.content.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import id.iswara.music.data.DeviceIdentity
import id.iswara.music.model.Track
import id.iswara.music.playback.PlaybackService
import id.iswara.music.ui.MusicViewModel
import org.junit.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=IswaraApp::class,qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactPhonePlayerUiTest {
    val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var service:ServiceController<PlaybackService>
    private val binding=object:ExternalResource(){
        override fun before(){RuntimeEnvironment.setFontScale(1.5f);val app=RuntimeEnvironment.getApplication();service=Robolectric.buildService(PlaybackService::class.java).create();Shadows.shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app,PlaybackService::class.java),service.get().onBind(Intent("androidx.media3.session.MediaSessionService")))}
        override fun after(){service.get().engine.release();service.destroy();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
    }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(binding).around(ui)
    @Test fun coreControlsStayVisibleWithoutScrollingOnSmallPhoneWithLargeText(){
        val vm=ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        val track=Track("compact-fixture","Judul lagu yang cukup panjang untuk layar kecil","Artis pengujian",duration=180,url="content://iswara-test/missing",local=true,device=DeviceIdentity.id(ui.activity))
        ui.runOnUiThread{vm.playback.play(listOf(track,track.copy(id="compact-next",title="Lagu berikutnya")))}
        ui.waitUntil(15000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));vm.playback.state.value.track!=null}
        ui.onAllNodesWithText(track.title).onLast().performClick()
        listOf("Putar atau jeda","Lagu berikutnya","Timer tidur","Equalizer","Studio suara & karaoke","Mode berkendara").forEach {label->ui.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("compact-player"))).assertIsDisplayed()}
        ui.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView ?: ui.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap))
            val file=java.io.File("../docs/screenshots/player-compact-phone.png");file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        ui.onNodeWithContentDescription("Studio suara & karaoke").performClick()
        ui.onNodeWithContentDescription("Tutup studio suara").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("Timer tidur").performClick()
        ui.onNodeWithText("15 menit").assertExists()
    }
}
