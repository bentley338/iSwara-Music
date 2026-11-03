package id.iswara.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.model.*

@Composable fun LibraryScreen(vm: MusicViewModel, onMenu: (Track) -> Unit, onCreate: () -> Unit, onScan: () -> Unit, onFile: () -> Unit, initialCategory: Int = 0) {
    val library = vm.library.collectAsStateWithLifecycle().value
    val downloads = vm.offline.downloads.collectAsStateWithLifecycle().value
    val local = vm.local.collectAsStateWithLifecycle().value
    val loading = vm.localLoading.collectAsStateWithLifecycle().value
    val player = vm.playback.state.collectAsStateWithLifecycle().value
    var category by rememberSaveable { mutableIntStateOf(initialCategory.coerceIn(0, 4)) }
    var lastCategory by rememberSaveable { mutableIntStateOf(initialCategory) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(false) }
    LaunchedEffect(initialCategory) { if (initialCategory != lastCategory) { category = initialCategory.coerceIn(0, 4); lastCategory = initialCategory; selectedId = null; query = "" } }
    val playlist = library.playlists.find { it.id == selectedId }
    BackHandler(playlist != null) { selectedId = null }
    val all = when { playlist != null -> playlist.tracks; category == 1 -> library.favorites; category == 2 -> library.history; category == 3 -> local; category == 4 -> downloads.map { it.track }; else -> emptyList() }
    val tracks = all.filter { query.isBlank() || "${it.title} ${it.artist}".contains(query, true) }.let { if (sort && playlist == null) it.sortedBy { t -> t.title.lowercase() } else it }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (playlist != null) item {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Kembali ke koleksi", "Back to library"), { selectedId = null }); Text(tr("Playlist pribadi", "Your playlist")) }
            PageHeading(playlist.title, "${playlist.tracks.size} ${tr("lagu", "tracks")}")
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.playback.play(playlist.tracks) }, enabled = playlist.tracks.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text(tr("Putar", "Play")) }
                OutlinedButton({ rename = playlist.title }) { Text(tr("Ubah nama", "Rename")) }
                ActionIcon(Icons.Rounded.DeleteOutline, tr("Hapus playlist", "Delete playlist"), { delete = true })
            }
        } else item {
            PageHeading(tr("Koleksimu", "Your library"), tr("Suara yang ingin kamu simpan.", "Sounds worth keeping.")) { ActionIcon(Icons.Rounded.Add, tr("Buat playlist", "Create playlist"), onCreate) }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Playlist", tr("Favorit", "Favorites"), tr("Riwayat", "History"), tr("Lokal", "Local"), tr("Unduhan", "Downloads")).forEachIndexed { index, label -> FilterChip(category == index, { category = index; query = "" }, { Text(label) }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text(tr("Cari di koleksi", "Search library")) }, modifier = Modifier.weight(1f), singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) })
                if (playlist == null) ActionIcon(Icons.Rounded.SortByAlpha, tr("Urutkan judul A–Z", "Sort title A–Z"), { sort = !sort }, active = sort)
            }
        }
        if (playlist == null && category == 0) {
            val playlists = library.playlists.filter { query.isBlank() || it.title.contains(query, true) }.let { if (sort) it.sortedBy(Playlist::title) else it }
            if (playlists.isEmpty()) item { EmptyState(tr("Ruang untuk musikmu", "Make room for your music"), tr("Buat playlist lalu tambahkan lagu lewat menu lagu.", "Create a playlist and add tracks from the track menu."), action = tr("Buat playlist", "Create playlist"), onAction = onCreate) }
            items(playlists, key = { it.id }) { p -> Row(Modifier.fillMaxWidth().clickable { selectedId = p.id; query = "" }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Art(p.tracks.firstOrNull()?.artwork.orEmpty(), p.title, Modifier.size(68.dp))
                Column(Modifier.weight(1f)) { Text(p.title, style = MaterialTheme.typography.titleMedium); Text("${p.tracks.size} ${tr("lagu", "tracks")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.Rounded.ChevronRight, null)
            } }
        } else {
            if (playlist == null && category == 3) item { FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onScan, enabled = !loading) { Text(tr("Pindai musik", "Scan music")) }
                OutlinedButton(onFile) { Text(tr("Pilih file", "Choose file")) }
            } }
            if (playlist == null && category == 2 && library.history.isNotEmpty()) item { TextButton({ clearHistory = true }, Modifier.padding(horizontal = 12.dp)) { Text(tr("Hapus riwayat dengar", "Clear listening history")) } }
            if (loading && category == 3) item { LoadingRows() }
            if (tracks.isEmpty() && !loading) item { EmptyState(if (query.isNotBlank()) tr("Tidak ada hasil", "No matches") else tr("Belum ada lagu", "No tracks yet"), when { playlist != null -> tr("Tambahkan lagu dari pencarian melalui menu lagu.", "Add tracks from search using the track menu."); category == 3 -> tr("Pindai perangkat atau pilih file audio untuk didengarkan offline.", "Scan your device or choose an audio file to listen offline."); category == 4 -> tr("Unduh lagu melalui menu lagu. Lagu selesai unduh muncul di sini.", "Download from a track menu. Completed downloads appear here."); category == 1 -> tr("Simpan lagu favorit lewat ikon hati di player.", "Save favorites with the heart in the player."); else -> tr("Lagu yang kamu putar akan muncul di sini.", "Tracks you play will appear here.") }) }
            items(tracks, key = { it.id }) { track ->
                TrackRow(track, { vm.playback.play(tracks, tracks.indexOf(track)) }, { onMenu(track) }, active = player.track?.id == track.id)
                if (playlist != null) Row(Modifier.fillMaxWidth().padding(start = 80.dp, end = 12.dp), horizontalArrangement = Arrangement.End) {
                    val index = playlist.tracks.indexOf(track)
                    ActionIcon(Icons.Rounded.KeyboardArrowUp, tr("Geser lagu ke atas", "Move track up"), { vm.action { vm.libraryRepository.updatePlaylist(playlist.copy(tracks = MusicRules.move(playlist.tracks, index, index - 1))) } }, enabled = index > 0)
                    ActionIcon(Icons.Rounded.KeyboardArrowDown, tr("Geser lagu ke bawah", "Move track down"), { vm.action { vm.libraryRepository.updatePlaylist(playlist.copy(tracks = MusicRules.move(playlist.tracks, index, index + 1))) } }, enabled = index < playlist.tracks.lastIndex)
                    ActionIcon(Icons.Rounded.RemoveCircleOutline, tr("Hapus lagu dari playlist", "Remove track from playlist"), { vm.action { vm.libraryRepository.updatePlaylist(playlist.copy(tracks = playlist.tracks.filterNot { it.id == track.id })) } })
                }
            }
        }
    }
    if (rename != null && playlist != null) TextPrompt(tr("Ubah nama playlist", "Rename playlist"), tr("Nama playlist", "Playlist name"), rename!!, { rename = it }, { rename = null }) { vm.action { vm.libraryRepository.rename(playlist, rename.orEmpty()); rename = null } }
    if (delete && playlist != null) ConfirmDialog(tr("Hapus playlist ini?", "Delete this playlist?"), playlist.title, { delete = false }) { vm.action { vm.libraryRepository.delete(playlist) }; delete = false; selectedId = null }
    if (clearHistory) ConfirmDialog(tr("Hapus riwayat dengar?", "Clear listening history?"), tr("Riwayat akun ini akan dihapus saat sinkronisasi berikutnya.", "This account's history will be deleted on the next sync."), { clearHistory = false }) { vm.action { vm.libraryRepository.clearHistory() }; clearHistory = false }
}

@Composable fun ConfirmDialog(title: String, message: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(confirm) { Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(dismiss) { Text(tr("Batal", "Cancel")) } })
}
