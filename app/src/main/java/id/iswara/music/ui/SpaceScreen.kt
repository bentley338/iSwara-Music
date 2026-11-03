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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class SpaceFeature(val id: String, val title: String, val english: String, val description: String, val englishDescription: String, val group: Int, val icon: ImageVector) {
    SMART_QUEUE("smart-queue","Kurasi antrean","Queue curator","Lagu berikutnya dari riwayat dan pilihanmu.","Next tracks based on your listening and choices.",1,Icons.Rounded.AutoAwesome),
    IMPORT("import","Impor playlist","Import playlists","Tautan publik, CSV, JSON, M3U, atau daftar lagu.","Public links, CSV, JSON, M3U or a song list.",1,Icons.Rounded.PlaylistAdd),
    UPDATES("updates","Pembaruan aplikasi","App updates","Versi baru, unduhan, dan pemasangan.","New releases, downloads and installation.",4,Icons.Rounded.SystemUpdate),
    EXPLORE("explore","Jelajah musik","Explore music","Genre, suasana, album, dan dekade.","Genres, moods, albums, and decades.",1,Icons.Rounded.Explore),
    RADIO("radio","Radio & DJ Lounge","Radio & DJ Lounge","Siaran publik, komentar, dan reaksi bersama.","Public stations, comments, and reactions.",3,Icons.Rounded.Radio),
    MIX("mix", "Mix sesuai suasana", "Mood mixes", "Susun lagu, cek hasilnya, lalu putar.", "Build, preview, then play your mix.", 1, Icons.Rounded.GraphicEq),
    STUDIO("studio", "Studio audio", "Audio studio", "Remix, nada dasar, dan karaoke AI.", "Remix, pitch, and AI karaoke.", 2, Icons.Rounded.Tune),
    LYRICS("lyrics", "Studio lirik", "Lyrics studio", "Ikuti lirik dan buat gambar, audio, atau video.", "Follow lyrics and create images, audio, or video.", 2, Icons.Rounded.Lyrics),
    EQ("eq", "Equalizer & bass", "Equalizer & bass", "Sesuaikan karakter suara.", "Shape your sound.", 2, Icons.Rounded.Equalizer),
    QUEUE("queue", "Antrean sekarang", "Current queue", "Atur urutan dan lagu berikutnya.", "Arrange what's playing next.", 1, Icons.Rounded.QueueMusic),
    BANKS("banks", "Antrean tersimpan", "Saved queues", "Lanjutkan sesi musik yang berbeda.", "Resume different listening sessions.", 1, Icons.Rounded.PlaylistPlay),
    OFFLINE("offline", "Pusat offline", "Offline hub", "Kelola unduhan dan ruang penyimpanan.", "Manage downloads and storage.", 1, Icons.Rounded.DownloadDone),
    LOCAL("local", "Musik perangkat", "Device music", "Pindai lagu lokal atau pilih berkas.", "Scan local songs or choose a file.", 1, Icons.Rounded.FolderOpen),
    TOGETHER("together", "Denger Bareng", "Listen Together", "Buat room, bagikan kode, dan isi antrean bersama.", "Create a room, share its code, and build a queue together.", 3, Icons.Rounded.Groups),
    WIDGETS("widgets", "Widget layar utama", "Home screen widgets", "Vinyl, Retro Classic, dan minimal transparan.", "Vinyl, Retro Classic, and transparent minimal.", 2, Icons.Rounded.Widgets),
    RECAP("recap", "Rekap dengar", "Listening recap", "Lihat lagu dan artis yang paling menemani.", "See your most played tracks and artists.", 4, Icons.Rounded.Insights),
    CAR("car", "Mode berkendara", "Car mode", "Kontrol besar untuk perjalanan.", "Larger controls for your journey.", 1, Icons.Rounded.DirectionsCar),
    LINK("link", "Buka tautan musik", "Open music link", "Buka lagu atau playlist dari tautan.", "Open a track or playlist link.", 1, Icons.Rounded.Link),
    ACCOUNT("account", "Akun & sinkronisasi", "Account & sync", "Profil, login, dan koleksi cloud.", "Profile, sign in, and cloud library.", 4, Icons.Rounded.AccountCircle),
    SETTINGS("settings", "Pengaturan", "Settings", "Tampilan, pemutaran, bahasa, dan lirik melayang.", "Appearance, playback, language, and floating lyrics.", 4, Icons.Rounded.Settings),
    BACKUP("backup", "Ekspor backup", "Export backup", "Simpan playlist dan favorit sebagai JSON.", "Save playlists and favorites as JSON.", 4, Icons.Rounded.UploadFile),
    RESTORE("restore", "Impor backup", "Import backup", "Pulihkan koleksi dari berkas JSON.", "Restore your library from a JSON file.", 4, Icons.Rounded.FileOpen),
    ADMIN("admin", "Panel admin", "Admin panel", "Kelola konten aplikasi dan pengguna.", "Manage app content and users.", 4, Icons.Rounded.AdminPanelSettings);
}

