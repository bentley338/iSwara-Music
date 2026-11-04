@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package id.iswara.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionToken
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.data.LibraryRepository
import id.iswara.music.data.Preferences
import id.iswara.music.data.DeviceIdentity
import id.iswara.music.data.OfflineStore
import id.iswara.music.data.ListeningStats
import id.iswara.music.data.SavedQueues
import id.iswara.music.model.*
import id.iswara.music.source.MusicSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

fun Track.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(id)
    .setUri(Uri.Builder().scheme("iswara").authority(if (local) "local" else "stream").appendQueryParameter("url", url).appendQueryParameter("id", id).appendQueryParameter("device", device)
        .appendQueryParameter("title",title).appendQueryParameter("artist",artist).appendQueryParameter("duration",duration.toString()).build())
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork.takeIf { it.isNotBlank() }?.let(Uri::parse)).setExtras(Bundle().apply { putString("track", AppJson.encodeToString(this@mediaItem)) }).build()).build()
fun MediaItem.track(): Track? = mediaMetadata.extras?.getString("track")?.let { runCatching { AppJson.decodeFromString<Track>(it) }.getOrNull() }

data class PlaybackState(val track: Track? = null, val playing: Boolean = false, val loading: Boolean = false, val position: Long = 0, val duration: Long = 0, val queue: List<Track> = emptyList(), val index: Int = 0, val shuffle: Boolean = false, val repeat: Int = 0, val error: String? = null, val timerEnds: Long? = null, val canNext: Boolean = false, val radioLoading: Boolean = false, val radioError: String? = null, val radioIds: Set<String> = emptySet(), val errorDetails: String? = null, val sleepAtEnd: Boolean = false, val order: List<Int> = emptyList(), val undo: String? = null, val activeBank: String? = null, val pauseReason: String? = null, val roomRole: String? = null, val playRequested: Boolean = false)

