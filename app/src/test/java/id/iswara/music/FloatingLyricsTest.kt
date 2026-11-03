package id.iswara.music

import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.room.Room
import id.iswara.music.data.IswaraDatabase
import id.iswara.music.model.Track
import id.iswara.music.playback.FloatingLyrics
import id.iswara.music.playback.mediaItem
import id.iswara.music.source.LyricCandidate
import id.iswara.music.source.LyricsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=IswaraApp::class)
class FloatingLyricsTest {
    @Test fun overlayRequiresPermissionFollowsLyricsHidesOnPauseClosesAndHandlesRevocation() {
        val app=RuntimeEnvironment.getApplication() as IswaraApp
        val database=Room.inMemoryDatabaseBuilder(app,IswaraDatabase::class.java).build()
        val lyrics=LyricsRepository(database,app.library)
        val track=Track("overlay-test","Lagu overlay","Artis uji",url="content://iswara-test/overlay")
        runBlocking {lyrics.choose(track,LyricCandidate("lrclib:fixture","Lagu overlay","Artis uji",30,"lrclib",synced="[00:00.00]Baris pertama\n[00:01.00]Baris kedua"));app.preferences.set("floatingLyrics","true")}
        var playing=true
        val player=java.lang.reflect.Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) {_,method,_ ->
            when(method.name) {"getCurrentMediaItem"->track.mediaItem();"isPlaying"->playing;"getCurrentPosition"->1500L;else->null}
        } as Player
        val manager=app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val windows=Shadow.extract<ShadowWindowManagerImpl>(manager)
        val floating=FloatingLyrics(app,app.preferences,lyrics,app.library)
        Shadows.shadowOf(app.getSystemService(android.os.PowerManager::class.java)).setIsInteractive(true)
        Shadows.shadowOf(app.getSystemService(android.app.KeyguardManager::class.java)).setKeyguardLocked(false)
        fun waitFor(condition:()->Boolean) {
            val deadline=System.nanoTime()+10_000_000_000L
            while(!condition() && System.nanoTime()<deadline) {Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));Thread.sleep(10)}
            assertTrue(condition())
        }
        try {
            ShadowSettings.setCanDrawOverlays(false);floating.start(player)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(windows.views.isEmpty())
            ShadowSettings.setCanDrawOverlays(true)
            runBlocking {app.preferences.set("floatingSize","26")}
            waitFor {windows.views.isNotEmpty()}
            val root=windows.views.single() as LinearLayout
            val label=root.getChildAt(1) as TextView
            waitFor {label.text.toString()=="Baris kedua"}
            assertEquals(View.VISIBLE,root.visibility)
            playing=false;waitFor {root.visibility==View.GONE}
            playing=true;waitFor {root.visibility==View.VISIBLE}
            ((root.getChildAt(0) as LinearLayout).getChildAt(1) as Button).performClick()
            waitFor {windows.views.isEmpty()}
            runBlocking {app.preferences.set("floatingLyrics","true")}
            waitFor {windows.views.isNotEmpty()}
            ShadowSettings.setCanDrawOverlays(false)
            waitFor {windows.views.isEmpty()}
        } finally {floating.stop();database.close()}
    }
}
