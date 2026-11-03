@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music

import android.content.*
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.session.*
import com.google.common.util.concurrent.ListenableFuture
import id.iswara.music.model.*
import id.iswara.music.playback.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = IswaraApp::class)
class CarLibraryTest {
    private val song = Track("car-test", "Test fixture", "Test artist", url = "https://www.youtube.com/watch?v=car-test")
    @Test fun catalogPreservesPlaylistOrderFiltersOtherDeviceAndBoundsPages() {
        val hidden = song.copy(id = "other-device", local = true, device = "other", url = "content://audio/1")
        val second = song.copy(id = "second")
        val library = Library(favorites = listOf(song, hidden), playlists = listOf(Playlist("p", "Road", listOf(second, song, hidden))))
        val children = CarLibrary.children(CarLibrary.playlistId("p"), library, "current", false)!!
        assertEquals(listOf("second", "car-test"), children.map { it.mediaId })
        assertTrue(children.all { it.mediaMetadata.isPlayable == true && it.mediaMetadata.isBrowsable == false })
        assertEquals("Road", CarLibrary.item(CarLibrary.playlistId("p"), library, "current", false)!!.mediaMetadata.title)
        assertNull(CarLibrary.item(hidden.id, library, "current", false))
        assertEquals(listOf("car-test"), CarLibrary.page(children, 1, 1).map { it.mediaId })
        assertTrue(CarLibrary.page(children, Int.MAX_VALUE, Int.MAX_VALUE).isEmpty())
        assertNull(CarLibrary.children("missing", library, "current", false))
    }
    @Test fun realMediaBrowserReadsLibraryResolvesSavedTrackAndSwitchesOwner() {
        val app = RuntimeEnvironment.getApplication() as IswaraApp
        val serviceController = Robolectric.buildService(PlaybackService::class.java).create()
        val service = serviceController.get()
        val component = ComponentName(app, PlaybackService::class.java)
        val binder = service.onBind(Intent(MediaLibraryService.SERVICE_INTERFACE))
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(component, binder)
        var browser: MediaBrowser? = null
        try {
            runBlocking { app.library.favorite(song) }
            browser = await(MediaBrowser.Builder(app, SessionToken(app, component)).buildAsync())
            val root = await(browser.getLibraryRoot(null))
            assertEquals(LibraryResult.RESULT_SUCCESS, root.resultCode)
            assertEquals(CarLibrary.ROOT, root.value!!.mediaId)
            val favorites = await(browser.getChildren(CarLibrary.FAVORITES, 0, 50, null))
            assertEquals(listOf(song.id), favorites.value!!.map { it.mediaId })
            browser.setMediaItem(MediaItem.Builder().setMediaId(song.id).build())
            repeat(100) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10)); Thread.sleep(2) }
            assertEquals(song.id, service.engine.player.currentMediaItem?.mediaId)
            assertEquals("iswara", service.engine.player.currentMediaItem?.localConfiguration?.uri?.scheme)
            service.engine.player.addMediaItem(song.copy(id = "second").mediaItem())
            service.engine.player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(intArrayOf(1, 0), 1L))
            service.engine.player.shuffleModeEnabled = true
            repeat(100) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10)); Thread.sleep(2) }
            assertEquals(listOf(1, 0), playbackOrder(browser))
            app.library.scope.value = "other-account"
            assertTrue(await(browser.getChildren(CarLibrary.FAVORITES, 0, 50, null)).value!!.isEmpty())
            assertNotEquals(LibraryResult.RESULT_SUCCESS, await(browser.getItem(song.id)).resultCode)
        } finally {
            browser?.release(); service.engine.release(); serviceController.destroy()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private fun <T> await(future: ListenableFuture<T>): T {
        repeat(500) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            if (future.isDone) return future.get()
            Thread.sleep(5)
        }
        throw AssertionError("MediaBrowser request timed out")
    }
}
