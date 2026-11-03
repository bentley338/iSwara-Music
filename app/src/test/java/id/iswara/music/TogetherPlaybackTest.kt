@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music

import androidx.media3.common.Player
import id.iswara.music.model.Track
import id.iswara.music.playback.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[26],application=IswaraApp::class)
class TogetherPlaybackTest : TogetherPlaybackChecks()

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=IswaraApp::class)
class TogetherPlaybackModernTest : TogetherPlaybackChecks()

abstract class TogetherPlaybackChecks {
    @Test fun roomEditsAndTransportDoNotReplaceThePersonalQueueOnExit() {
        val controller=Robolectric.buildService(PlaybackService::class.java).create()
        val engine=controller.get().engine
        val a=Track("aaaaaaaaaaa","A",url="https://www.youtube.com/watch?v=aaaaaaaaaaa")
        val b=a.copy(id="bbbbbbbbbbb",title="B")
        try {
            engine.player.setMediaItems(listOf(a.mediaItem(),b.mediaItem()))
            engine.player.seekTo(1,42_000);engine.player.shuffleModeEnabled=true;engine.player.repeatMode=Player.REPEAT_MODE_ALL
            engine.enterTogether("member")
            engine.player.setMediaItems(listOf(a.mediaItem()))
            engine.player.seekTo(0,1000)
            engine.exitTogether()
            assertEquals(2,engine.player.mediaItemCount)
            assertEquals("bbbbbbbbbbb",engine.player.currentMediaItem!!.mediaId)
            assertEquals(42_000L,engine.player.currentPosition)
            assertTrue(engine.player.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_ALL,engine.player.repeatMode)
            assertFalse(engine.player.playWhenReady);assertNull(engine.roomRole.value)
        } finally {engine.release();controller.destroy();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()}
    }
}
