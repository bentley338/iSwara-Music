package id.iswara.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.cloud.*
import id.iswara.music.data.summarizeListening
import id.iswara.music.R
import id.iswara.music.model.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.testTag

@Composable fun HomeScreen(vm: MusicViewModel, onMenu: (Track) -> Unit, onSearch: () -> Unit, onAccount: () -> Unit, onPlayer: () -> Unit, onExplore: () -> Unit, onSpace: () -> Unit) {
    val content = vm.homeContent.collectAsStateWithLifecycle().value
    val saved = vm.savedQueues.collectAsStateWithLifecycle().value
    val cloudRows = content.announcements
    val featuredRows = content.collections
    val owner = vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val taste = vm.taste.collectAsStateWithLifecycle().value
    val shelves = vm.shelves.collectAsStateWithLifecycle().value.map { shelf ->
        if (taste.first == owner) shelf.copy(tracks = shelf.tracks.filterNot { taste.second.hidden(it) }.sortedBy { taste.second.less(it) }) else shelf
    }
    val library = vm.library.collectAsStateWithLifecycle().value
    val player = vm.screenPlayback.collectAsStateWithLifecycle().value
    val busy = vm.mixBusy.collectAsStateWithLifecycle().value
    var mood by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf(false) }
    var banks by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) }
    val english = LocalEnglish.current
    LazyColumn(Modifier.fillMaxSize().testTag("home-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key="home-header") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_logo), null, Modifier.size(40.dp))
                Text("iSwara Music", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                ActionIcon(Icons.Rounded.Insights, tr("Rekap dengar", "Listening recap"), { stats = true })
                ActionIcon(Icons.Rounded.Search, tr("Cari musik", "Search music"), onSearch)
                ActionIcon(Icons.Rounded.Settings, tr("Setelan", "Settings"), onAccount)
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text(tr("Mau dengar\napa hari ini?", "What sounds\ngood today?"), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(12.dp))
                Text(tr("Pilih suasana. Sisanya biar musik mengalir.", "Pick a mood. Let the music take it from there."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(tr("Untukmu", "For you"), "Indonesia", "Global").forEachIndexed { index, label -> FilterChip(filter == index, { filter = index }, { Text(label) }) }
                AssistChip({ vm.refreshHome() }, { Text(tr("Segarkan", "Refresh")) }, leadingIcon = { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)) })
                AssistChip({banks=true},{Text(tr("Antreanku","My queues"))},leadingIcon={Icon(Icons.Rounded.QueueMusic,null,Modifier.size(18.dp))})
                AssistChip({vm.smartQueueOpen.value=true},{Text(tr("Kurasi antrean","Queue curator"))},leadingIcon={Icon(Icons.Rounded.AutoAwesome,null,Modifier.size(18.dp))})
                AssistChip({vm.importOpen.value=true},{Text(tr("Impor playlist","Import playlists"))},leadingIcon={Icon(Icons.Rounded.PlaylistAdd,null,Modifier.size(18.dp))})
            }
        }
        item(key="home-shortcuts") { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onExplore, Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.Explore, null); Spacer(Modifier.width(8.dp)); Text(tr("Jelajah musik", "Explore music")) }
            OutlinedButton(onSpace, Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(8.dp)); Text(tr("Ruang kreatif", "Creative space")) }
        } }
        items(cloudRows, key = { "announcement:${it.text("id")}" }) { row ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.text("title"), style = MaterialTheme.typography.titleMedium)
                    Text(row.text("body"), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (saved.isNotEmpty()) {
            item(key="saved-queue-title") { SectionTitle(tr("Antrean tersimpan", "Saved queues")) }
            item(key="saved-queue-content") { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(saved.take(6), key = { it.id }) { queue ->
                    Surface(onClick = { vm.loadBank(queue.id) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(230.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Art(queue.snapshot.tracks.getOrNull(queue.snapshot.index)?.artwork.orEmpty(), queue.title, Modifier.size(50.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(queue.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${queue.snapshot.tracks.size} ${tr("lagu", "tracks")} \u00b7 ${timeLabel(queue.snapshot.position)}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            } }
        }
        if (featuredRows.isNotEmpty()) {
            item(key="featured-title") { SectionTitle(tr("Pilihan iSwara Music", "iSwara Music picks")) }
            item(key="featured-content") { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(featuredRows, key = { it.text("id") }) { row ->
                    Column(Modifier.width(170.dp).clickable { vm.shared(row.text("url")) }) {
                        Art(row.text("artwork"), row.text("title"), Modifier.size(170.dp))
                        Text(row.text("title"), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        Text(row.text("subtitle"), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } }
        }
        item(key="together-entry") { FilledTonalButton({vm.togetherOpen.value=true},Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp)) { Icon(Icons.Rounded.Groups,null);Spacer(Modifier.width(8.dp));Text(tr("Denger bareng","Listen together")) } }
        val resume = player.track ?: library.history.firstOrNull()
        if (resume != null) item(key="resume") {
            Surface(onClick = { if (player.track?.id == resume.id) onPlayer() else vm.playback.play(listOf(resume)) }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Art(resume.artwork, resume.title, Modifier.size(92.dp))
                    Column(Modifier.weight(1f)) { Text(tr("LANJUT DENGARKAN", "KEEP LISTENING"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)); Text(resume.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(resume.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                    ActionIcon(if (player.playing) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,tr("Putar atau jeda","Play or pause"),{if(player.track?.id==resume.id)vm.playback.toggle() else vm.playback.play(listOf(resume))},active=true)
                }
            }
        }
        if (filter == 0) item(key = "listening-charts") {
            HomeListeningCharts(vm, onMenu, onSearch)
        }
        item(key="mood-title") { SectionTitle(tr("Bawa suasanamu", "Find your mood")) }
        item(key="mood-content") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf("Santai" to "acoustic chill Indonesia", "Fokus" to "instrumental piano focus", "Galau" to "lagu galau Indonesia akustik", "Semangat" to "upbeat workout pop", "Malam" to "late night R&B", "Nostalgia" to "lagu Indonesia 2000an")) { (title, query) ->
                    OutlinedButton({ vm.moodMix(query) }, enabled = !busy, shape = RoundedCornerShape(16.dp)) { Text(if (english) query.substringBefore(" Indonesia") else title) }
                }
            }
            FilledTonalButton({ mood = "" }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), enabled = !busy) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(if (busy) tr("Menyusun mix...", "Building your mix...") else tr("Bikin mix versimu", "Make your own mix")) }
        }
        if (library.playlists.isNotEmpty()) {
            item(key="playlist-title") { SectionTitle(tr("Playlist pilihanmu", "Your playlists")) }
            item(key="playlist-content") { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(library.playlists.take(8), key = { it.id }) { p ->
                Surface(onClick = { vm.playback.play(p.tracks) }, enabled = p.tracks.isNotEmpty(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(240.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Art(p.tracks.firstOrNull()?.artwork.orEmpty(), p.title, Modifier.size(54.dp)); Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall); Text("${p.tracks.size} ${tr("lagu", "tracks")}", style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Rounded.PlayArrow, null) } }
            } } }
        }
        if (library.history.isNotEmpty()) { item(key="history-title") { SectionTitle(tr("Putar lagi", "Play it again")) }; item(key="history-content") { TrackShelf(library.history.take(10).distinctBy {it.id}, { vm.playback.play(listOf(it)) }, onMenu) } }
        shelves.filter { it.tracks.isNotEmpty() || it.loading || it.error != null }.filter {
            filter == 0 || (filter == 1 && it.query.contains("Indonesia")) ||
                (filter == 2 && !it.query.contains("Indonesia") && it.query.isNotEmpty())
        }.forEach { shelf ->
            item(key = "catalog:"+shelf.query.ifEmpty {"related"}) {
                SectionTitle(if (english) when (shelf.query) { "musik Indonesia populer" -> "Sounds of Indonesia"; "international indie pop songs" -> "Outside your rotation"; "lagu akustik Indonesia" -> "Acoustic afternoon"; "instrumental piano focus" -> "Stay focused"; "indie Indonesia songs" -> "Indie picks"; "upbeat pop workout songs" -> "Fresh energy"; else -> "Inspired by your listening" } else shelf.title)
                when {
                    shelf.tracks.isNotEmpty() -> TrackShelf(shelf.tracks.distinctBy {it.id}, { vm.playback.playFromDiscovery(it, shelf.query, shelf.tracks) }, onMenu)
                    shelf.loading -> HomeShelfLoading()
                    shelf.error != null -> EmptyState(tr("Belum tersambung", "Unable to connect"), tr("Musik belum bisa dimuat.", "Music could not be loaded."), Icons.Rounded.WifiOff, tr("Coba lagi", "Retry"), vm::refreshHome)
                }
            }
        }
        if (library.favorites.isNotEmpty()) { item(key="favorites-title") { SectionTitle(tr("Favoritmu", "Your favorites")) }; items(library.favorites.take(8).distinctBy {it.id}, key = { "favorite:${it.id}" }) { t -> TrackRow(t, { vm.playback.play(listOf(t)) }, { onMenu(t) }) } }
    }
    if (mood != null) TextPrompt(tr("Mix sesuai suasana", "A mix for your mood"), tr("Contoh: akustik galau saat hujan", "Try: acoustic songs for a rainy night"), mood!!, { mood = it }, { mood = null }) { vm.moodMix(mood.orEmpty()); mood = null }
    if (stats) ListeningRecap(vm) { stats = false }
    if (banks) SavedQueuesDialog(vm) { banks = false }
}

@Composable private fun HomeListeningCharts(vm: MusicViewModel, onMenu: (Track) -> Unit, onSearch: () -> Unit) {
    val owner = vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val listening = vm.listening.collectAsStateWithLifecycle().value
    var showArtists by remember(owner) { mutableStateOf(false) }
    var month by remember(owner) { mutableStateOf(false) }
    val today = java.time.LocalDate.now()
    val from = today.minusDays(if (month) 29 else 6).toString()
    val summary = remember(listening, owner, from, today) {
        val entries = listening.entries.takeIf { listening.owner == owner }.orEmpty()
        summarizeListening(entries.filter { it.date >= from && it.date <= today.toString() }, "")
    }
    SectionTitle(tr("Top dengaranmu", "Your listening charts"))
    Text(tr("Diurutkan berdasarkan waktu dengar di perangkat ini.", "Ranked by listening time on this device."),
        modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!showArtists, { showArtists = false }, { Text(tr("Top Lagu", "Top tracks")) })
        FilterChip(showArtists, { showArtists = true }, { Text(tr("Top Artis", "Top artists")) })
        FilterChip(!month, { month = false }, { Text(tr("7 hari", "7 days")) })
        FilterChip(month, { month = true }, { Text(tr("30 hari", "30 days")) })
    }
    if (summary.seconds == 0L || (showArtists && summary.artists.isEmpty())) {
        Text(tr("Belum ada data untuk periode ini.", "No listening data for this period."),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
    } else if (showArtists) {
        summary.artists.forEachIndexed { index, (artist, seconds) ->
            ListItem(
                headlineContent = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Text("${index + 1}", color = MaterialTheme.colorScheme.primary) },
                supportingContent = { Text(timeLabel(seconds * 1000)) },
                modifier = Modifier.clickable(onClickLabel = tr("Cari lagu artis", "Find artist tracks")) {
                    vm.discover(artist, SearchKind.SONGS)
                    onSearch()
                },
            )
        }
    } else {
        summary.tracks.forEachIndexed { index, (track, seconds) ->
            TrackRow(track, { vm.playback.play(listOf(track)) }, { onMenu(track) }, trailing = {
                Text("#${index + 1} · ${timeLabel(seconds * 1000)}", style = MaterialTheme.typography.labelMedium)
                ActionIcon(Icons.Rounded.MoreHoriz, tr("Opsi lagu", "Track options"), { onMenu(track) })
            })
        }
    }
}

@Composable fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp).semantics { heading() }) }