object SpaceRules {
    private val defaults = listOf(SpaceFeature.STUDIO, SpaceFeature.OFFLINE, SpaceFeature.TOGETHER, SpaceFeature.RECAP)
    fun pins(raw: String?, admin: Boolean): List<SpaceFeature> =
        (if (raw == null) defaults else raw.split(',').mapNotNull { id -> SpaceFeature.entries.find { it.id == id } })
            .distinct().filter { it != SpaceFeature.ADMIN || (id.iswara.music.BuildConfig.ADMIN_TOOLS_ENABLED && admin) }.take(6)
    fun toggle(pins: List<SpaceFeature>, feature: SpaceFeature): List<SpaceFeature> =
        if (feature in pins) pins - feature else (pins + feature).takeLast(6)
    fun matches(feature: SpaceFeature, query: String, group: Int, admin: Boolean): Boolean =
        (feature != SpaceFeature.ADMIN || (id.iswara.music.BuildConfig.ADMIN_TOOLS_ENABLED && admin)) && (group == 0 || feature.group == group) &&
            (query.isBlank() || "${feature.title} ${feature.english} ${feature.description} ${feature.englishDescription}".contains(query.trim(), true))
}

@Composable fun SpaceScreen(vm: MusicViewModel, open: (SpaceFeature) -> Unit) {
    val owner = vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val account = vm.auth.account.collectAsStateWithLifecycle().value
    val access = vm.adminAccess.collectAsStateWithLifecycle().value
    val admin = id.iswara.music.BuildConfig.ADMIN_TOOLS_ENABLED && account != null && access.allowed && access.owner == account.id
    val rawPins = remember(owner) { vm.preferences.raw("spacePins:$owner") }.collectAsStateWithLifecycle(initialValue = null).value
    val pins = remember(rawPins, admin) { SpaceRules.pins(rawPins, admin) }
    val player = vm.playback.state.collectAsStateWithLifecycle().value
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableIntStateOf(0) }
    val english = LocalEnglish.current
    val shown = SpaceFeature.entries.filter { SpaceRules.matches(it, query, group, admin) }
    val groups = listOf(1 to tr("Dengarkan", "Listen"), 2 to tr("Kreasikan", "Create"), 3 to tr("Bersama", "Together"), 4 to tr("Pribadi", "Personal"))
    LazyColumn(Modifier.fillMaxSize().testTag("space-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeading(tr("Ruang musikmu", "Your music space"), tr("Dengarkan, kreasikan, dan bagikan.", "Listen, create, and share.")) {
                ActionIcon(Icons.Rounded.AccountCircle, tr("Buka akun", "Open account"), { open(SpaceFeature.ACCOUNT) })
            }
            OutlinedTextField(query, { query = it.take(100) }, singleLine = true,
                label = { Text(tr("Cari fitur", "Find a feature")) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) ActionIcon(Icons.Rounded.Close, tr("Hapus pencarian fitur", "Clear feature search"), { query = "" }) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(tr("Semua", "All"), tr("Dengarkan", "Listen"), tr("Kreasikan", "Create"), tr("Bersama", "Together"), tr("Pribadi", "Personal")).forEachIndexed { index, label ->
                    FilterChip(group == index, { group = index }, { Text(label) })
                }
            }
        }
        if (query.isBlank() && group == 0) {
            if (pins.isNotEmpty()) item {
                Text(tr("Pintasanmu", "Your shortcuts"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pins.forEach { feature -> AssistChip({ open(feature) }, { Text(if (english) feature.english else feature.title) }, leadingIcon = { Icon(feature.icon, null, Modifier.size(18.dp)) }) }
                }
            }
            item {
                Surface(onClick = { open(SpaceFeature.MIX) }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Mix buat harimu", "A mix for your day"), style = MaterialTheme.typography.titleLarge)
                            Text(tr("Pilih suasana. Pratinjau lagunya sebelum diputar.", "Pick a mood. Preview the tracks before playing."), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            player.track?.let { track -> item {
                Surface(onClick = { open(SpaceFeature.QUEUE) }, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Art(track.artwork, track.title, Modifier.size(56.dp))
                        Column(Modifier.weight(1f)) { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall); Text("${player.queue.size} ${tr("lagu di antrean", "tracks queued")}", style = MaterialTheme.typography.bodySmall) }
                        Icon(Icons.Rounded.QueueMusic, null)
                    }
                }
            } }
        }
        if (shown.isEmpty()) item { EmptyState(tr("Fitur tidak ditemukan", "No matching feature"), tr("Coba kata lain atau pilih Semua.", "Try another word or choose All."), Icons.Rounded.SearchOff) }
        groups.forEach { (category, title) ->
            val features = shown.filter { it.group == category }
            if (features.isNotEmpty()) {
                item { SectionTitle(title) }
                items(features, key = { it.id }) { feature ->
                    ListItem(headlineContent = { Text(if (english) feature.english else feature.title) },
                        supportingContent = { Text(if (english) feature.englishDescription else feature.description, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(feature.icon, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { IconToggleButton(feature in pins, {
                            val next = SpaceRules.toggle(pins, feature)
                            vm.action {
                                vm.preferences.set("spacePins:$owner", next.joinToString(",") { it.id })
                                if (pins.size == 6 && feature !in pins) vm.messages.emit("Pintasan paling awal diganti. Maksimal enam pintasan.")
                            }
                        }) { Icon(if (feature in pins) Icons.Rounded.PushPin else Icons.Rounded.Add, tr("Pintasan: ", "Shortcut: ") + if (english) feature.english else feature.title) } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open(feature) }.testTag("feature-${feature.id}"))
                }
            }
        }
    }
}
