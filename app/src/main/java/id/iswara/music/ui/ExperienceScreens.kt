package id.iswara.music.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.data.summarizeListening
import id.iswara.music.playback.AudioEffects
import kotlinx.serialization.encodeToString

@Composable fun EqualizerDialog(vm: MusicViewModel, close: () -> Unit) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val effect = vm.playback.effects.state.collectAsStateWithLifecycle().value
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 700.dp), shape = MaterialTheme.shapes.extraLarge) {
            LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
                item { PageHeading("Equalizer", tr("Sesuaikan warna suaramu.", "Shape your sound."), { ActionIcon(Icons.Rounded.Close, tr("Tutup equalizer", "Close equalizer"), close) }) }
                item { SettingToggle(tr("Aktifkan equalizer", "Enable equalizer"), if (effect.supported) tr("Tersimpan untuk pemutaran berikutnya.", "Saved for future playback.") else effect.message, settings.equalizer) { vm.action { vm.preferences.toggle("equalizer", it) } } }
                item { SettingChoice("Preset", settings.eqPreset, AudioEffects.presets.keys.map { it to it } + ("Custom" to tr("Manual", "Custom"))) { vm.action { vm.preferences.set("eqPreset", it) } } }
                if (settings.eqPreset == "Custom") items(5) { index ->
                    val frequency = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")[index]
                    val value = settings.eqBands.getOrElse(index) { 0 }
                    Column(Modifier.padding(horizontal = 20.dp)) { Text("$frequency  ${if (value > 0) "+" else ""}$value dB"); Slider(value.toFloat(), { next -> vm.action { val bands = List(5) { settings.eqBands.getOrElse(it) { 0 } }.toMutableList(); bands[index] = next.toInt(); vm.preferences.set("eqBands", id.iswara.music.model.AppJson.encodeToString(bands)) } }, valueRange = -12f..12f, steps = 23, modifier = Modifier.semantics { contentDescription = frequency }) }
                }
                item { SettingChoice("Bass Boost", settings.bass, listOf("0" to tr("Mati", "Off"), "250" to "25%", "500" to "50%", "750" to "75%", "1000" to "100%")) { vm.action { vm.preferences.set("bass", it) } } }
                item { SettingChoice("Reverb", settings.reverb, listOf("none" to tr("Mati", "Off"), "room" to "Room", "hall" to "Hall")) { vm.action { vm.preferences.set("reverb", it) } } }
                item { TextButton({ vm.action { vm.preferences.set("eqPreset", "Flat"); vm.preferences.set("bass", "0"); vm.preferences.set("reverb", "none"); vm.preferences.set("eqBands", "[0,0,0,0,0]") } }, Modifier.padding(horizontal = 12.dp)) { Text(tr("Kembalikan suara asli", "Reset sound")) } }
                if (effect.message.isNotBlank()) item { Text(effect.message, Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable fun ListeningRecap(vm: MusicViewModel, close: () -> Unit) {
    val entries = vm.listening.collectAsStateWithLifecycle().value
    var period by remember { mutableStateOf("month") }
    var clear by remember { mutableStateOf(false) }
    val now = java.time.LocalDate.now()
    val start = when (period) { "today" -> now; "week" -> now.minusDays(6); "year" -> now.withDayOfYear(1); else -> now.withDayOfMonth(1) }
    val summary = remember(entries, start, now) { summarizeListening(entries.filter { it.date >= start.toString() && it.date <= now.toString() }, "") }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp), shape = MaterialTheme.shapes.extraLarge) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item { PageHeading(tr("Rekap dengar", "Your listening"), "$start — $now", { ActionIcon(Icons.Rounded.Close, tr("Tutup rekap", "Close recap"), close) }) }
                item { Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("today" to tr("Hari ini", "Today"), "week" to tr("7 hari", "7 days"), "month" to tr("Bulan ini", "This month"), "year" to tr("Tahun ini", "This year")).forEach { (key, label) -> FilterChip(period == key, { period = key }, { Text(label) }) }
                } }
                item { Column(Modifier.padding(20.dp)) { Text("${summary.seconds / 60}", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary); Text(tr("menit bersama musik", "minutes with music"), style = MaterialTheme.typography.titleLarge); Text(tr("Dihitung dari pemutaran di perangkat ini.", "Based on listening on this device."), style = MaterialTheme.typography.bodySmall) } }
                if (summary.seconds == 0L) item { EmptyState(tr("Ceritamu baru dimulai", "Your story starts here"), tr("Putar lagu untuk mengisi rekapmu.", "Listen to music to build your recap."), Icons.Rounded.Insights) }
                if (summary.artists.isNotEmpty()) item { SectionTitle(tr("Artis teratas", "Top artists")) }
                itemsIndexed(summary.artists) { index, artist -> ListItem(headlineContent = { Text(artist.first.ifBlank { tr("Artis tidak diketahui", "Unknown artist") }) }, leadingContent = { Text("${index + 1}", color = MaterialTheme.colorScheme.primary) }, supportingContent = { Text("${artist.second / 60} ${tr("menit", "minutes")}") }) }
                if (summary.tracks.isNotEmpty()) item { SectionTitle(tr("Lagu paling menemani", "Your most played")) }
                items(summary.tracks) { (track, seconds) -> TrackRow(track, { vm.playback.play(listOf(track)) }, {}, trailing = { Text("${seconds / 60}m", Modifier.padding(12.dp)) }) }
                if (entries.isNotEmpty()) item { TextButton({ clear = true }, Modifier.padding(horizontal = 12.dp)) { Text(tr("Hapus rekap perangkat ini", "Clear device recap")) } }
            }
        }
    }
    if (clear) ConfirmDialog(tr("Hapus rekap dengar?", "Clear listening recap?"), tr("Statistik akun ini di perangkat ini akan dihapus.", "Clears this account's listening statistics on this device."), { clear = false }) {
        val owner = vm.libraryRepository.scope.value
        vm.action { vm.statistics.clearOwner(owner) }; clear = false
    }
}