@Composable private fun HomeShelfLoading() {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=20.dp).semantics {contentDescription="Memuat musik"},horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        repeat(3){Column(Modifier.width(154.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(154.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Text("",minLines=2,style=MaterialTheme.typography.titleSmall,modifier=Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer))
            Spacer(Modifier.height(18.dp))
        }}
    }
}

@Composable fun TrackShelf(tracks: List<Track>, onPlay: (Track) -> Unit, onMenu: (Track) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(tracks, key = { it.id }) { track -> Column(Modifier.width(154.dp)) {
            Box {
                Column(Modifier.fillMaxWidth().clickable(onClickLabel = tr("Putar lagu", "Play track")) { onPlay(track) }) {
                    Art(track.artwork, track.title, Modifier.size(154.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(track.title, style = MaterialTheme.typography.titleSmall, minLines=2,maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onMenu(track) }, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(48.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MoreHoriz, tr("Opsi lagu", "Track options"), Modifier.size(20.dp)) }
                }
            }
        } }
    }
}

@Composable fun SearchScreen(vm: MusicViewModel, onMenu: (Track) -> Unit) {
    val search = vm.search.collectAsStateWithLifecycle().value
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val state = vm.playback.state.collectAsStateWithLifecycle().value
    var lyricSearch by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeading(tr("Cari musik", "Search music"), tr("Lagu, album, playlist, dan artis.", "Songs, albums, playlists, and artists.")) }
        item {
            OutlinedTextField(search.query, vm::query, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { contentDescription = "Pencarian musik" }, label = { Text(tr("Judul lagu atau nama artis", "Song title or artist")) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if (search.query.isNotEmpty()) ActionIcon(Icons.Rounded.Close, tr("Hapus pencarian", "Clear search"), { vm.query("") }) }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchKind.entries.forEach { kind -> val label = when (kind) { SearchKind.SONGS -> tr("Lagu", "Songs"); SearchKind.ALBUMS -> "Album"; SearchKind.PLAYLISTS -> "Playlist"; SearchKind.ARTISTS -> tr("Artis", "Artists") }
                    FilterChip(search.kind == kind, { vm.kind(kind) }, { Text(label) })
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({ vm.openRecognition() }, { Text(tr("Kenali lagu", "Identify song")) })
                AssistChip({ lyricSearch = true }, { Text(tr("Cari dari lirik", "Search lyrics")) })
            }
        }
        if (search.query.isBlank()) {
            item { SectionTitle(tr("Mulai dari suasana", "Start with a mood")) }
            item { Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Fokus" to "instrumental focus music", "Santai" to "acoustic chill Indonesia", "Perjalanan" to "road trip songs", "Energi baru" to "upbeat pop songs").forEach { (label, query) ->
                    OutlinedButton(onClick = { vm.query(query) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) { Text(if (LocalEnglish.current) query.substringBefore(" music").replaceFirstChar { it.uppercase() } else label); Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.Search, null) }
                }
            } }
            if (settings.searchHistory.isNotEmpty()) {
                item { SectionTitle(tr("Pencarian terakhir", "Recent searches")) }
                items(settings.searchHistory) { query -> MenuAction(Icons.Rounded.History, query) { vm.query(query) } }
                item { TextButton(onClick = { vm.action { vm.preferences.set("search", "[]") } }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(tr("Hapus riwayat pencarian", "Clear search history")) } }
            }
        } else {
            items(search.items, key = { it.id }) { item -> CatalogRow(item, onClick = { if (item.track != null) { val tracks = search.items.mapNotNull { it.track }; vm.playback.play(tracks, tracks.indexOf(item.track)) } else vm.open(item) }, onMenu = { item.track?.let(onMenu) }, active = item.track?.id == state.track?.id) }
            if (search.loading) item { LoadingRows() }
            if (search.error != null) item { EmptyState(tr("Pencarian belum berhasil", "Search failed"), search.error!!, Icons.Rounded.WifiOff, tr("Coba lagi", "Retry"), { vm.searchNow() }) }
            if (!search.loading && search.error == null && search.items.isEmpty()) item { EmptyState(tr("Tidak ada hasil", "No results"), tr("Coba kata lain atau ganti kategori.", "Try another query or category."), Icons.Rounded.SearchOff) }
            if (search.next != null && !search.loading) item { OutlinedButton(vm::moreSearch, Modifier.fillMaxWidth().padding(20.dp)) { Text(tr("Muat lebih banyak", "Load more")) } }
        }
    }
    if (lyricSearch) LyricPhraseDialog(vm) { lyricSearch = false }
}