@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackEngine @Inject constructor(@ApplicationContext private val context: Context, private val streams: StreamResolver, private val preferences: Preferences, private val library: LibraryRepository, private val source: MusicSource, private val offline: OfflineStore, val effects: AudioEffects, private val statistics: ListeningStats, val savedQueues: SavedQueues, val stems:id.iswara.music.data.StemStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var quality = "high"
    private var prefetch = true
    private var prefetchJob: Job? = null
    private var prefetchKey: String? = null
    private var retryJob: Job? = null
    private var recordedTrack: String? = null
    private var timer: Job? = null
    val timerEnds = MutableStateFlow<Long?>(null)
    private var retried: String? = null
    private var audioRetried: String? = null
    val sleepAtEnd = MutableStateFlow(false)
    var onPersonalQueueRequest:(()->Unit)?=null
    val roomRole = MutableStateFlow<String?>(null)
    private var roomBackup: Pair<String,QueueSnapshot>? = null
    val activeBank = MutableStateFlow<String?>(null)
    val undoLabel = MutableStateFlow<String?>(null)
    val pauseReason = MutableStateFlow<String?>(null)
    private data class Undo(val owner: String,val snapshot: QueueSnapshot,val playing: Boolean,val bank: String?)
    private var undo: Undo? = null
    private var undoJob: Job? = null
    private var saveJob: Job? = null
    private var restoring = false
    private var activeOwner = library.scope.value
    private var settings = id.iswara.music.data.Settings()
    private var radioJob: Job? = null
    private var radioContext: RadioContext? = null
    private var radioAdvanceFrom: String? = null
    private var generation = 0L
    private var radioStopped = false
    private var failedInRow = 0
    private val radioSeeds = mutableSetOf<String>()
    private val recent = ArrayDeque<String>()
    val radioLoading = MutableStateFlow(false)
    val radioError = MutableStateFlow<String?>(null)
    val radioIds = MutableStateFlow<Set<String>>(emptySet())
    private val volumeBalancer = VolumeBalancer()
    private val remixProcessor = RemixAudioProcessor()
    private val analogProcessor = AnalogAudioProcessor()
    private val warmCache = AudioWarmCache()
    private val audioHttpFactory: DataSource.Factory
    private val mediaFactory: DataSource.Factory
    private var excerptPlayer: ExoPlayer? = null
    private var shuffleJob: Job? = null
    val player: ExoPlayer
    val queueEditor by lazy { PlaybackQueue(player) }
    fun enqueue(track: Track, next: Boolean) { clearUndo(); queueEditor.enqueue(track, next, settings.allowDuplicates) }
    fun moveQueue(from: Int,to: Int) { clearUndo(); queueEditor.move(from,to) }
    fun detachBank(id: String) { if (activeBank.value == id) activeBank.value = null }
    val english get() = settings.language == "en"
    init {
        val client = okhttp3.OkHttpClient.Builder().connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS).readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
        val audioHttp = OkHttpDataSource.Factory(client).setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
        audioHttpFactory = DataSource.Factory { ChunkedAudioDataSource(audioHttp) }
        val http = DataSource.Factory { WarmAudioDataSource(warmCache, audioHttpFactory.createDataSource()) }
        val factory = DataSource.Factory {
            // Keep seeks on the same audio representation if settings change mid-track.
            val itemQuality = quality
            val base=OfflineDataSource(offline,DefaultDataSource.Factory(context,http).createDataSource())
            val variant=object:DataSource {
                var active:DataSource=base
                val listeners=mutableListOf<TransferListener>()
                override fun open(spec:DataSpec):Long {active=if(spec.uri.scheme=="iswara-stem")StemDataSource(stems).also {s->listeners.forEach(s::addTransferListener)}else base;return active.open(spec)}
                override fun read(buffer:ByteArray,offset:Int,length:Int)=active.read(buffer,offset,length)
                override fun getUri()=active.uri
                override fun getResponseHeaders()=active.responseHeaders
                override fun addTransferListener(listener:TransferListener){listeners+=listener;base.addTransferListener(listener)}
                override fun close()=active.close()
            }
            ResolvingDataSource(variant) { spec ->
            if (spec.uri.scheme != "iswara") spec else {
                val page = spec.uri.getQueryParameter("url") ?: throw java.io.IOException("Lagu tidak valid")
                val id = spec.uri.getQueryParameter("id")
                val stemMode=if(id!=null && roomRole.value==null)stems.mode(id)else "original"
                if(id!=null && stemMode!="original")return@ResolvingDataSource spec.withUri(Uri.Builder().scheme("iswara-stem").authority(stemMode).appendQueryParameter("id",id).appendQueryParameter("owner",stems.ownerTag()).build())
                if (id != null && offline.available(id)) return@ResolvingDataSource spec.withUri(Uri.parse("iswara-offline://$id"))
                if (spec.uri.host == "local") {
                    if (spec.uri.getQueryParameter("device") != DeviceIdentity.id(context)) throw java.io.IOException("File lokal berada di perangkat lain. Pilih file di perangkat ini.")
                    return@ResolvingDataSource spec.withUri(Uri.parse(page))
                }
                val request=Track(id ?: page,spec.uri.getQueryParameter("title").orEmpty(),spec.uri.getQueryParameter("artist").orEmpty(),duration=spec.uri.getQueryParameter("duration")?.toLongOrNull() ?: 0,url=page)
                val url = runBlocking { streams.resolve(request, itemQuality).url }
                spec.withUri(Uri.parse(url))
            }
            }
        }
        mediaFactory = factory
        val buffers = DefaultLoadControl.Builder().setBufferDurationsMs(30_000, 120_000, 750, 2500).setPrioritizeTimeOverSizeThresholds(true).build()
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setAudioProcessors(arrayOf(remixProcessor,analogProcessor,volumeBalancer)).build()
        }.setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(context, renderers).setLoadControl(buffers).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_IS_PLAYING_CHANGED))
                        MusicWidgets.render(context, player.currentMediaItem?.track(), player.isPlaying)
                    if (playbackState == Player.STATE_ENDED && sleepAtEnd.value) { pause(); sleep(0) }
                    if (!restoring && (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_IS_PLAYING_CHANGED) || events.contains(Player.EVENT_POSITION_DISCONTINUITY))) saveSoon()
                    if (playbackState == Player.STATE_READY) prepareNext()
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) maybeRadio()
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (sleepAtEnd.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) sleep(0)
                    retried = null
                    audioRetried = null
                    recordedTrack = null
                    retryJob?.cancel()
                    prefetchJob?.cancel(); prefetchKey = null
                    mediaItem?.track()?.let { recent.addLast(it.id); while (recent.size > 200) recent.removeFirst() }
                    setWakeMode(if (mediaItem?.track()?.local == true) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK)
                }
                override fun onAudioSessionIdChanged(audioSessionId: Int) { effects.attach(audioSessionId); effects.apply(settings); setAuxEffectInfo(AuxEffectInfo(effects.reverbId, if (settings.equalizer) 1f else 0f)) }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleepAtEnd.value) sleep(0)
                    if (!playWhenReady) pauseReason.value = when(reason) {
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "Audio dipakai aplikasi lain."
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "Headset terlepas."
                        else -> null
                    }
                }
                override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) { pauseReason.value = if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) "Menunggu keluaran audio." else null }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) failedInRow = 0
                    if (isPlaying) pauseReason.value = null
                    if (isPlaying) currentMediaItem?.track()?.takeIf { it.id != recordedTrack }?.let { track ->
                        recordedTrack = track.id
                        scope.launch { library.history(track) }
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    val failed=player.currentMediaItem?.track()
                    if(failed!=null && stems.mode(failed.id)!="original"){stems.fallback(failed.id);return}
                    val item = currentMediaItem ?: return
                    val recovery = PlaybackFailures.recovery(error)
                    val resume = playWhenReady
                    val position = currentPosition.coerceAtLeast(0)
                    if (recovery == Recovery.AUDIO && audioRetried != item.mediaId) {
                        audioRetried = item.mediaId
                        retryJob?.cancel()
                        retryJob = scope.launch {
                            delay(250)
                            if (currentMediaItem?.mediaId != item.mediaId || playWhenReady != resume) return@launch
                            setAuxEffectInfo(AuxEffectInfo(0, 0f)); effects.release()
                            stop(); seekTo(currentMediaItemIndex.coerceAtLeast(0), position); prepare(); playWhenReady = resume
                        }
                    } else if (recovery in listOf(Recovery.RETRY, Recovery.REFRESH_URL) && retried != item.mediaId && item.localConfiguration?.uri?.host == "stream") {
                        retried = item.mediaId
                        if (recovery == Recovery.REFRESH_URL) item.track()?.let { streams.invalidate(it) }; warmCache.clear()
                        retryJob?.cancel()
                        retryJob = scope.launch { delay(if (recovery == Recovery.RETRY) 1000 else 250); if (currentMediaItem?.mediaId == item.mediaId && playWhenReady == resume) { prepare(); playWhenReady = resume } }
                    } else if (recovery == Recovery.SKIP && settings.skipUnavailable && item.localConfiguration?.uri?.host == "stream" && playWhenReady && failedInRow < 3) {
                        failedInRow++
                        retryJob = scope.launch { delay(750); if (currentMediaItem?.mediaId != item.mediaId || !playWhenReady) return@launch; if (hasNextMediaItem()) { seekToNextMediaItem(); prepare() } else maybeRadio() }
                    }
                }
            })
        }
        scope.launch {
            var previous=stems.selection.value
            stems.selection.collect {selection ->
                if(selection!=previous){val track=player.currentMediaItem?.track();if(track!=null && (track.id==previous.track || track.id==selection.track))reloadStem()}
                previous=selection
            }
        }
        scope.launch { preferences.settings.distinctUntilChanged().collect {
            val newQuality = if (it.dataSaver) "low" else it.quality
            val newPrefetch = it.preload && !it.dataSaver
            val preparationChanged = quality != newQuality || prefetch != newPrefetch
            settings = it
            quality = newQuality
            prefetch = newPrefetch
            player.skipSilenceEnabled = it.skipSilence && roomRole.value==null
            player.playbackParameters=RemixRules.parameters(it,roomRole.value!=null)
            remixProcessor.hall=RemixRules.hall(it,roomRole.value!=null)
            analogProcessor.sound=AnalogSound(it.warmth/100f,it.vinyl/100f,it.spatial/100f)
            MusicWidgets.motion(context,it.widgetMotion)
            effects.apply(it)
            volumeBalancer.enabled = it.normalize
            player.setAuxEffectInfo(AuxEffectInfo(effects.reverbId, if (it.equalizer) 1f else 0f))
            if (!it.autoplay) { radioJob?.cancel(); radioLoading.value = false } else maybeRadio()
            if (preparationChanged) { prefetchJob?.cancel(); prefetchKey = null; warmCache.clear() }
            if (player.playbackState == Player.STATE_READY) prepareNext()
        } }
        scope.launch {
            library.scope.collectLatest { owner ->
                val old = activeOwner
                if (owner != old) {
                    val savedOld = persistenceSnapshot()
                    preferences.saveQueue(old, savedOld)
                    activeBank.value?.let { savedQueues.update(old,it,savedOld) }
                    activeBank.value = null
                    restoring = true; player.pause(); player.stop(); player.clearMediaItems(); restoring = false
                    sleep(0)
                    retryJob?.cancel(); prefetchJob?.cancel(); prefetchKey = null; streams.clear(); warmCache.clear()
                    beginQueue()
                }
                activeOwner = owner
                val saved = preferences.queue(owner).first()
                if (player.mediaItemCount == 0 && saved.tracks.isNotEmpty()) {
                    restoring = true
                    player.restoreQueue(saved)
                    restoring = false
                }
            }
        }
        scope.launch { while (isActive) { delay(10_000); if (player.isPlaying) persistQueue() } }
        scope.launch {
            var pendingTrack: Track? = null; var pendingOwner = activeOwner; var seconds = 0L
            while (isActive) {
                val track = player.currentMediaItem?.track(); val owner = activeOwner; val playing = player.isPlaying
                delay(1000)
                if (pendingTrack != null && (track?.id != pendingTrack.id || owner != pendingOwner || !player.isPlaying || seconds >= 10)) {
                    statistics.record(pendingOwner, pendingTrack, seconds); seconds = 0; pendingTrack = null
                }
                if (track != null && playing && player.isPlaying && player.currentMediaItem?.mediaId == track.id && owner == activeOwner) {
                    pendingTrack = track; pendingOwner = owner; seconds++
                }
            }
        }
    }
    private fun reloadStem() {
        val item=player.currentMediaItem ?: return
        val position=player.currentPosition;val play=player.playWhenReady;val index=player.currentMediaItemIndex
        val uri=item.localConfiguration?.uri ?: return
        player.replaceMediaItem(index,item.buildUpon().setUri(uri.buildUpon().appendQueryParameter("variantRevision",System.nanoTime().toString()).build()).build())
        player.seekTo(index,position);player.prepare();player.playWhenReady=play
    }
    fun enterTogether(role:String) {
        roomBackup=activeOwner to snapshot()
        beginQueue();stopRadioForQueue();roomRole.value=role;stems.original()
        player.shuffleModeEnabled=false;player.repeatMode=Player.REPEAT_MODE_OFF
        player.playbackParameters=PlaybackParameters.DEFAULT;player.skipSilenceEnabled=false;remixProcessor.hall=false
    }
    fun discardRoomUndo() { clearUndo() }
    fun exitTogether() {
        val saved=roomBackup;roomBackup=null;roomRole.value=null
        player.playbackParameters=RemixRules.parameters(settings,false);player.skipSilenceEnabled=settings.skipSilence;remixProcessor.hall=RemixRules.hall(settings,false)
        if(saved?.first==activeOwner) {player.pause();player.stop();player.restoreQueue(saved.second)}
    }
    private fun persistenceSnapshot()=roomBackup?.takeIf {it.first==activeOwner}?.second ?: snapshot()
    fun beginQueue() = beginQueue(null)
    internal fun beginQueue(context: RadioContext?) {
        shuffleJob?.cancel()
        val bank = activeBank.value; val owner = activeOwner; val saved = snapshot()
        if (bank != null) scope.launch { savedQueues.update(owner,bank,saved) }
        activeBank.value = null; clearUndo()
        radioContext = context
        radioAdvanceFrom = null
        if (sleepAtEnd.value) sleep(0); generation++; radioStopped = false; failedInRow = 0; radioJob?.cancel(); radioSeeds.clear(); recent.clear(); radioIds.value = emptySet(); radioError.value = null; radioLoading.value = false
        excerptPlayer?.stop()
    }
    private fun clearUndo() { undoJob?.cancel(); undo = null; undoLabel.value = null }
    fun editWithUndo(label: String, block: () -> Unit) {
        val saved = Undo(activeOwner,snapshot(),player.playWhenReady,activeBank.value)
        block(); clearUndo()
        if (saved.snapshot.tracks.isEmpty()) return
        undo = saved; undoLabel.value = label
        undoJob = scope.launch { delay(30_000); undo = null; undoLabel.value = null }
    }
    fun undoEdit() {
        val saved = undo?.takeIf { it.owner == activeOwner } ?: return
        val current = player.currentMediaItem?.mediaId
        val index = saved.snapshot.tracks.indices.firstOrNull { it >= saved.snapshot.index && saved.snapshot.tracks[it].id == current }
        val restore = if (index != null) saved.snapshot.copy(index = index,position = player.currentPosition.coerceAtLeast(0)) else saved.snapshot
        val play = if (current != null) player.playWhenReady else saved.playing
        clearUndo(); activeBank.value = saved.bank
        player.restoreQueue(restore); player.prepare(); player.playWhenReady = play
    }
    suspend fun saveBank(title: String) { val owner = activeOwner; val saved = savedQueues.create(title,snapshot(),owner); check(owner == activeOwner && owner == library.scope.value) { "Akun berubah." }; if(roomRole.value==null)activeBank.value = saved.id }
    suspend fun loadBank(id: String) {
        val owner = activeOwner
        val oldBank = activeBank.value
        if (oldBank != null) savedQueues.update(owner,oldBank,snapshot())
        val saved = savedQueues.get(owner,id) ?: error("Antrean tidak ditemukan.")
        check(owner == activeOwner && owner == library.scope.value) { "Akun berubah." }
        beginQueue(); activeBank.value = id; player.restoreQueue(saved.snapshot); player.prepare(); player.play()
    }
    fun stopRadioForQueue() { radioStopped = true; generation++; radioJob?.cancel(); radioAdvanceFrom = null; radioLoading.value = false; radioError.value = null }
    fun nextFromRadio() {
        radioAdvanceFrom = player.currentMediaItem?.mediaId
        maybeRadio(force = true)
        if (radioJob?.isActive != true) radioAdvanceFrom = null
    }
    fun maybeRadio(force: Boolean = false) {
        if(roomRole.value!=null)return
        if (force) radioStopped = false
        if (radioStopped) return
        val seed = player.currentMediaItem?.track()?.takeUnless { it.local } ?: return
        if (!settings.autoplay || player.repeatMode != Player.REPEAT_MODE_OFF || !player.playWhenReady || radioJob?.isActive == true) return
        if (!force) {
            val order = playbackOrder(player)
            if (order.size - order.indexOf(player.currentMediaItemIndex) > 3 || seed.id in radioSeeds) return
        }
        val token = generation
        val owner = activeOwner
        val context = radioContext
        radioLoading.value = true; radioError.value = null
        radioJob = scope.launch {
            try {
                if (!force) delay(1500)
                val queue = playbackOrder(player).mapNotNull { player.getMediaItemAt(it).track() }
                val collection = library.snapshot(owner)
                val taste = preferences.taste(owner).first()
                val topArtists = try {
                    statistics.topArtistKeys(owner)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emptySet()
                }
                if (token != generation || owner != activeOwner) return@launch
                val recommendations = source.recommendQueue(
                    seed, queue, collection, settings.radioMode, recent.toList(),
                    limit = if (context == null) 15 else 18, context = context, topArtists = topArtists, taste = taste,
                )
                if (token != generation || owner != activeOwner || owner != library.scope.value || !settings.autoplay ||
                    radioStopped || player.repeatMode != Player.REPEAT_MODE_OFF) return@launch
                val currentTaste = preferences.taste(owner).first()
                if (token != generation || owner != activeOwner || owner != library.scope.value || !settings.autoplay ||
                    radioStopped || player.repeatMode != Player.REPEAT_MODE_OFF) return@launch
                val latestRecommendations = recommendations.filterNot { currentTaste.hidden(it) }
                val finalQueue = playbackOrder(player).mapNotNull { player.getMediaItemAt(it).track() }
                val additions = if (finalQueue == queue) latestRecommendations else QueueRules.smart(
                    seed, finalQueue, latestRecommendations, recent.toList() + collection.history.take(8).map { it.id },
                    collection.favorites, collection.history, settings.radioMode,
                    limit = latestRecommendations.size, topArtists = topArtists, tasteRules = currentTaste.rules,
                )
                radioSeeds += seed.id
                if (additions.isEmpty()) { radioError.value = "Belum ada rekomendasi baru. Coba radio lagi."; return@launch }
                val ended = player.playbackState == Player.STATE_ENDED || player.playerError != null
                val first = player.mediaItemCount
                radioIds.value += additions.map { it.id }
                clearUndo(); queueEditor.appendRadio(additions)
                if (player.playWhenReady) {
                    if (ended) { player.seekTo(first, 0); player.prepare() }
                    else if (radioAdvanceFrom == player.currentMediaItem?.mediaId) player.seekToNextMediaItem()
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { if (token == generation) radioError.value = "Radio belum tersambung. Antreanmu tetap tersimpan." }
            finally { if (token == generation) { radioAdvanceFrom = null; radioLoading.value = false } }
        }
    }
    internal fun removeHiddenRecommendations(taste: ListeningTaste) {
        if (roomRole.value != null) return
        val order = playbackOrder(player)
        val after = order.indexOf(player.currentMediaItemIndex)
        val counts = order.groupingBy { player.getMediaItemAt(it).mediaId }.eachCount()
        order.drop(after + 1).filter { index ->
            val item = player.getMediaItemAt(index)
            item.mediaId in radioIds.value && counts[item.mediaId] == 1 && item.track()?.let { taste.hidden(it) } == true
        }.sortedDescending().forEach(player::removeMediaItem)
    }

    internal fun toggleFreshShuffle() {
        if (shuffleJob?.isActive == true) {
            shuffleJob?.cancel()
            return
        }
        if (player.shuffleModeEnabled) {
            player.shuffleModeEnabled = false
            return
        }
        val owner = activeOwner
        val token = generation
        shuffleJob = scope.launch {
            val history = try {
                library.snapshot(owner).history
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (owner != activeOwner || owner != library.scope.value || token != generation || roomRole.value != null) return@launch
            val current = player.currentMediaItemIndex
            val upcoming = (0 until player.mediaItemCount).filter { it != current }
            val tracks = upcoming.map { player.getMediaItemAt(it).track() }
            if (tracks.all { it != null }) {
                val artist = QueueRules.artistKey(player.currentMediaItem?.track()?.artist.orEmpty())
                val order = listOf(current).filter { it in 0 until player.mediaItemCount } +
                    freshShuffleOrder(tracks.filterNotNull(), history, artist).map { upcoming[it] }
                player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
            }
            player.shuffleModeEnabled = true
        }
    }

    internal suspend fun previewExcerpt(track: Track, onReady: () -> Unit) {
        check(roomRole.value == null) { "Keluar dari room untuk mencoba cuplikan." }
        check(excerptPlayer == null) { "Cuplikan masih berjalan." }
        val resume = player.playWhenReady
        val token = generation
        val current = player.currentMediaItem
        val position = player.currentPosition
        val preview = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(mediaFactory)).build()
        val done = CompletableDeferred<Unit>()
        val mainListener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) done.complete(Unit)
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                done.complete(Unit)
            }
        }
        excerptPlayer = preview
        player.pause()
        player.addListener(mainListener)
        try {
            preview.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            preview.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> onReady()
                        Player.STATE_ENDED, Player.STATE_IDLE -> done.complete(Unit)
                    }
                }
                override fun onPlayerError(error: PlaybackException) { done.completeExceptionally(error) }
            })
            preview.setMediaItem(track.mediaItem().buildUpon()
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(15_000).build()).build())
            preview.prepare()
            preview.play()
            withTimeout(28_000) { done.await() }
        } finally {
            player.removeListener(mainListener)
            if (excerptPlayer === preview) {
                excerptPlayer = null
                preview.release()
                if (resume && token == generation && current == player.currentMediaItem &&
                    kotlin.math.abs(player.currentPosition - position) < 1000 && roomRole.value == null) player.play()
            }
        }
    }
    private fun prepareNext() {
        if (!prefetch || !player.playWhenReady || !player.hasNextMediaItem()) return
        val next = player.getMediaItemAt(player.nextMediaItemIndex).track()?.takeUnless { it.local || offline.available(it.id) } ?: return
        val key = "${next.url}:$quality"
        if (prefetchKey == key) return
        prefetchKey = key
        prefetchJob?.cancel()
        val selectedQuality = quality
        prefetchJob = scope.launch {
            delay(2000)
            try {
                val audio = streams.resolve(next, selectedQuality)
                warmCache.preload(Uri.parse(audio.url), audioHttpFactory)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Optional preparation must not interrupt the current song. */ }
        }
    }
    private fun snapshot() = player.snapshotQueue()
    private suspend fun persistQueue() {
        val owner = activeOwner; val saved = persistenceSnapshot(); val bank = activeBank.value
        preferences.saveQueue(owner,saved)
        if (bank != null) savedQueues.update(owner,bank,saved)
    }
    private fun saveSoon() { saveJob?.cancel(); saveJob = scope.launch { delay(300); persistQueue() } }
    fun sleep(minutes: Int) {
        sleepAtEnd.value = false; player.pauseAtEndOfMediaItems = false
        timer?.cancel(); timerEnds.value = null
        if (minutes > 0) { timerEnds.value = System.currentTimeMillis() + minutes * 60_000L; timer = scope.launch { delay(minutes * 60_000L); player.pause(); timerEnds.value = null } }
    }
    fun sleepAfterTrack() { sleep(0); if (player.currentMediaItem != null) { sleepAtEnd.value = true; player.pauseAtEndOfMediaItems = true } }
    fun invalidateStream() { if (player.playerError?.let(PlaybackFailures::recovery) == Recovery.REFRESH_URL) player.currentMediaItem?.track()?.let { streams.invalidate(it) }; retried = null; audioRetried = null }
    fun reloadSource(track:Track){streams.invalidate(track);warmCache.clear();prefetchJob?.cancel();prefetchKey=null;retried=null;audioRetried=null;retryJob?.cancel()}
    fun release() { val owner = activeOwner; val saved = persistenceSnapshot(); val bank = activeBank.value; timer?.cancel(); retryJob?.cancel(); radioJob?.cancel(); prefetchJob?.cancel(); undoJob?.cancel(); saveJob?.cancel(); shuffleJob?.cancel(); excerptPlayer?.let { excerptPlayer = null; it.stop(); it.release() }; warmCache.clear(); effects.release(); scope.launch { try { preferences.saveQueue(owner, saved); if (bank != null) savedQueues.update(owner,bank,saved) } finally { scope.cancel() } }; player.release() }
}

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject lateinit var engine: PlaybackEngine
    @Inject lateinit var floatingLyrics: FloatingLyrics
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var source: MusicSource
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaLibrarySession? = null
    override fun onCreate() {
        super.onCreate()
        val callback = CarLibraryCallback(library, DeviceIdentity.id(this), { engine.english }, serviceScope, source, {engine.onPersonalQueueRequest?.invoke();engine.beginQueue()},{engine.roomRole.value=="member"})
        session = MediaLibrarySession.Builder(this, engine.player, callback).setId("id.iswara.music.playback").build()
        floatingLyrics.start(engine.player)
        serviceScope.launch {
            var previousPlaylists = emptySet<String>()
            library.library.debounce(300).collect { current ->
            session?.let { s ->
                s.notifyChildrenChanged(CarLibrary.ROOT, 3, null)
                s.notifyChildrenChanged(CarLibrary.PLAYLISTS, current.playlists.size, null)
                s.notifyChildrenChanged(CarLibrary.FAVORITES, current.favorites.size, null)
                s.notifyChildrenChanged(CarLibrary.HISTORY, current.history.size, null)
                val currentIds = current.playlists.map { it.id }.toSet()
                (previousPlaylists + currentIds).forEach { id -> s.notifyChildrenChanged(CarLibrary.playlistId(id), current.playlists.find { it.id == id }?.tracks?.size ?: 0, null) }
                previousPlaylists = currentIds
            }
        } }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        if (controllerInfo.isTrusted || controllerInfo.uid == android.os.Process.myUid()) session else null
    override fun onDestroy() { serviceScope.cancel(); floatingLyrics.stop(); session?.release(); session = null; super.onDestroy() }
}

