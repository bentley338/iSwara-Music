package id.iswara.music.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.R
import id.iswara.music.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import androidx.compose.animation.*
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MusicApp(vm: MusicViewModel) {
    val context = LocalContext.current
    val together = vm.together.state.collectAsStateWithLifecycle().value
    val togetherOpen = vm.togetherOpen.collectAsStateWithLifecycle().value
    val state = vm.screenPlayback.collectAsStateWithLifecycle().value
    val appSettings = vm.settings.collectAsStateWithLifecycle().value
    val animateScreens=LocalAppMotion.current
    val library = vm.library.collectAsStateWithLifecycle().value
    val detail = vm.detail.collectAsStateWithLifecycle().value
    val account = vm.auth.account.collectAsStateWithLifecycle().value
    val owner = vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val pages = key(owner) { rememberSaveableStateHolder() }
    val recovery = vm.recovery.collectAsStateWithLifecycle().value
    val radioRequest=vm.radioOpen.collectAsStateWithLifecycle().value
    val updatesOpen=vm.updatesOpen.collectAsStateWithLifecycle().value
    val importOpen=vm.importOpen.collectAsStateWithLifecycle().value
    val smartQueueOpen=vm.smartQueueOpen.collectAsStateWithLifecycle().value
    LaunchedEffect(vm){if(vm.preferences.settings.first().updateAuto)vm.updates.check()}
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var player by rememberSaveable { mutableStateOf(false) }
    var playerTab by rememberSaveable { mutableIntStateOf(0) }
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryCategory by rememberSaveable { mutableIntStateOf(0) }
    var featureDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var linkPrompt by remember { mutableStateOf<String?>(null) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var sourceTrack by remember {mutableStateOf<Track?>(null)}
    var playlistTrack by remember { mutableStateOf<Track?>(null) }
    var playlistName by remember { mutableStateOf<String?>(null) }
    val downloads = vm.offline.downloads.collectAsStateWithLifecycle().value
    val downloadProgress = vm.offline.progress.collectAsStateWithLifecycle().value
    var equalizerOpen by remember { mutableStateOf(false) }
    var systemMessage by remember { mutableStateOf<String?>(null) }
    val equalizerUnavailable = tr("Equalizer sistem tidak tersedia di perangkat ini.", "A system equalizer is not available on this device.")
    val snackbars = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.action {
        val content = vm.libraryRepository.export()
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) } ?: error("Backup tidak bisa disimpan") }
        vm.messages.emit("Backup disimpan")
    } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.action {
        val raw = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { stream ->
            BackupReader.read(stream)
        } ?: error("Backup tidak bisa dibaca") }
        vm.libraryRepository.import(raw); vm.messages.emit("Backup berhasil diimpor")
    } }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        vm.openFile(uri)
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if (allowed) vm.scanLocal() else vm.action { vm.messages.emit("Akses musik ditolak. Gunakan Pilih file untuk membuka satu lagu.") } }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.playing) { if (state.playing && !askedNotifications && Build.VERSION.SDK_INT >= 33) {
        askedNotifications = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
    } }
    LaunchedEffect(vm) { vm.messages.collect { snackbars.showSnackbar(it) } }
    LaunchedEffect(account?.id) { featureDialog = null;sourceTrack=null }
    if(updatesOpen)UpdateScreen(vm){vm.updatesOpen.value=false}
    if(importOpen)PlaylistImportScreen(vm){vm.importOpen.value=false}
    if(smartQueueOpen)SmartQueueScreen(vm){vm.smartQueueOpen.value=false}
    val navigate: (Int) -> Unit = { next -> tab = next; settingsOpen = false; section = null; libraryCategory = 0; vm.closeDetail() }
    LaunchedEffect(radioRequest){if(radioRequest){navigate(6);vm.radioOpen.value=false}}
    val showPlayer: (Int) -> Unit = { target ->
        if (state.track != null) { playerTab = target; player = true }
        else vm.action { vm.messages.emit("Putar lagu terlebih dahulu, lalu buka lirik atau antrean.") }
    }
    val openFeature: (SpaceFeature) -> Unit = { feature -> when (feature) {
        SpaceFeature.SMART_QUEUE -> vm.smartQueueOpen.value=true
        SpaceFeature.IMPORT -> vm.importOpen.value=true
        SpaceFeature.UPDATES -> vm.updatesOpen.value=true
        SpaceFeature.EXPLORE -> navigate(4)
        SpaceFeature.RADIO -> navigate(6)
        SpaceFeature.MIX -> { section = "mix"; vm.closeDetail() }
        SpaceFeature.OFFLINE -> { section = "offline"; vm.closeDetail() }
        SpaceFeature.LOCAL -> { navigate(2); libraryCategory = 3 }
        SpaceFeature.QUEUE -> showPlayer(2)
        SpaceFeature.LYRICS -> showPlayer(1)
        SpaceFeature.ACCOUNT -> navigate(3)
        SpaceFeature.SETTINGS -> { settingsOpen = true }
        SpaceFeature.TOGETHER -> { vm.togetherOpen.value = true }
        SpaceFeature.BACKUP -> export.launch("iswara-backup.json")
        SpaceFeature.RESTORE -> import.launch(arrayOf("application/json", "text/plain"))
        SpaceFeature.LINK -> { linkPrompt = "" }
        else -> { featureDialog = feature.id }
    } }
    BackHandler(enabled = settingsOpen || detail != null || section != null || tab != 0) {
        when { settingsOpen -> settingsOpen = false; detail != null -> vm.closeDetail(); section != null -> section = null; tab == 3 -> navigate(5); else -> navigate(0) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val sideNavigation = maxWidth >= 600.dp && maxHeight >= 480.dp
    Row(Modifier.fillMaxSize()) {
    if (sideNavigation) AppSideNavigation(tab, navigate, { settingsOpen = true })
    Scaffold(
        modifier = Modifier.weight(1f),
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                AppUpdateBanner(vm){vm.updatesOpen.value=true}
                together.room?.let { room -> Surface(onClick={if(room.radio)navigate(6)else vm.togetherOpen.value=true},color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.fillMaxWidth()) {Row(Modifier.padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Groups,null);Text("${room.title} \u00b7 ${room.members} ${tr("anggota","members")}",Modifier.weight(1f).padding(start=12.dp),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge);Icon(Icons.Rounded.ChevronRight,tr("Buka room","Open room"))
                }} }

                if (state.undo != null) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.undo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(vm.playback::undo) { Text(tr("Urungkan", "Undo")) }
                    }
                }
                MiniPlayerBar(vm){showPlayer(0)}
                if (!sideNavigation) AppBottomNavigation(tab, navigate)
                else Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (settingsOpen) SettingsScreen(vm, onBack = { settingsOpen = false }, onExport = { export.launch("iswara-backup.json") }, onImport = { import.launch(arrayOf("application/json", "text/plain")) })
            else if (detail != null) DetailScreen(vm, detail!!, onMenu = { menuTrack = it })
            else if (section == "mix") MixBuilderScreen(vm, { section = null }, { menuTrack = it })
            else if (section == "offline") OfflineScreen(vm, { section = null }, { navigate(2); libraryCategory = 3 }, { navigate(1) }, { menuTrack = it })
            else AnimatedContent(tab,transitionSpec={if(animateScreens)fadeIn(tween(160)) togetherWith fadeOut(tween(100))else EnterTransition.None togetherWith ExitTransition.None},label="navigation") {currentTab->pages.SaveableStateProvider(currentTab) { when (currentTab) {
                0 -> HomeScreen(vm, onMenu = { menuTrack = it }, onSearch = { navigate(1) }, onAccount = { settingsOpen = true }, onPlayer = {showPlayer(0)}, onExplore = {navigate(4)}, onSpace = {navigate(5)})
                1 -> SearchScreen(vm, onMenu = { menuTrack = it })
                2 -> LibraryScreen(vm, onMenu = { menuTrack = it }, onCreate = { playlistName = "" }, onScan = {
                    val needed = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                    if (ContextCompat.checkSelfPermission(context, needed) == PackageManager.PERMISSION_GRANTED) vm.scanLocal() else permission.launch(needed)
                }, onFile = { file.launch(arrayOf("audio/*")) }, initialCategory = libraryCategory)
                3 -> Column { Row(Modifier.fillMaxWidth()) { TextButton({navigate(5)}) {Icon(Icons.AutoMirrored.Rounded.ArrowBack,null);Text(tr("Kembali ke Ruang", "Back to Space"))} }; Box(Modifier.weight(1f)) {AccountScreen(vm, onSettings = { settingsOpen = true })} }
                4 -> ExploreScreen(vm, { query, kind -> navigate(1); vm.discover(query, kind) }, { section = "mix" }, {menuTrack=it})
                5 -> SpaceScreen(vm, openFeature)
                6 -> RadioScreen(vm,{navigate(3)},{vm.togetherOpen.value=true},{showPlayer(2)})
            } } }
        }
    }
    }
    }
    if (player && state.track != null) PlayerSheet(vm, onDismiss = { player = false }, onMenu = { menuTrack = it }, onEqualizer = {
        equalizerOpen = true
    }, initialTab = playerTab)
    if(togetherOpen) TogetherScreen(vm,{vm.togetherOpen.value=false},{vm.togetherOpen.value=false;navigate(3)})
    if (equalizerOpen) EqualizerDialog(vm) { equalizerOpen = false }
    when (featureDialog) {
        "studio" -> SoundStudio(vm) { featureDialog = null }
        "eq" -> EqualizerDialog(vm) { featureDialog = null }
        "widgets" -> WidgetGallery { featureDialog = null }
        "banks" -> SavedQueuesDialog(vm) { featureDialog = null }
        "recap" -> ListeningRecap(vm) { featureDialog = null }
        "car" -> CarMode(vm) { featureDialog = null }
        "admin" -> account?.let { current -> AdminScreen(vm, current.id) { featureDialog = null } }
    }
    if (linkPrompt != null) TextPrompt(tr("Buka tautan musik", "Open music link"), tr("Tautan lagu atau playlist", "Track or playlist link"), linkPrompt!!, { linkPrompt = it }, { linkPrompt = null }) { vm.shared(linkPrompt.orEmpty()); linkPrompt = null }
    sourceTrack?.let {SourcePicker(vm,it){sourceTrack=null}}
    menuTrack?.let { track -> ModalBottomSheet(onDismissRequest = { menuTrack = null }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Art(track.artwork,track.title,Modifier.size(76.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("iSwara Music", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        MenuAction(Icons.Rounded.PlayArrow, tr("Putar lagu", "Play track")) { vm.playback.play(listOf(track)); menuTrack = null }
        if (track.artist.isNotBlank() && !track.local) MenuAction(Icons.Rounded.PersonSearch,tr("Cari lagu artis ini","Find this artist")) {navigate(1);vm.discover(track.artist,SearchKind.SONGS);menuTrack=null;player=false}
        val liked = library.favorites.any { it.id == track.id }
        MenuAction(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) tr("Hapus dari favorit", "Remove favorite") else tr("Simpan ke favorit", "Save favorite")) { vm.favorite(track); menuTrack = null }
        MenuAction(Icons.Rounded.PlaylistAdd, tr("Tambah ke playlist", "Add to playlist")) { playlistTrack = track; menuTrack = null }
        if(!track.local)MenuAction(Icons.Rounded.AltRoute,tr("Sumber audio & cadangan","Audio sources & alternatives")){sourceTrack=track;menuTrack=null}
        if(state.roomRole!="member") MenuAction(Icons.Rounded.QueueMusic, tr("Putar berikutnya", "Play next")) { vm.playback.enqueue(track, true); menuTrack = null }
        MenuAction(Icons.Rounded.AddToQueue, if(together.room!=null) tr("Tambah ke antrean room","Add to room queue") else tr("Tambah ke antrean", "Add to queue")) { vm.playback.enqueue(track, false); menuTrack = null }
        if (!track.local && track.id !in downloadProgress) {
            if (downloads.any { it.track.id == track.id }) MenuAction(Icons.Rounded.DeleteOutline, tr("Hapus unduhan", "Remove download")) { vm.action { vm.offline.remove(track) }; menuTrack = null }
            else MenuAction(Icons.Rounded.Download, tr("Unduh untuk offline", "Download for offline")) { vm.download(track); menuTrack = null }
        }
        if (track.id in downloadProgress) Text("${tr("Mengunduh", "Downloading")} ${downloadProgress[track.id]}%", Modifier.padding(20.dp))
        if (!track.local) MenuAction(Icons.Rounded.Share, tr("Bagikan tautan lagu", "Share track link")) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "iswara://track/${track.id}\n${track.url}"), null)); menuTrack = null }
        Spacer(Modifier.height(24.dp))
        }
    } }
    playlistTrack?.let { track -> ModalBottomSheet(onDismissRequest = { playlistTrack = null }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface) {
        Text(tr("Pilih playlist", "Choose playlist"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.heightIn(max = 400.dp)) { items(library.playlists, key = { it.id }) { p -> MenuAction(Icons.Rounded.PlaylistPlay, p.title) { vm.addTo(p, track); playlistTrack = null } } }
        MenuAction(Icons.Rounded.Add, tr("Buat playlist baru", "Create playlist")) { playlistName = "" }
        Spacer(Modifier.height(24.dp))
    } }
    if (playlistName != null) TextPrompt(tr("Playlist baru", "New playlist"), tr("Nama playlist", "Playlist name"), playlistName!!, { playlistName = it }, { playlistName = null }) {
        val name = playlistName.orEmpty(); val track = playlistTrack
        vm.action { val p = vm.libraryRepository.createPlaylist(name); if (track != null) vm.libraryRepository.add(p, track) }
        playlistName = null; playlistTrack = null
    }
    if (recovery) {
        var password by remember { mutableStateOf("") }
        TextPrompt(tr("Atur password baru", "Set new password"), tr("Password minimal 8 karakter", "Password, at least 8 characters"), password, { password = it }, { vm.recovery.value = false }, secret = true) { vm.finishRecovery(password) }
    }
    if (systemMessage != null) AlertDialog(onDismissRequest = { systemMessage = null }, title = { Text("Equalizer") }, text = { Text(systemMessage!!) }, confirmButton = { TextButton({ systemMessage = null }) { Text(tr("Tutup", "Close")) } })
}

@Composable fun MenuAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge) }
}

@Composable fun TextPrompt(title: String, label: String, value: String, onValue: (String) -> Unit, dismiss: () -> Unit, secret: Boolean = false, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true, visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None) }, confirmButton = { TextButton(confirm, enabled = value.isNotBlank() && (!secret || value.length >= 8)) { Text(tr("Simpan", "Save")) } }, dismissButton = { TextButton(dismiss) { Text(tr("Batal", "Cancel")) } })
}