@Composable fun CatalogRow(item: CatalogItem, onClick: () -> Unit, onMenu: () -> Unit, active: Boolean = false) {
    if (item.track != null) TrackRow(item.track, onClick, onMenu, active) else Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Art(item.artwork, item.title, Modifier.size(64.dp))
        Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        Icon(Icons.Rounded.ChevronRight, null)
    }
}

@Composable fun DetailScreen(vm: MusicViewModel, detail: DetailState, onMenu: (Track) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playing = vm.playback.state.collectAsStateWithLifecycle().value
    val tracks = detail.items.mapNotNull { it.track }
    var artworkOpen by remember(detail.url) { mutableStateOf(false) }
    val enlargeLabel = tr("Perbesar sampul", "Enlarge artwork")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Kembali", "Back"), vm::closeDetail); Text("iSwara Music", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) } }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Art(detail.artwork.ifBlank { tracks.firstOrNull()?.artwork.orEmpty() }, detail.title, Modifier.widthIn(max = 300.dp).fillMaxWidth(.78f).aspectRatio(1f).clickable(onClickLabel = enlargeLabel) { artworkOpen = true }.semantics { contentDescription = enlargeLabel })
                Spacer(Modifier.height(20.dp))
                Text(detail.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth().semantics { heading() })
                if (detail.subtitle.isNotBlank()) Text(detail.subtitle, Modifier.fillMaxWidth().padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tracks.isNotEmpty()) Text("${tracks.size} ${tr("lagu", "tracks")} · ${timeLabel(tracks.sumOf { it.duration } * 1000)}", Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tracks.isNotEmpty()) {
                        Button({ vm.playback.play(tracks) }) { Icon(Icons.Rounded.PlayArrow, null); Text(tr("Putar", "Play")) }
                        OutlinedButton({ vm.playShuffled(tracks) }) { Icon(Icons.Rounded.Shuffle, null); Text(tr("Acak", "Shuffle")) }
                        OutlinedButton({ vm.saveCollection() }) { Icon(Icons.Rounded.PlaylistAdd, null); Text(tr("Simpan", "Save")) }
                    }
                    IconButton({ context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, detail.url), null)) }) { Icon(Icons.Rounded.Share, tr("Bagikan koleksi", "Share collection")) }
                }
            }
        }
        items(detail.items, key = { it.id }) { item -> CatalogRow(item, { if (item.track != null) vm.playback.play(tracks, tracks.indexOf(item.track)) else vm.open(item) }, { item.track?.let(onMenu) }, item.track?.id == playing.track?.id) }
        if (detail.loading) item { LoadingRows() }
        if (detail.error != null) item { EmptyState(tr("Belum bisa dibuka", "Unable to open"), detail.error, action = tr("Coba lagi", "Retry"), onAction = { vm.loadDetail(false) }) }
        if (!detail.loading && detail.error == null && detail.items.isEmpty()) item { EmptyState(tr("Koleksi kosong", "Empty collection"), tr("Tidak ada lagu yang tersedia dari sumber ini.", "No tracks are available from this source.")) }
        if (detail.next != null && !detail.loading) item { OutlinedButton({ vm.loadDetail(true) }, Modifier.fillMaxWidth().padding(20.dp)) { Text(tr("Muat lebih banyak", "Load more")) } }
    }
    if (artworkOpen) androidx.compose.ui.window.Dialog(onDismissRequest = { artworkOpen = false }) {
        Surface(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Art(detail.artwork.ifBlank { tracks.firstOrNull()?.artwork.orEmpty() }, detail.title, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(detail.title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
            TextButton({ artworkOpen = false }) { Text(tr("Tutup sampul", "Close artwork")) }
        } }
    }
}
