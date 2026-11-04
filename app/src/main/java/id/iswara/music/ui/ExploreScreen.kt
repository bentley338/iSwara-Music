package id.iswara.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.model.*

data class MusicTopic(val title: String, val english: String, val query: String, val group: String)
val musicTopics = listOf(
    MusicTopic("Pop Indonesia", "Indonesian pop", "pop Indonesia songs", "genre"),
    MusicTopic("Indie", "Indie", "indie Indonesia songs", "genre"),
    MusicTopic("Dangdut", "Dangdut", "dangdut Indonesia", "genre"),
    MusicTopic("Jazz", "Jazz", "jazz vocal songs", "genre"),
    MusicTopic("R&B", "R&B", "r&b soul songs", "genre"),
    MusicTopic("Rock", "Rock", "alternative rock songs", "genre"),
    MusicTopic("K-Pop", "K-Pop", "kpop songs", "genre"),
    MusicTopic("J-Pop", "J-Pop", "jpop songs", "genre"),
    MusicTopic("Elektronik", "Electronic", "electronic dance music", "genre"),
    MusicTopic("Klasik", "Classical", "classical piano orchestra", "genre"),
    MusicTopic("Santai", "Unwind", "acoustic chill Indonesia", "mood"),
    MusicTopic("Fokus", "Focus", "instrumental piano focus", "mood"),
    MusicTopic("Galau", "Heartbreak", "lagu galau Indonesia akustik", "mood"),
    MusicTopic("Olahraga", "Workout", "upbeat workout pop", "mood"),
    MusicTopic("Perjalanan", "On the road", "road trip songs", "mood"),
    MusicTopic("Menjelang tidur", "Wind down", "ambient sleep music", "mood"),
    MusicTopic("90-an", "90s", "90s pop songs", "decade"),
    MusicTopic("2000-an", "2000s", "lagu Indonesia 2000an", "decade"),
    MusicTopic("2010-an", "2010s", "2010s pop songs", "decade"),
)

