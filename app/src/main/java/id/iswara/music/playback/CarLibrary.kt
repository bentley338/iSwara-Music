@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import android.net.Uri
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.*
import id.iswara.music.data.LibraryRepository
import id.iswara.music.model.*
import id.iswara.music.source.MusicSource
import kotlinx.coroutines.*

object CarLibrary {
    const val ROOT = "iswara:library"
    const val FAVORITES = "iswara:favorites"
    const val PLAYLISTS = "iswara:playlists"
    const val HISTORY = "iswara:history"
    private const val PLAYLIST = "iswara:playlist:"
    fun playlistId(id: String) = PLAYLIST + id
    fun folder(id: String, title: String, artwork: String = ""): MediaItem = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setArtworkUri(artwork.takeIf { it.isNotBlank() }?.let(Uri::parse)).build()).build()
    fun playable(track: Track): MediaItem {
        val item = track.mediaItem()
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setIsBrowsable(false).setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
    }
    fun tracks(library: Library, device: String): List<Track> = (library.favorites + library.history + library.playlists.flatMap { it.tracks })
        .filter { !it.local || it.device == device }.distinctBy { it.id }
    fun children(parent: String, library: Library, device: String, english: Boolean): List<MediaItem>? = when {
        parent == ROOT -> listOf(folder(FAVORITES, if (english) "Favorites" else "Favorit"), folder(PLAYLISTS, "Playlist"), folder(HISTORY, if (english) "Recently played" else "Baru diputar"))
        parent == PLAYLISTS -> library.playlists.map { folder(playlistId(it.id), it.title, it.tracks.firstOrNull()?.artwork.orEmpty()) }
        parent == FAVORITES -> library.favorites.filter { !it.local || it.device == device }.map(::playable)
        parent == HISTORY -> library.history.filter { !it.local || it.device == device }.map(::playable)
        parent.startsWith(PLAYLIST) -> library.playlists.find { it.id == parent.removePrefix(PLAYLIST) }?.tracks?.filter { !it.local || it.device == device }?.map(::playable)
        else -> null
    }
    fun item(id: String, library: Library, device: String, english: Boolean): MediaItem? =
        if (id == ROOT) folder(ROOT, "iSwara Music") else children(ROOT, library, device, english)!!.find { it.mediaId == id }
            ?: children(PLAYLISTS, library, device, english)!!.find { it.mediaId == id }
            ?: tracks(library, device).find { it.id == id }?.let(::playable)
    fun page(items: List<MediaItem>, page: Int, size: Int): List<MediaItem> {
        require(page >= 0 && size > 0)
        val limit = size.coerceAtMost(500)
        val start = (page.toLong() * limit).coerceAtMost(items.size.toLong()).toInt()
        return items.subList(start, (start.toLong() + limit).coerceAtMost(items.size.toLong()).toInt())
    }
}

class CarLibraryCallback(
    private val library: LibraryRepository,
    private val device: String,
    private val english: () -> Boolean,
    private val scope: CoroutineScope,
    private val source: MusicSource,
    private val newQueue: () -> Unit = {},
    private val roomMember: () -> Boolean = {false},
) : MediaLibrarySession.Callback {
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        if (controller.isTrusted || controller.uid == android.os.Process.myUid()) MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        else MediaSession.ConnectionResult.reject()

    @Suppress("DEPRECATION")
    override fun onPlayerCommandRequest(session:MediaSession,controller:MediaSession.ControllerInfo,playerCommand:Int):Int =
        if(roomMember() && playerCommand in setOf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,Player.COMMAND_SEEK_TO_MEDIA_ITEM,Player.COMMAND_CHANGE_MEDIA_ITEMS,Player.COMMAND_SET_MEDIA_ITEM,Player.COMMAND_SET_REPEAT_MODE,Player.COMMAND_SET_SHUFFLE_MODE,Player.COMMAND_SET_SPEED_AND_PITCH,Player.COMMAND_SEEK_BACK,Player.COMMAND_SEEK_FORWARD)) SessionError.ERROR_PERMISSION_DENIED else SessionResult.RESULT_SUCCESS

    private fun <T> query(block: suspend (Library) -> T): ListenableFuture<T> {
        val owner = library.scope.value
        val future = SettableFuture.create<T>()
        val job = scope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { library.snapshot(owner) }
                check(owner == library.scope.value) { "Akun berubah. Buka kembali koleksi." }
                val result = block(snapshot)
                check(owner == library.scope.value) { "Akun berubah. Buka kembali koleksi." }
                future.set(result)
            } catch (e: CancellationException) { future.cancel(false) } catch (e: Exception) { future.setException(e) }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return future
    }
    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(CarLibrary.folder(CarLibrary.ROOT, "iSwara Music"), params))

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = query { snapshot ->
        CarLibrary.item(mediaId, snapshot, device, english())?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }
    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = query { snapshot ->
        val children = CarLibrary.children(parentId, snapshot, device, english())
        if (children == null || page < 0 || pageSize <= 0) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        else LibraryResult.ofItemList(CarLibrary.page(children, page, pageSize), params)
    }
    override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
        val search = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
        if (!search.isNullOrBlank()) return query {
            val tracks = withTimeout(15_000) { source.search(search.trim().take(200)).items.mapNotNull { it.track }.take(20) }
            check(tracks.isNotEmpty()) { "Lagu belum ditemukan." }
            tracks.map(CarLibrary::playable)
        }
        // Our app sends full metadata; external controllers can only resolve saved library IDs.
        if (controller.uid == android.os.Process.myUid() && mediaItems.all { it.localConfiguration != null }) return Futures.immediateFuture(mediaItems)
        return query { snapshot ->
            val tracks = CarLibrary.tracks(snapshot, device).associateBy { it.id }
            mediaItems.map { item -> CarLibrary.playable(requireNotNull(tracks[item.mediaId]) { "Lagu tidak ada di koleksi aktif." }) }
        }
    }
    override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return Futures.transform(onAddMediaItems(mediaSession, controller, mediaItems), { resolved ->
            if (controller.uid != android.os.Process.myUid()) newQueue()
            MediaSession.MediaItemsWithStartPosition(requireNotNull(resolved), startIndex, startPositionMs)
        }, MoreExecutors.directExecutor())
    }
}