@Singleton
class PlaybackConnection @Inject constructor(@ApplicationContext private val context: Context, private val engine: PlaybackEngine) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val state = MutableStateFlow(PlaybackState())
    val effects get() = engine.effects
    private var controller: MediaController? = null
    private var queue = emptyList<Track>()
    private var currentTrack: Track? = null
    private var order = emptyList<Int>()
    var onRoomPersonalPlay: (() -> Unit)?
        get()=engine.onPersonalQueueRequest
        set(value) {engine.onPersonalQueueRequest=value}
    var onRoomEnqueue: ((Track,Boolean) -> Unit)?=null
    var onRoomToggle: (() -> Unit)?=null
    val roomControl=MutableStateFlow(0)
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
    init {
        future.addListener({
            runCatching {
                controller = future.get().also { c -> c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) updateQueue()
                        update()
                    }
                }) }
                updateQueue()
                while (pending.isNotEmpty()) pending.removeFirst()(controller!!)
                update()
            }.onFailure { state.value = state.value.copy(error = "Pemutar gagal dimulai: ${it.message}") }
        }, ContextCompat.getMainExecutor(context))
        scope.launch { while (isActive) { update(); delay(500) } }
    }
    private fun updateQueue() {
        val c = controller ?: return
        queue = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).track() }
        currentTrack = c.currentMediaItem?.track()
        order = playbackOrder(c)
    }
    private fun update() {
        val c = controller ?: return
        state.value = PlaybackState(currentTrack, c.isPlaying, c.playbackState == Player.STATE_BUFFERING, c.currentPosition.coerceAtLeast(0), c.duration.takeIf { it > 0 } ?: ((currentTrack?.duration ?: 0) * 1000), queue, c.currentMediaItemIndex.coerceAtLeast(0), c.shuffleModeEnabled, c.repeatMode, c.playerError?.let(PlaybackFailures::message), engine.timerEnds.value, c.hasNextMediaItem(), engine.radioLoading.value, engine.radioError.value, engine.radioIds.value, c.playerError?.let(PlaybackFailures::report), engine.sleepAtEnd.value, order, engine.undoLabel.value, engine.activeBank.value, engine.pauseReason.value, engine.roomRole.value,c.playWhenReady)
    }
    private fun command(action: (MediaController) -> Unit) { controller?.let(action) ?: pending.addLast(action) }
    fun play(tracks: List<Track>, index: Int = 0) = playQueue(tracks, index, null)
    fun playFromDiscovery(track: Track, query: String, candidates: List<Track>) {
        playQueue(listOf(track), 0, query.takeIf { it.isNotBlank() }?.let { RadioContext(it, candidates.take(40)) })
    }
    internal fun playAt(track: Track, position: Long) = playQueue(listOf(track), 0, null, position)
    private fun playQueue(tracks: List<Track>, index: Int, context: RadioContext?, position: Long = 0) {
        if (tracks.isEmpty()) return
        onRoomPersonalPlay?.invoke()
        command {
            engine.beginQueue(context)
            it.setMediaItems(tracks.map(Track::mediaItem), index.coerceIn(tracks.indices), position.coerceAtLeast(0))
            it.prepare()
            it.play()
        }
    }
    fun toggle() { if(engine.roomRole.value=="member") {onRoomToggle?.invoke();return};roomControl.value++;command { if ((it.isPlaying || (it.playWhenReady && it.playbackState == Player.STATE_BUFFERING)) && it.playerError == null) it.pause() else { if (it.playerError != null) engine.invalidateStream(); if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun resume() = command { if (it.mediaItemCount > 0) { if (it.playerError != null) engine.invalidateStream(); if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } }
    fun reloadSource(id:String)=command {player->
        val track=player.currentMediaItem?.track() ?: return@command
        if(track.id!=id)return@command
        val position=player.currentPosition.coerceAtLeast(0);val index=player.currentMediaItemIndex;val play=player.playWhenReady
        engine.reloadSource(track);player.stop();player.seekTo(index,position);player.prepare();player.playWhenReady=play
    }
    fun next() = command { if(engine.roomRole.value=="member")return@command;roomControl.value++; if (it.hasNextMediaItem()) { it.seekToNextMediaItem(); if (it.playbackState == Player.STATE_IDLE) it.prepare() } else engine.nextFromRadio() }
    fun previous() = command { if(engine.roomRole.value=="member")return@command;roomControl.value++; if (it.currentPosition > 3000) it.seekTo(0) else if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    fun seek(position: Long) = command { if(engine.roomRole.value=="member")return@command;roomControl.value++; it.seekTo(position) }
    fun shuffle() = command { if(engine.roomRole.value!=null)return@command;roomControl.value++; engine.toggleFreshShuffle() }
    internal fun removeHiddenRecommendations(taste: ListeningTaste) = engine.removeHiddenRecommendations(taste)
    internal suspend fun previewExcerpt(track: Track, onReady: () -> Unit) = engine.previewExcerpt(track, onReady)
    fun repeat() = command { if(engine.roomRole.value!=null)return@command;roomControl.value++; it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }
    fun enqueue(track: Track, next: Boolean) { onRoomEnqueue?.let {it(track,next);return};command { engine.enqueue(track, next) } }
    fun move(from: Int, to: Int) = command { if(engine.roomRole.value=="member")return@command;roomControl.value++; engine.moveQueue(from, to) }
    fun remove(index: Int) = command { if(engine.roomRole.value=="member")return@command;roomControl.value++; if (index in 0 until engine.player.mediaItemCount) engine.editWithUndo("Lagu dihapus") { engine.player.removeMediaItem(index) } }
    fun clear() = command { if(engine.roomRole.value=="member")return@command;roomControl.value++; engine.editWithUndo("Antrean dikosongkan") { engine.beginQueue(); engine.player.pause(); engine.player.stop(); engine.player.clearMediaItems() } }
    fun clearUpcoming() { if(engine.roomRole.value=="member")return;roomControl.value++;engine.stopRadioForQueue(); command { engine.editWithUndo("Lagu berikutnya dihapus") { engine.queueEditor.clearUpcoming() } } }
    fun clearPlayed() = command { if(engine.roomRole.value=="member")return@command;roomControl.value++; engine.editWithUndo("Riwayat antrean dihapus") { engine.queueEditor.clearPlayed() } }
    fun undo() = command { if(engine.roomRole.value=="member")return@command;roomControl.value++; engine.undoEdit() }
    suspend fun saveBank(title: String) = engine.saveBank(title)
    suspend fun loadBank(id: String) {onRoomPersonalPlay?.invoke();engine.loadBank(id)}
    fun detachBank(id: String) = engine.detachBank(id)
    fun enterTogether(role:String) {engine.enterTogether(role)}
    fun exitTogether() {engine.exitTogether()}
    fun applyTogether(saved:QueueSnapshot,playing:Boolean,replace:Boolean) = command { _ ->
        val c=engine.player
        if(replace)engine.discardRoomUndo()
        val existing=(0 until c.mediaItemCount).map {c.getMediaItemAt(it).mediaId}
        val incoming=saved.tracks.map {it.id}
        if(incoming.isEmpty()) {c.pause();c.stop();c.clearMediaItems();return@command}
        if(existing!=incoming) {
            if(incoming.size>existing.size && incoming.take(existing.size)==existing) c.addMediaItems(saved.tracks.drop(existing.size).map(Track::mediaItem))
            else c.setMediaItems(saved.tracks.map(Track::mediaItem),saved.index.coerceIn(0,saved.tracks.lastIndex.coerceAtLeast(0)),saved.position)
        }
        if(c.mediaItemCount>0) {
            if(c.currentMediaItemIndex!=saved.index || kotlin.math.abs(c.currentPosition-saved.position)>1800) c.seekTo(saved.index,saved.position)
            if(c.playbackState==Player.STATE_IDLE)c.prepare()
            c.playWhenReady=playing
        } else c.pause()
    }
    fun radio() = engine.maybeRadio(true)
    fun jump(index: Int) = command { if(engine.roomRole.value=="member")return@command;roomControl.value++; if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.prepare(); it.play() } }
    fun sleep(minutes: Int) = engine.sleep(minutes)
    fun sleepAfterTrack() = engine.sleepAfterTrack()
    val audioSessionId get() = engine.player.audioSessionId
}
