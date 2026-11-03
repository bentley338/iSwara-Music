@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import id.iswara.music.model.Track
import id.iswara.music.model.QueueSnapshot

fun ExoPlayer.snapshotQueue() = QueueSnapshot((0 until mediaItemCount).mapNotNull { getMediaItemAt(it).track() }, currentMediaItemIndex.coerceAtLeast(0), currentPosition.coerceAtLeast(0), shuffleModeEnabled, repeatMode, playbackOrder(this))
fun ExoPlayer.restoreQueue(saved: QueueSnapshot) {
    if (saved.tracks.isEmpty()) { clearMediaItems(); return }
    setMediaItems(saved.tracks.map { it.mediaItem() }, saved.index.coerceIn(saved.tracks.indices), saved.position.coerceAtLeast(0))
    if (saved.shuffle && saved.order.size == saved.tracks.size && saved.order.toSet() == saved.tracks.indices.toSet())
        setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(saved.order.toIntArray(), System.nanoTime()))
    shuffleModeEnabled = saved.shuffle; repeatMode = saved.repeat.coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
}

fun playbackOrder(player: Player): List<Int> {
    val timeline = player.currentTimeline
    val order = ArrayList<Int>()
    var index = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
    while (index != C.INDEX_UNSET && index in 0 until player.mediaItemCount && order.size < player.mediaItemCount) {
        order += index
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
    }
    return order
}

/** Manual edits change the order the player will actually follow, including in shuffle mode. */
class PlaybackQueue(private val player: ExoPlayer) {
    private fun shuffle(order: List<Int>) { player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime())) }
    fun enqueue(track: Track, next: Boolean, duplicates: Boolean) {
        val order = playbackOrder(player).toMutableList()
        val existing = if (duplicates) -1 else (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == track.id } ?: -1
        if (existing == player.currentMediaItemIndex && existing >= 0) return
        if (player.shuffleModeEnabled) {
            val target = if (existing >= 0) existing else player.mediaItemCount.also { player.addMediaItem(track.mediaItem()) }
            order.remove(target)
            val index = if (next) (order.indexOf(player.currentMediaItemIndex) + 1).coerceAtLeast(0) else order.size
            order.add(index, target); shuffle(order)
        } else if (existing >= 0) {
            val target = if (next) (player.currentMediaItemIndex + 1 - if (existing < player.currentMediaItemIndex) 1 else 0).coerceAtMost(player.mediaItemCount - 1) else player.mediaItemCount - 1
            player.moveMediaItem(existing, target)
        } else player.addMediaItem(if (next) (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount) else player.mediaItemCount, track.mediaItem())
    }
    fun move(from: Int, to: Int) {
        if (from !in 0 until player.mediaItemCount || to !in 0 until player.mediaItemCount) return
        if (!player.shuffleModeEnabled) player.moveMediaItem(from, to) else {
            val order = playbackOrder(player).toMutableList()
            val start = order.indexOf(from); val end = order.indexOf(to)
            if (start >= 0 && end >= 0) { order.add(end, order.removeAt(start)); shuffle(order) }
        }
    }
    fun clearUpcoming() = removePart(after = true)
    fun clearPlayed() = removePart(after = false)
    fun appendRadio(tracks: List<Track>) {
        val order = playbackOrder(player)
        val first = player.mediaItemCount
        player.addMediaItems(tracks.map { it.mediaItem() })
        if (player.shuffleModeEnabled) shuffle(order + (first until player.mediaItemCount))
    }
    private fun removePart(after: Boolean) {
        val order = playbackOrder(player)
        val index = order.indexOf(player.currentMediaItemIndex)
        if (index < 0) return
        val remove = if (after) order.drop(index + 1) else order.take(index)
        remove.sortedDescending().forEach(player::removeMediaItem)
    }
}
