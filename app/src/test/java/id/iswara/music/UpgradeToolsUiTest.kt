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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=IswaraApp::class,qualifiers="h851dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpgradeToolsUiTest {
    private val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var service:ServiceController<PlaybackService>
    private val binding=object:ExternalResource() {
        override fun before(){val app=RuntimeEnvironment.getApplication();service=Robolectric.buildService(PlaybackService::class.java).create();Shadows.shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app,PlaybackService::class.java),service.get().onBind(Intent("androidx.media3.session.MediaSessionService")))}
        override fun after(){service.get().engine.release();service.destroy();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
    }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(binding).around(ui)
    private fun capture(name:String){ui.waitForIdle();ui.runOnIdle{val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();check(dialog?.isShowing==true);val view=dialog!!.window!!.decorView;check(view.width>0&&view.height>0);val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap));val file=java.io.File("../docs/screenshots/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
    @Test fun updateImportAndCurationHaveWorkingEntryPointsAndEmptyStates() {
        val vm=ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        ui.runOnUiThread {vm.updatesOpen.value=true}
        ui.onNodeWithText("Cek pembaruan").assertIsDisplayed();capture("updates-0.9-phone")
        ui.onNodeWithContentDescription("Tutup pembaruan").performClick()
        ui.runOnUiThread {vm.importOpen.value=true}
        ui.onNodeWithText("Teks / berkas").performClick()
        ui.onNodeWithText("Artis - Judul, satu lagu per baris").performTextInput("Artis - Lagu")
        ui.onNodeWithText("Baca & cocokkan lagu").assertIsDisplayed().assertIsEnabled();capture("import-0.9-phone")
        ui.onNodeWithContentDescription("Tutup impor playlist").performClick()
        ui.runOnUiThread {vm.smartQueueOpen.value=true}
        ui.onNodeWithText("Susun rekomendasi").assertIsDisplayed().assertIsNotEnabled();capture("curation-0.9-phone")
        ui.onNodeWithContentDescription("Tutup kurasi antrean").performClick()
    }
}
