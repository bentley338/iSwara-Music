@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import id.iswara.music.model.Track
import id.iswara.music.model.AppJson
import id.iswara.music.model.QueueSnapshot
import kotlinx.serialization.encodeToString
import id.iswara.music.playback.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PlaybackQueueTest {
    private fun song(id: String) = Track(id, id, url = "https://www.youtube.com/watch?v=$id")
    private fun player(): ExoPlayer = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build().apply {
        setMediaItems(listOf("a", "b", "c", "d", "e").map { song(it).mediaItem() })
        setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(3, 1, 4, 0, 2), 5L))
        shuffleModeEnabled = true; seekTo(1, 1000)
    }
    private fun ids(player: ExoPlayer) = playbackOrder(player).map { player.getMediaItemAt(it).mediaId }
    @Test fun playNextHonorsShuffleForExistingAndNewSongsWithoutChangingCurrentTrack() {
        val player = player()
        try {
            val queue = PlaybackQueue(player)
            queue.enqueue(song("a"), true, false)
            assertEquals(listOf("d", "b", "a", "e", "c"), ids(player))
            assertEquals("a", player.getMediaItemAt(player.nextMediaItemIndex).mediaId)
            queue.enqueue(song("x"), true, false)
            assertEquals(listOf("d", "b", "x", "a", "e", "c"), ids(player))
            assertEquals("b", player.currentMediaItem!!.mediaId)
            assertEquals(6, player.mediaItemCount)
        } finally { player.release() }
    }
    @Test fun reorderAndClearUseActualShuffleOrderRatherThanStorageIndex() {
        val player = player()
        try {
            val queue = PlaybackQueue(player)
            queue.move(4, 1)
            assertEquals(listOf("d", "e", "b", "a", "c"), ids(player))
            queue.clearUpcoming()
            assertEquals(listOf("d", "e", "b"), ids(player))
            assertEquals("b", player.currentMediaItem!!.mediaId)
            queue.clearPlayed()
            assertEquals(listOf("b"), ids(player))
        } finally { player.release() }
    }
    @Test fun duplicatesAreExplicitAndEmptyShuffleQueueCanAcceptItsFirstSong() {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            player.shuffleModeEnabled = true
            val queue = PlaybackQueue(player)
            queue.enqueue(song("a"), true, false)
            queue.enqueue(song("a"), true, false)
            assertEquals(1, player.mediaItemCount)
            queue.enqueue(song("a"), true, true)
            assertEquals(2, player.mediaItemCount)
            assertEquals(1, player.nextMediaItemIndex)
        } finally { player.release() }
    }
    @Test fun normalQueueMovesExistingSongNextAndKeepsSeekPosition() {
        val player = player()
        try {
            player.shuffleModeEnabled = false
            val queue = PlaybackQueue(player)
            queue.enqueue(song("a"), true, false)
            assertEquals(listOf("b", "a", "c", "d", "e"), ids(player))
            assertEquals("b", player.currentMediaItem!!.mediaId)
            assertEquals(1000L, player.currentPosition)
        } finally { player.release() }
    }
    @Test fun radioAndSessionRestorePreserveManualShuffleEdits() {
        val original = player()
        val restored = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            val queue = PlaybackQueue(original)
            queue.enqueue(song("x"), true, false)
            queue.appendRadio(listOf(song("radio-1"), song("radio-2")))
            assertEquals(listOf("d", "b", "x", "e", "a", "c", "radio-1", "radio-2"), ids(original))
            val encoded = AppJson.encodeToString(original.snapshotQueue())
            restored.restoreQueue(AppJson.decodeFromString<QueueSnapshot>(encoded))
            assertEquals(ids(original), ids(restored))
            assertEquals("b", restored.currentMediaItem!!.mediaId)
            assertEquals(1000L, restored.currentPosition)
            assertTrue(restored.shuffleModeEnabled)
        } finally { original.release(); restored.release() }
    }
}
