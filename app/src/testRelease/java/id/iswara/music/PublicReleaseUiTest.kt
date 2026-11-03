package id.iswara.music

import android.content.*
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import id.iswara.music.cloud.AdminAccess
import id.iswara.music.playback.PlaybackService
import id.iswara.music.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=IswaraApp::class,qualifiers="h851dp-xxhdpi")
class PublicReleaseUiTest {
    private val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var service:ServiceController<PlaybackService>
    private val binding=object:ExternalResource() {
        override fun before(){val app=RuntimeEnvironment.getApplication();service=Robolectric.buildService(PlaybackService::class.java).create();Shadows.shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app,PlaybackService::class.java),service.get().onBind(Intent("androidx.media3.session.MediaSessionService")))}
        override fun after(){service.get().engine.release();service.destroy();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
    }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(binding).around(ui)

    @Test fun fullReleaseKeepsNativeAdminButGuestAndForgedLocalAccessCannotExposeAccountControls() {
        assertTrue(BuildConfig.ADMIN_TOOLS_ENABLED)
        assertTrue(SpaceRules.pins("admin,studio",true).contains(SpaceFeature.ADMIN))
        assertFalse(SpaceRules.pins("admin,studio",false).contains(SpaceFeature.ADMIN))
        assertFalse(SpaceRules.matches(SpaceFeature.ADMIN,"",0,false))
        val vm=ViewModelProvider(ui.activity)[MusicViewModel::class.java]
        ui.runOnUiThread {vm.adminAccess.value=AdminAccess("forged-owner",true)}
        assertFalse(runBlocking {vm.admin.access()}.allowed)
        ui.runOnUiThread {ui.activity.setContent {IswaraTheme("dark","id") {
            SpaceScreen(vm){}
        }}}
        ui.onNodeWithText("Ruang musikmu").assertIsDisplayed()
        ui.onNodeWithText("Panel admin").assertDoesNotExist()
        ui.runOnUiThread {ui.activity.setContent {IswaraTheme("dark","id") {AccountScreen(vm){}}}}
        ui.onNodeWithText("Panel admin").assertDoesNotExist()
    }
}
