package id.iswara.music.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.iswara.music.cloud.*
import id.iswara.music.data.*
import id.iswara.music.model.*
import id.iswara.music.playback.PlaybackConnection
import id.iswara.music.playback.ListeningTaste
import id.iswara.music.playback.freshShuffleOrder
import id.iswara.music.playback.recommendQueue
import id.iswara.music.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class Shelf(val title: String, val query: String, val tracks: List<Track> = emptyList(), val loading: Boolean = true, val error: String? = null)
data class SearchState(val query: String = "", val kind: SearchKind = SearchKind.SONGS, val items: List<CatalogItem> = emptyList(), val next: String? = null, val loading: Boolean = false, val error: String? = null)
data class MixPreview(val prompt: String = "", val tracks: List<Track> = emptyList(), val loading: Boolean = false, val error: String? = null, val owner: String = "")
internal data class TasteUndo(val owner: String, val key: String, val previous: Long?)
data class QueuePreview(val tracks:List<Track> = emptyList(),val loading:Boolean=false,val error:String?=null,val owner:String="",val seed:String="")
data class DetailState(val title: String, val url: String, val items: List<CatalogItem> = emptyList(), val next: String? = null, val loading: Boolean = true, val error: String? = null, val artwork: String = "", val subtitle: String = "", val kind: String = "playlist")

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val source: MusicSource,
    val resilient: ResilientMusicSource,
    val playback: PlaybackConnection,
    val libraryRepository: LibraryRepository,
    val preferences: Preferences,
    val updates:AppUpdates,
    val auth: AuthRepository,
    val sync: SyncCoordinator,
    private val localMusic: LocalMusic,
    private val lyricsRepository: LyricsRepository,
    val smartLyrics: SmartLyrics,
    val playlistImporter:PlaylistImporter,
    val offline: OfflineStore,
    val statistics: ListeningStats,
    val artwork: ArtworkTools,
    val queueBanks: SavedQueues,
    val admin: AdminRepository,
    val together: TogetherRepository,
    val radio: RadioRepository,
    val stems:id.iswara.music.data.StemStore,
    val lyricMedia:id.iswara.music.data.LyricMediaStore,
) : ViewModel() {
    val screenPlayback = playback.state.map {it.copy(position=0,duration=0)}.distinctUntilChanged()
        .stateIn(viewModelScope,SharingStarted.Eagerly,playback.state.value.copy(position=0,duration=0))
    val settings = preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val library = libraryRepository.library.stateIn(viewModelScope, SharingStarted.Eagerly, Library())
    val shelves = MutableStateFlow(listOf(Shelf("Suara dari Indonesia", "musik Indonesia populer"), Shelf("Di luar putaranmu", "international indie pop songs"), Shelf("Akustik sore", "lagu akustik Indonesia"), Shelf("Tenang & fokus", "instrumental piano focus"), Shelf("Indie pilihan", "indie Indonesia songs"), Shelf("Energi baru", "upbeat pop workout songs")))
    val adminAccess = MutableStateFlow(AdminAccess())
    val homeContent = MutableStateFlow(HomeContent())
    fun refreshAdmin() = viewModelScope.launch { adminAccess.value = if (id.iswara.music.BuildConfig.ADMIN_TOOLS_ENABLED) admin.access() else AdminAccess() }
    fun refreshContent() = viewModelScope.launch { try { homeContent.value = admin.home() } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
    val togetherOpen=MutableStateFlow(false)
    val togetherInvite=MutableStateFlow("")
    val radioInvite=MutableStateFlow("")
    val radioOpen=MutableStateFlow(false)
    suspend fun roomSearch(query:String)=source.search(query).items.mapNotNull {it.track}.filter(TogetherRules::valid).take(20)
    val mixBusy = MutableStateFlow(false)
    val mixPreview = MutableStateFlow(MixPreview())
    val mixPinned = MutableStateFlow<Set<String>>(emptySet())
    internal val tasteUndo = MutableStateFlow<TasteUndo?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val taste = libraryRepository.scope.flatMapLatest { owner ->
        preferences.taste(owner).map { owner to it }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "" to ListeningTaste())
    private var mixPreviewJob: Job? = null
    private var moodMixJob: Job? = null
    private var mixRotation = DiscoveryRotation()
    private var personalRotation = DiscoveryRotation()
    private val homeRotation = DiscoveryRotation()
    private val homePermits = Semaphore(3)
    private val homeRefresh = MutableStateFlow(0)
    val listening = statistics.scopedEntries.stateIn(viewModelScope, SharingStarted.Eagerly, OwnedListening())
    val savedQueues = queueBanks.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val search = MutableStateFlow(SearchState())
    val detail = MutableStateFlow<DetailState?>(null)
    val local = MutableStateFlow<List<Track>>(emptyList())
    val localLoading = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val authBusy = MutableStateFlow(false)
    val recovery = MutableStateFlow(false)
    val updatesOpen=MutableStateFlow(false)
    val importOpen=MutableStateFlow(false)
    val smartQueueOpen=MutableStateFlow(false)
    val queuePreview=MutableStateFlow(QueuePreview())
    private var queuePreviewJob:Job?=null
    fun previewQueue() {
        queuePreviewJob?.cancel();val track=playback.state.value.track ?: return;val owner=libraryRepository.scope.value
        queuePreview.value=QueuePreview(loading=true,owner=owner,seed=track.id)
        queuePreviewJob=viewModelScope.launch {try {
            val state = playback.state.value
            val queue = state.order.mapNotNull { state.queue.getOrNull(it) }.ifEmpty { state.queue }
            val tracks = withTimeout(25_000) {
                source.recommendQueue(track, queue, library.value, settings.value.radioMode, limit = 25,
                    taste = preferences.taste(owner).first())
            }
            val latestTaste = preferences.taste(owner).first()
            if (owner == libraryRepository.scope.value && playback.state.value.track?.id == track.id) {
                queuePreview.value = QueuePreview(tracks.filterNot { latestTaste.hidden(it) }.sortedBy { latestTaste.less(it) }, owner = owner, seed = track.id)
            } else if (queuePreview.value.owner == owner && queuePreview.value.seed == track.id) {
                queuePreview.value = QueuePreview()
            }
        }catch(_:TimeoutCancellationException){if(owner==libraryRepository.scope.value)queuePreview.value=QueuePreview(error="Pencarian rekomendasi terlalu lama. Coba lagi.",owner=owner,seed=track.id)}catch(e:CancellationException){throw e}catch(_:Exception){if(owner==libraryRepository.scope.value)queuePreview.value=QueuePreview(error="Rekomendasi belum tersedia. Coba lagi.",owner=owner,seed=track.id)}}
    }
    fun cancelQueuePreview(){queuePreviewJob?.cancel();queuePreview.update {it.copy(loading=false)}}
    @OptIn(ExperimentalCoroutinesApi::class)
    val lyricOffset = combine(libraryRepository.scope, playback.state.map { it.track?.id }.distinctUntilChanged()) { owner, id -> owner to id }
        .flatMapLatest { (owner,id) -> if (id == null) flowOf(0L) else preferences.raw("lyricsOffset:$owner:$id").map { (it?.toLongOrNull() ?: 0L).coerceIn(-15_000L,15_000L) }.onStart { emit(0L) } }
        .stateIn(viewModelScope,SharingStarted.Eagerly,0L)
    fun adjustLyricOffset(delta: Long, reset: Boolean = false) = action {
        val id = playback.state.value.track?.id ?: return@action
        preferences.adjustLyricsOffset(libraryRepository.scope.value,id,delta,reset)
    }
    val lyrics = MutableStateFlow<Lyrics?>(null)
    val lyricsLoading = MutableStateFlow(false)
    val lyricsError = MutableStateFlow<String?>(null)
    val lyricCandidates = MutableStateFlow<List<LyricCandidate>>(emptyList())
    val lyricSearchBusy = MutableStateFlow(false)
    val lyricSearchError = MutableStateFlow<String?>(null)
    private var lyricSearchJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var lyricJob: Job? = null
    private var homeJob: Job? = null
    init {
        viewModelScope.launch {
            libraryRepository.scope.drop(1).collect {
                cancelMix()
                moodMixJob?.cancel()
                mixRotation = DiscoveryRotation()
                personalRotation = DiscoveryRotation()
                mixPreview.value = MixPreview()
                mixPinned.value = emptySet()
                tasteUndo.value = null
                cancelQueuePreview()
                queuePreview.value = QueuePreview()
                shelves.update { all -> all.filter { it.query.isNotEmpty() } }
            }
        }
        viewModelScope.launch {together.messages.collect {messages.emit(it)}}
        loadHome(expand = false)
        observeHomeRecommendations()
        refreshContent()
        if (id.iswara.music.BuildConfig.ADMIN_TOOLS_ENABLED) viewModelScope.launch { auth.account.map { it?.id }.distinctUntilChanged().collectLatest { adminAccess.value = AdminAccess(); adminAccess.value = admin.access() } }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeHomeRecommendations() {
        viewModelScope.launch {
            libraryRepository.scope.flatMapLatest { owner ->
                combine(libraryRepository.observe(owner), homeRefresh) { collection, refresh ->
                    Triple(owner, collection, refresh)
                }
            }.distinctUntilChangedBy { (owner, collection, refresh) ->
                Triple(owner, collection.history.firstOrNull { !it.local }?.id, refresh)
            }.collectLatest { (owner, collection) ->
                val seed = collection.history.firstOrNull { !it.local }
                if (seed == null) {
                    shelves.update { all -> all.filter { it.query.isNotEmpty() } }
                    return@collectLatest
                }
                val related = source.recommendQueue(seed, emptyList(), collection, settings.value.radioMode, limit = 30,
                    taste = preferences.taste(owner).first())
                if (libraryRepository.scope.value != owner) return@collectLatest
                val catalog = shelves.value.filter { it.query.isNotEmpty() }
                val picks = personalRotation.select(related, 12, collection, blocked = catalog.flatMap { it.tracks })
                val title = "Karena kamu mendengarkan ${seed.artist.ifBlank { seed.title }}"
                shelves.value = if (picks.isEmpty()) catalog else listOf(Shelf(title, "", picks, false)) + catalog
            }
        }
    }
    fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.emit(e.message ?: "Terjadi kesalahan. Coba lagi.") }
    }
    fun refreshHome() {
        homeRefresh.update { it + 1 }
        loadHome(expand = true)
    }
    private fun loadHome(expand: Boolean) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            val catalog = shelves.value.filter { it.query.isNotEmpty() }
            shelves.update { all -> all.map { if (it.query.isEmpty()) it else it.copy(loading = true, error = null) } }
            catalog.forEach { shelf ->
                launch {
                    val candidates = homePermits.withPermit {
                        try {
                            withTimeoutOrNull(18_000) { source.discoveryCandidates(shelf.query, expand) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                    }
                    val current = shelves.value
                    val blocked = current.filter { it.query != shelf.query }.flatMap { it.tracks }
                    val refreshed = if (candidates.isNullOrEmpty()) {
                        shelf.copy(loading = false, error = if (candidates == null) "Musik belum bisa dimuat. Coba lagi." else null)
                    } else {
                        val picks = homeRotation.select(candidates, 20, Library(), blocked = blocked)
                        shelf.copy(tracks = picks, loading = false, error = null)
                    }
                    shelves.value = current.map { if (it.query == shelf.query) refreshed else it }
                }
            }
        }
    }
    fun query(value: String) { search.value = search.value.copy(query = value); searchNow(true) }
    fun discover(query: String, kind: SearchKind) { search.value = SearchState(query = query, kind = kind); searchNow() }
    fun previewMix(prompt: String, count: Int = 15, duration: String = "any", refinement: String = "", discovery: Boolean = false) {
        mixPreviewJob?.cancel()
        val text = prompt.trim().take(180)
        if (text.length < 3) { mixPreview.value = MixPreview(error = "Tulis suasana atau jenis musik yang kamu inginkan."); return }
        val owner = libraryRepository.scope.value
        val previous = mixPreview.value.takeIf { it.owner == owner && it.prompt == text }
        if (previous == null) mixPinned.value = emptySet()
        val keep = previous?.tracks.orEmpty().filter { it.id in mixPinned.value }.take(count.coerceIn(10, 20))
        mixPreview.value = MixPreview(text, previous?.tracks.orEmpty(), loading = true, owner = owner)
        mixPreviewJob = viewModelScope.launch {
            try {
                val tracks = withTimeout(25_000) {
                    selectMix(text, count.coerceIn(10, 20), duration, owner, keep, refinement, discovery)
                }
                check(owner == libraryRepository.scope.value) { "Akun berubah. Susun mix lagi." }
                mixPreview.value = MixPreview(text, tracks, error = if (tracks.isEmpty()) "Belum ada lagu yang cocok. Coba suasana atau durasi lain." else null, owner = owner)
            } catch (e: TimeoutCancellationException) { if (owner == libraryRepository.scope.value) mixPreview.value = MixPreview(text, previous?.tracks.orEmpty(), error = "Pencarian terlalu lama. Coba lagi.", owner = owner) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (owner == libraryRepository.scope.value) mixPreview.value = MixPreview(text, previous?.tracks.orEmpty(), error = "Mix belum bisa dimuat. Periksa koneksi lalu coba lagi.", owner = owner) }
        }
    }
    fun cancelMix() { mixPreviewJob?.cancel(); mixPreviewJob = null; mixPreview.update { it.copy(loading = false) } }
    fun pinMixTrack(id: String) { mixPinned.update { if (id in it) it - id else it + id } }
    fun removeMixTrack(id: String) {
        mixPinned.update { it - id }
        mixPreview.update { it.copy(tracks = it.tracks.filterNot { track -> track.id == id }) }
    }
    internal fun changeTaste(track: Track, hide: Boolean) = action {
        val owner = libraryRepository.scope.value
        val current = preferences.taste(owner).first()
        val key = if (hide) ListeningTaste.recordingKey(track) else ListeningTaste.artistKey(track)
        val previous = current.rules[key]
        val value = if (hide) {
            if (current.hidden(track)) null else System.currentTimeMillis() + 30L * 86_400_000
        } else if (current.less(track)) null else -1L
        preferences.editTaste(owner, key, value)
        if (owner == libraryRepository.scope.value) {
            tasteUndo.value = TasteUndo(owner, key, previous)
            if (hide && value != null) {
                playback.removeHiddenRecommendations(preferences.taste(owner).first())
                val hidden = mixPreview.value.tracks.filter { ListeningTaste.recordingKey(it) == key }.map { it.id }.toSet()
                mixPinned.update { it - hidden }
                mixPreview.update { it.copy(tracks = it.tracks.filterNot { item -> item.id in hidden }) }
                queuePreview.update { it.copy(tracks = it.tracks.filterNot { item -> ListeningTaste.recordingKey(item) == key }) }
            }
        }
    }
    internal fun undoTaste(undo: TasteUndo) = action {
        if (undo.owner == libraryRepository.scope.value && tasteUndo.value == undo) {
            preferences.editTaste(undo.owner, undo.key, undo.previous)
            tasteUndo.value = null
        }
    }
    internal suspend fun findLyricPhrase(query: String): List<LyricHit> {
        val collection = library.value
        return lyricsRepository.findPhrase(query, collection.history + collection.favorites +
            collection.playlists.flatMap { it.tracks } + local.value)
    }
    fun playShuffled(tracks: List<Track>) {
        playback.play(freshShuffleOrder(tracks, library.value.history).map { tracks[it] })
    }
    fun saveMix(title: String) = action {
        val result = mixPreview.value
        check(result.tracks.isNotEmpty() && !result.loading) { "Susun mix terlebih dahulu." }
        check(result.owner == libraryRepository.scope.value) { "Akun berubah. Susun mix lagi." }
        libraryRepository.createPlaylistWithTracks(title, result.tracks, result.owner)
        messages.emit("Mix disimpan ke koleksi")
    }
    fun kind(value: SearchKind) { search.value = search.value.copy(kind = value); searchNow(false) }
    fun searchNow(debounce: Boolean = false) {
        searchJob?.cancel()
        val current = search.value
        if (current.query.isBlank()) { search.value = current.copy(items = emptyList(), next = null, loading = false, error = null); return }
        searchJob = viewModelScope.launch {
            if (debounce) delay(400)
            search.value = current.copy(loading = true, items = emptyList(), next = null, error = null)
            try {
                val result = source.search(current.query.trim(), current.kind)
                search.value = current.copy(items = result.items, next = result.next)
                preferences.search(current.query.trim())
            } catch (e: CancellationException) { throw e } catch (e: Exception) { search.value = current.copy(error = "Pencarian gagal. ${e.message.orEmpty().take(140)}") }
        }
    }
    fun moreSearch() {
        if (search.value.loading) return
        val current = search.value; val next = current.next ?: return
        searchJob = viewModelScope.launch {
            search.value = current.copy(loading = true)
            try { val result = source.search(current.query, current.kind, next); search.value = current.copy(items = (current.items + result.items).distinctBy { it.id }, next = result.next) }
            catch (e: CancellationException) { throw e } catch (e: Exception) { search.value = current.copy(error = e.message) }
        }
    }
    fun open(item: CatalogItem) {
        item.track?.let { playback.play(listOf(it)); return }
        detail.value = DetailState(item.title, item.url, artwork = item.artwork, subtitle = item.subtitle, kind = item.kind)
        loadDetail(false)
    }
    fun loadDetail(more: Boolean) {
        detailJob?.cancel(); val current = detail.value ?: return
        detailJob = viewModelScope.launch {
            detail.value = current.copy(loading = true, error = null)
            try { val result = source.collection(current.url, if (more) current.next else null); detail.value = current.copy(items = (if (more) current.items + result.items else result.items).distinctBy { it.id }, next = result.next, loading = false) }
            catch (e: CancellationException) { throw e } catch (e: Exception) { detail.value = current.copy(loading = false, error = "Koleksi belum bisa dimuat. ${e.message.orEmpty().take(100)}") }
        }
    }
    fun closeDetail() { detailJob?.cancel(); detail.value = null }
    fun favorite(track: Track) = action { libraryRepository.favorite(track) }
    private suspend fun selectMix(
        text: String, count: Int, duration: String, owner: String, keep: List<Track> = emptyList(),
        refinement: String = "", discovery: Boolean = false,
    ): List<Track> {
        val total = Regex("""\b(\d{1,3})\s*(?:menit|minutes|min)\b""", RegexOption.IGNORE_CASE)
        val goal = total.find(text)?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(5, 120)?.times(60)
        val phrase = total.replace(text, "").trim().trimEnd(',', '.', ' ').ifBlank { text }
        val query = listOf(phrase, refinement, "songs").filter { it.isNotBlank() }.joinToString(" ")
        val collection = libraryRepository.snapshot(owner)
        val candidates = source.discoveryCandidates(query, expand = true)
        val preferences = preferences.taste(owner).first()
        check(libraryRepository.scope.value == owner) { "Akun berubah. Susun mix lagi." }
        val pinned = keep.filterNot { preferences.hidden(it) }
        val picks = mixRotation.select(candidates, count - pinned.size, collection, duration, pinned, preferences, discovery)
        if (goal == null) return pinned + picks
        var seconds = pinned.sumOf { it.duration }
        return pinned + picks.filter { track ->
            val include = seconds < goal && seconds + track.duration <= goal + 120
            if (include) seconds += track.duration
            include
        }
    }
    fun moodMix(prompt: String) {
        if (mixBusy.value) return
        val owner = libraryRepository.scope.value
        moodMixJob = action {
            val text = prompt.trim().take(180)
            require(text.length >= 3) { "Tulis suasana atau jenis musik yang kamu inginkan." }
            mixBusy.value = true
            try {
                val found = withTimeoutOrNull(25_000) { selectMix(text, 15, "any", owner) }
                    ?: error("Pencarian terlalu lama. Coba lagi.")
                check(found.isNotEmpty()) { "Belum ada lagu yang cocok. Coba suasana lain." }
                playback.play(found)
            } finally {
                mixBusy.value = false
            }
        }
    }
    fun saveQueue() = action { val state = playback.state.value; val tracks = state.order.ifEmpty { state.queue.indices.toList() }.mapNotNull { state.queue.getOrNull(it) }; check(tracks.isNotEmpty()); val p = libraryRepository.createPlaylist("Antrean ${java.time.LocalDate.now()}"); libraryRepository.updatePlaylist(p.copy(tracks = tracks.distinctBy { it.id })); messages.emit("Antrean disimpan sebagai playlist") }
    fun download(track: Track) = action { offline.enqueue(track, if (settings.value.dataSaver) "low" else settings.value.quality); messages.emit("${track.title} masuk antrean unduhan") }
    fun saveCollection() = action {
        val collection = detail.value ?: return@action
        val songs = collection.items.mapNotNull { it.track }.toMutableList()
        var next = collection.next
        val seen = mutableSetOf<String>()
        while (next != null && seen.add(next) && songs.size < 500) { val page = source.collection(collection.url, next); songs += page.items.mapNotNull { it.track }; next = page.next }
        val p = libraryRepository.createPlaylist(collection.title)
        libraryRepository.updatePlaylist(p.copy(tracks = songs.distinctBy { it.id }.take(500)))
        messages.emit("${songs.distinctBy { it.id }.size.coerceAtMost(500)} lagu disimpan")
    }
    fun createPlaylist(name: String) = action { libraryRepository.createPlaylist(name) }
    fun addTo(playlist: Playlist, track: Track) = action { libraryRepository.add(playlist, track); messages.emit("Ditambahkan ke ${playlist.title}") }
    fun scanLocal() = action { localLoading.value = true; try { local.value = localMusic.scan(); if (local.value.isEmpty()) messages.emit("Belum ada musik lokal di perangkat") } finally { localLoading.value = false } }
    fun openFile(uri: Uri) = action { val track = localMusic.file(uri); local.update { (it + track).distinctBy(Track::id) }; playback.play(listOf(track)) }
    fun playSearch(query: String) = action {
        if (query.isBlank()) { playback.resume(); return@action }
        val owner = libraryRepository.scope.value
        val tracks = withTimeout(15_000) { source.search(query.trim().take(200)).items.mapNotNull { it.track }.take(20) }
        check(owner == libraryRepository.scope.value) { "Akun berubah. Coba pencarian lagi." }
        check(tracks.isNotEmpty()) { "Lagu belum ditemukan. Coba judul atau artis lain." }
        playback.play(tracks)
    }
    internal val recognition = MutableStateFlow<id.iswara.music.source.RecognitionEntry?>(null)
    internal fun openRecognition(entry: id.iswara.music.source.RecognitionEntry = id.iswara.music.source.RecognitionEntry()) {
        id.iswara.music.source.PlaybackRecognitionService.reset()
        recognition.value = entry
    }
    fun shared(text: String): Job = action {
        Regex("iswara://(room|radio)/([A-Za-z0-9-]{12,16})(?=[?\\s]|$)").find(text)?.let {invite->
            val code=TogetherRules.code(invite.groupValues[2]);require(code.length==12){"Kode undangan tidak valid."}
            if(invite.groupValues[1]=="radio"){radioInvite.value=code;radioOpen.value=true}else{togetherInvite.value=code;togetherOpen.value=true}
            return@action
        }
        val nativeTrack = Regex("iswara://track/[A-Za-z0-9_-]{11}").find(text)?.value
        if (nativeTrack != null) { val id = Uri.parse(nativeTrack).lastPathSegment.orEmpty(); require(id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Tautan lagu tidak valid" }; playback.play(listOf(source.fromUrl("https://www.youtube.com/watch?v=$id"))); return@action }
        if (text.startsWith("iswara://playlist")) { val target = Uri.parse(text).getQueryParameter("url") ?: error("Tautan playlist tidak valid"); require(Uri.parse(target).scheme == "https" && Uri.parse(target).host in setOf("youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com", "youtu.be")) { "Tautan playlist tidak valid" }; shared(target); return@action }
        val url = Regex("https://(?:music\\.|www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)/[^\\s]+").find(text)?.value ?: error("Tautan musik tidak ditemukan")
        if (url.contains("list=") && !url.contains("v=")) { detail.value = DetailState("Playlist iSwara Music", url); loadDetail(false) } else playback.play(listOf(source.fromUrl(url)))
    }
    fun loadLyrics(force: Boolean = false) {
        lyricJob?.cancel(); val track = playback.state.value.track ?: return
        lyrics.value = null; lyricsError.value = null; lyricsLoading.value = true
        lyricJob = viewModelScope.launch {
            try { lyrics.value = lyricsRepository.get(track,force) } catch (e: CancellationException) { throw e } catch (e: Exception) { lyricsError.value = "Lirik belum bisa dimuat. Periksa koneksi." } finally { lyricsLoading.value = false }
        }
    }
    fun searchLyrics(query: String) {
        lyricSearchJob?.cancel(); lyricCandidates.value = emptyList(); lyricSearchError.value = null; lyricSearchBusy.value = true
        lyricSearchJob = viewModelScope.launch { try { lyricCandidates.value = lyricsRepository.search(query) } catch (e: CancellationException) { throw e } catch (_: Exception) { lyricSearchError.value = "Pencarian lirik belum tersambung." } finally { lyricSearchBusy.value = false } }
    }
    fun chooseLyrics(track: Track,candidate: LyricCandidate) = action {
        lyricJob?.cancel(); val result = lyricsRepository.choose(track,candidate)
        if (playback.state.value.track?.id == track.id) { lyrics.value = result; lyricsLoading.value = false; lyricsError.value = null }
        messages.emit("Lirik pilihan disimpan")
    }
    fun importLyrics(track: Track,uri: Uri,context: android.content.Context) = action {
        val owner = libraryRepository.scope.value
        val raw = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val result = StringBuilder(); val buffer = CharArray(8192)
            while (true) { val n = reader.read(buffer); if (n < 0) break; require(result.length + n <= 500_000) { "Berkas lirik terlalu besar." }; result.append(buffer,0,n) }; result.toString()
        } ?: error("Berkas tidak dapat dibaca") }
        check(owner == libraryRepository.scope.value) { "Akun berubah." }
        lyricJob?.cancel(); val result = lyricsRepository.import(track,raw,owner)
        if (playback.state.value.track?.id == track.id) { lyrics.value = result; lyricsLoading.value = false; lyricsError.value = null }
        messages.emit("Lirik LRC tersimpan di perangkat")
    }
    fun resetLyrics(track: Track) = action { lyricsRepository.reset(track); if (playback.state.value.track?.id == track.id) loadLyrics(true) }
    fun saveBank(title: String) = action { playback.saveBank(title); messages.emit("Antrean tersimpan") }
    fun loadBank(id: String) = action { playback.loadBank(id) }
    fun deleteBank(id: String) = action { playback.detachBank(id); queueBanks.remove(id) }
    fun login(email: String, password: String, register: Boolean) = action {
        authBusy.value = true
        try { if (register) { auth.register(email, password); messages.emit("Periksa email untuk mengaktifkan akun") } else { auth.login(email, password); messages.emit("Berhasil masuk") } } finally { authBusy.value = false }
    }
    fun reset(email: String) = action { auth.reset(email); messages.emit("Jika akun terdaftar, tautan reset dikirim ke email") }
    fun callback(uri: Uri) = action { recovery.value = auth.callback(uri); messages.emit("Email berhasil diverifikasi") }
    fun finishRecovery(password: String) = action { auth.updatePassword(password); recovery.value = false; messages.emit("Password diperbarui") }
    fun deleteAccount() = action {
        val owner = auth.account.value?.id ?: return@action
        auth.delete()
        lyricMedia.clearOwner(owner)
        stems.clearOwner(owner)
        offline.clearOwner(owner)
        statistics.clearOwner(owner, deleting = true)
        preferences.clearOwnerData(owner)
        queueBanks.clearOwner(owner)
        lyricsRepository.clearOwner(owner)
        libraryRepository.clearOwner(owner)
        libraryRepository.scope.value = "guest"
        messages.emit("Akun dan data cloud dihapus")
    }
}