@Composable fun CarMode(vm: MusicViewModel, close: () -> Unit) {
    val player = vm.playback.state.collectAsStateWithLifecycle().value
    val library = vm.library.collectAsStateWithLifecycle().value
    val activity = LocalActivity.current
    DisposableEffect(Unit) { activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Mode berkendara", "Car mode"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); ActionIcon(Icons.Rounded.Close, tr("Tutup mode berkendara", "Close car mode"), close) }
                Art(player.track?.artwork.orEmpty(), player.track?.title.orEmpty(), Modifier.widthIn(max = 240.dp).fillMaxWidth(.7f).aspectRatio(1f))
                Text(player.track?.title ?: tr("Belum ada lagu", "No track"), style = MaterialTheme.typography.headlineLarge)
                Text(player.track?.artist.orEmpty(), style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(vm.playback::previous, Modifier.size(80.dp), enabled = player.roomRole!="member" && player.track != null) { Icon(Icons.Rounded.SkipPrevious, tr("Lagu sebelumnya", "Previous track"), Modifier.size(42.dp)) }
                    FilledIconButton(vm.playback::toggle, Modifier.size(100.dp), enabled = player.track != null) { Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr("Putar atau jeda", "Play or pause"), Modifier.size(56.dp)) }
                    FilledTonalIconButton(vm.playback::next, Modifier.size(80.dp), enabled = player.roomRole!="member" && player.canNext) { Icon(Icons.Rounded.SkipNext, tr("Lagu berikutnya", "Next track"), Modifier.size(42.dp)) }
                }
                val liked = library.favorites.any { it.id == player.track?.id }
                OutlinedButton({ player.track?.let(vm::favorite) }, Modifier.fillMaxWidth().heightIn(min = 64.dp), enabled = player.track != null) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null); Text(tr("Favorit", "Favorite")) }
            }
        }
    }
}