@Composable fun ExploreScreen(vm: MusicViewModel, search: (String, SearchKind) -> Unit, mix: () -> Unit, onMenu: (Track) -> Unit) {
    val shelves = vm.shelves.collectAsStateWithLifecycle().value
    var group by rememberSaveable { mutableStateOf("all") }
    val english = LocalEnglish.current
    val groups = listOf("genre" to tr("Cari warna musikmu", "Explore genres"), "mood" to tr("Untuk setiap suasana", "For every mood"), "decade" to tr("Putar kembali waktunya", "Turn back time"))
    LazyColumn(Modifier.fillMaxSize().testTag("explore-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeading(tr("Jelajah", "Explore"), tr("Temukan warna lain dalam musikmu.", "Find another side of your music.")) }
        item {
            Surface(onClick = mix, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Rounded.GraphicEq, null, Modifier.size(32.dp))
                    Text(tr("Mulai dari suasana", "Start with a mood"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
                    Text(tr("Susun mix yang pas buat kegiatanmu.", "Build a mix that fits what you're doing."), modifier = Modifier.padding(top = 8.dp))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to tr("Semua", "All"), "genre" to tr("Genre", "Genres"), "mood" to tr("Suasana", "Moods"), "decade" to tr("Dekade", "Decades")).forEach { (key, label) -> FilterChip(group == key, { group = key }, { Text(label) }) }
            }
        }
        groups.filter { group == "all" || it.first == group }.forEach { (key, title) ->
            item { SectionTitle(title) }
            items(musicTopics.filter { it.group == key }.chunked(2)) { pair ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { topic ->
                        Surface(onClick = { search(topic.query, SearchKind.SONGS) }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                            Text(if (english) topic.english else topic.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item { SectionTitle(tr("Jelajahi katalog", "Browse the catalog")) }
        item { FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SearchKind.ALBUMS to "Album", SearchKind.PLAYLISTS to "Playlist", SearchKind.ARTISTS to tr("Artis", "Artists")).forEach { (kind, label) ->
                OutlinedButton({ search("Indonesia", kind) }) { Text(label) }
            }
        } }
        shelves.filter { it.tracks.isNotEmpty() }.take(2).forEach { shelf ->
            item { SectionTitle(if (english) "From your discovery" else shelf.title) }
            item { TrackShelf(shelf.tracks, { vm.playback.playFromDiscovery(it, shelf.query, shelf.tracks) }, onMenu) }
        }
    }
}

@Composable fun MixBuilderScreen(vm: MusicViewModel, back: () -> Unit, onMenu: (Track) -> Unit) {
    val owner = vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val snapshot = vm.mixPreview.collectAsStateWithLifecycle().value
    val result = if (snapshot.owner == owner || snapshot.owner.isEmpty()) snapshot else MixPreview()
    val player = vm.playback.state.collectAsStateWithLifecycle().value
    var prompt by rememberSaveable(owner) { mutableStateOf("") }
    var size by rememberSaveable { mutableIntStateOf(15) }
    var duration by rememberSaveable { mutableStateOf("any") }
    var name by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("mix-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeading(tr("Mix sesuai suasana", "Mood mixes"), tr("Cari lagu untuk suasanamu, lalu pilih hasilnya.", "Find tracks for your mood, then choose what stays.")) {
                ActionIcon(Icons.Rounded.Close, tr("Tutup pembuat mix", "Close mix builder"), back)
            }
            OutlinedTextField(prompt, { prompt = it.take(180) }, label = { Text(tr("Lagi ingin dengar apa?", "What do you feel like hearing?")) },
                placeholder = { Text(tr("Akustik galau buat hujan sore", "Acoustic songs for a rainy afternoon")) }, minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                musicTopics.filter { it.group == "mood" }.forEach { topic -> AssistChip({ prompt = topic.query }, { Text(if (LocalEnglish.current) topic.english else topic.title) }) }
            }
            SettingChoice(tr("Jumlah lagu", "Track count"), size.toString(), listOf(10, 15, 20).map { it.toString() to "$it ${tr("lagu", "tracks")}" }) { size = it.toInt() }
            SettingChoice(tr("Durasi per lagu", "Track duration"), duration, listOf("any" to tr("Bebas", "Any"), "short" to "< 4 ${tr("menit", "min")}", "long" to "4–10 ${tr("menit", "min")}")) { duration = it }
            Button({ vm.previewMix(prompt, size, duration) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), enabled = prompt.trim().length >= 3 && !result.loading) {
                if (result.loading) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (result.loading) tr("Menyusun mix…", "Building mix…") else tr("Susun mix", "Build mix"))
            }
            if (result.loading) TextButton(vm::cancelMix, Modifier.padding(horizontal = 12.dp)) { Text(tr("Batalkan pencarian mix", "Cancel mix search")) }
        }
        if (result.error != null) item { EmptyState(tr("Mix belum tersedia", "Mix unavailable"), result.error, Icons.Rounded.SearchOff, tr("Coba lagi", "Retry"), { vm.previewMix(prompt, size, duration) }) }
        if (!result.loading && result.tracks.isEmpty() && result.error == null) item { EmptyState(tr("Suasana dulu, musik kemudian", "Your mood comes first"), tr("Tulis suasana atau pilih saran di atas. Lagu akan ditampilkan sebelum diputar.", "Write a mood or choose a suggestion. Preview tracks before playing."), Icons.Rounded.GraphicEq) }
        if (result.tracks.isNotEmpty()) {
            item {
                SectionTitle(tr("Hasil mix", "Your mix"))
                Text(result.prompt, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${result.tracks.size} ${tr("lagu", "tracks")} · ${result.tracks.sumOf { it.duration } / 60} ${tr("menit", "min")}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.playback.play(result.tracks) }, enabled = !result.loading && player.roomRole == null) { Icon(Icons.Rounded.PlayArrow, null); Text(tr("Putar mix", "Play mix")) }
                    OutlinedButton({ vm.playback.play(result.tracks.shuffled()) }, enabled = !result.loading && player.roomRole == null) { Icon(Icons.Rounded.Shuffle, null); Text(tr("Acak", "Shuffle")) }
                    OutlinedButton({ name = result.prompt.take(100) }, enabled = !result.loading) { Text(tr("Simpan playlist", "Save playlist")) }
                }
                if (player.roomRole != null) Text(tr("Keluar room untuk memutar mix pribadi.", "Leave the room to play a personal mix."), Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall)
            }
            items(result.tracks, key = { it.id }) { track ->
                TrackRow(track, { if (player.roomRole == null) vm.playback.play(result.tracks, result.tracks.indexOf(track)) }, { onMenu(track) }, active = track.id == player.track?.id, enabled = player.roomRole == null)
                TextButton({ vm.removeMixTrack(track.id) }, Modifier.padding(start = 72.dp), enabled = !result.loading) { Text(tr("Keluarkan dari mix", "Remove from mix")) }
            }
        }
    }
    name?.let { title -> TextPrompt(tr("Simpan mix", "Save mix"), tr("Nama playlist", "Playlist name"), title, { name = it }, { name = null }) { vm.saveMix(name.orEmpty()); name = null } }
}
