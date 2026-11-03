package id.iswara.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.data.DownloadedTrack
import id.iswara.music.model.Track

object OfflineRules {
    fun filter(entries: List<DownloadedTrack>, query: String, sort: String): List<DownloadedTrack> {
        val found = entries.filter { query.isBlank() || "${it.track.title} ${it.track.artist}".contains(query.trim(), true) }
        return when (sort) { "title" -> found.sortedBy { it.track.title.lowercase() }; "size" -> found.sortedByDescending { it.bytes }; else -> found.asReversed() }
    }
    fun megabytes(bytes: Long): String = String.format(java.util.Locale.ROOT, "%.1f MB", bytes.coerceAtLeast(0) / 1_000_000.0)
}

@Composable fun OfflineScreen(vm: MusicViewModel, back: () -> Unit, local: () -> Unit, search: () -> Unit, onMenu: (Track) -> Unit) {
    val owner = vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val entries = vm.offline.downloads.collectAsStateWithLifecycle().value
    val progress = vm.offline.progress.collectAsStateWithLifecycle().value
    val player = vm.playback.state.collectAsStateWithLifecycle().value
    val library = vm.library.collectAsStateWithLifecycle().value
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("recent") }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember(owner) { mutableStateOf<Set<String>>(emptySet()) }
    var delete by remember(owner) { mutableStateOf<List<Track>>(emptyList()) }
    var deleting by remember(owner) { mutableStateOf(false) }
    LaunchedEffect(entries) { selected = selected.intersect(entries.map { it.track.id }.toSet()) }
    val shown = remember(entries, query, sort) { OfflineRules.filter(entries, query, sort) }
    val chosen = entries.filter { it.track.id in selected }
    val tracks = shown.map { it.track }
    LazyColumn(Modifier.fillMaxSize().testTag("offline-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeading(tr("Pusat offline", "Offline hub"), tr("Musik yang tetap dekat tanpa koneksi.", "Keep your music close without a connection.")) {
                ActionIcon(Icons.Rounded.Close, tr("Tutup pusat offline", "Close offline hub"), back)
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.DownloadDone, null, tint = MaterialTheme.colorScheme.primary)
                    Text("${entries.size} ${tr("lagu siap offline", "tracks ready offline")}", style = MaterialTheme.typography.titleLarge)
                    Text(OfflineRules.megabytes(entries.sumOf { it.bytes }), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("Unduhan tersimpan untuk akun ini di perangkat ini.", "Downloads belong to this account on this device."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(local) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(tr("Musik perangkat", "Device music")) }
                OutlinedButton(search) { Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp)); Text(tr("Cari lagu", "Find music")) }
            }
        }
        if (progress.isNotEmpty()) {
            item { SectionTitle(tr("Sedang mengunduh", "Downloading")) }
            items(progress.toList(), key = { it.first }) { (id, percent) ->
                val track = (player.queue + library.history + library.favorites).find { it.id == id }
                ListItem(headlineContent = { Text(track?.title ?: tr("Menyiapkan lagu", "Preparing track")) },
                    supportingContent = { Column { Text("$percent%"); LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth()) } },
                    trailingContent = { ActionIcon(Icons.Rounded.Close, tr("Batalkan unduhan", "Cancel download"), { vm.offline.cancel(id) }) })
            }
        }
        item {
            OutlinedTextField(query, { query = it.take(120) }, singleLine = true, label = { Text(tr("Cari unduhan", "Search downloads")) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
            SettingChoice(tr("Urutkan unduhan", "Sort downloads"), sort, listOf("recent" to tr("Terbaru", "Newest"), "title" to tr("Judul A–Z", "Title A–Z"), "size" to tr("Ukuran terbesar", "Largest first"))) { sort = it }
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selecting, { selecting = !selecting; selected = emptySet() }, { Text(tr("Pilih beberapa", "Select tracks")) })
                if (selecting) TextButton({ selected = if (shown.all { it.track.id in selected }) selected - shown.map { it.track.id }.toSet() else selected + shown.map { it.track.id } }) { Text(tr("Pilih semua hasil", "Select all results")) }
                else {
                    Button({ vm.playback.play(tracks) }, enabled = tracks.isNotEmpty() && player.roomRole == null) { Text(tr("Putar hasil", "Play results")) }
                    OutlinedButton({ vm.playback.play(tracks.shuffled()) }, enabled = tracks.isNotEmpty() && player.roomRole == null) { Text(tr("Acak", "Shuffle")) }
                }
            }
            if (selecting && chosen.isNotEmpty()) {
                Text("${chosen.size} ${tr("dipilih", "selected")} · ${OfflineRules.megabytes(chosen.sumOf { it.bytes })}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.playback.play(chosen.map { it.track }) }, enabled = player.roomRole == null) { Text(tr("Putar pilihan", "Play selected")) }
                    OutlinedButton({ delete = chosen.map { it.track } }, enabled = !deleting) { Text(tr("Hapus pilihan", "Delete selected")) }
                }
            }
            if (deleting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
        }
        if (shown.isEmpty()) item { EmptyState(if (query.isNotBlank()) tr("Tidak ada hasil", "No matches") else tr("Belum ada unduhan", "No downloads yet"),
            if (query.isNotBlank()) tr("Coba judul atau artis lain.", "Try another title or artist.") else tr("Buka opsi lagu lalu pilih Unduh untuk offline, atau gunakan musik perangkat.", "Open a track's options and choose Download for offline, or use device music."), Icons.Rounded.Download) }
        items(shown, key = { it.track.id }) { entry ->
            TrackRow(entry.track, { if (selecting) selected = if (entry.track.id in selected) selected - entry.track.id else selected + entry.track.id else if (player.roomRole == null) vm.playback.play(tracks, tracks.indexOf(entry.track)) },
                { onMenu(entry.track) }, active = entry.track.id == player.track?.id,
                trailing = { if (selecting) Checkbox(entry.track.id in selected, { checked -> selected = if (checked) selected + entry.track.id else selected - entry.track.id }) else Text(OfflineRules.megabytes(entry.bytes), Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall) })
        }
    }
    if (delete.isNotEmpty()) ConfirmDialog(tr("Hapus unduhan terpilih?", "Delete selected downloads?"), tr("Berkas offline dihapus. Playlist dan favorit tetap tersimpan.", "Removes offline files. Playlists and favorites are kept."), { delete = emptyList() }) {
        val removing = delete; delete = emptyList(); deleting = true
        vm.action { try { removing.forEach { vm.offline.remove(it, owner) }; selected = emptySet() } finally { deleting = false } }
    }
}
