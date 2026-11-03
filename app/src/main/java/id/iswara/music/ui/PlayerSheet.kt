package id.iswara.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.model.Track
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlayerSheet(vm: MusicViewModel, onDismiss: () -> Unit, onMenu: (Track) -> Unit, onEqualizer: () -> Unit, initialTab: Int = 0) {
    // Lazy content must retain the same snapshot used to choose its branch.
    val player = vm.playback.state.collectAsStateWithLifecycle().value
    val library = vm.library.collectAsStateWithLifecycle().value
    val lyrics = vm.lyrics.collectAsStateWithLifecycle().value
    val lyricsLoading = vm.lyricsLoading.collectAsStateWithLifecycle().value
    val lyricOffset = vm.lyricOffset.collectAsStateWithLifecycle().value
    val lyricsError = vm.lyricsError.collectAsStateWithLifecycle().value
    val lyricLines = lyrics?.lines.orEmpty()
    val plainLyrics = lyrics?.plain.orEmpty()
    val track = player.track ?: return
    val member = player.roomRole=="member"
    val context = LocalContext.current
    val settings = vm.settings.collectAsStateWithLifecycle().value
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val smart=vm.smartLyrics.state.collectAsStateWithLifecycle().value
    val owner=vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val smartActive=settings.smartRomanize || settings.smartTranslate
    val smartReady=smart.owner==owner && smart.track==track.id
    var smartSettings by remember {mutableStateOf(false)}
    var studio by remember { mutableStateOf(false) }
    var car by remember { mutableStateOf(false) }
    var banks by remember { mutableStateOf(false) }
    var pickLyrics by remember(track.id) { mutableStateOf(false) }
    var cardLines by remember(track.id) { mutableStateOf<List<String>?>(null) }
    var cardTimings by remember(track.id) { mutableStateOf<List<id.iswara.music.model.LyricLine>>(emptyList()) }
    var cardPicker by remember(track.id) { mutableStateOf(false) }
    var selecting by remember(track.id) { mutableStateOf(false) }
    var selectedLines by remember(track.id) { mutableStateOf<Set<Int>>(emptySet()) }
    val artworkPalette by produceState<ArtworkPalette?>(null, track.artwork, settings.playerColors) {
        value = null
        if (settings.playerColors) value = vm.artwork.palette(track.artwork)
    }
    val base = LyricsVisuals.scheme(if(tab==1)settings.lyricsTheme else "cover",MaterialTheme.colorScheme,track.id)
    val activePalette=if(tab==1 && settings.lyricsTheme!="cover")null else artworkPalette
    val colors = remember(activePalette, base) { ArtworkColors.player(activePalette, base.surface.toArgb(), base.primary.toArgb()) }
    val appMotion=LocalAppMotion.current
    val background by key(base.surface.luminance()>.5f){androidx.compose.animation.animateColorAsState(Color(colors.background),animationSpec=if(appMotion)androidx.compose.animation.core.tween(220)else androidx.compose.animation.core.snap(),label="artwork-background")}

    LaunchedEffect(initialTab) { tab = initialTab.coerceIn(0, 2) }
    var lyricTiming by remember { mutableStateOf(false) }
    var queueQuery by rememberSaveable { mutableStateOf("") }
    var followPaused by remember(track.id) { mutableStateOf(false) }
    var timer by remember { mutableStateOf(false) }
    var slider by remember(track.id) { mutableStateOf<Float?>(null) }
    val lyricList = rememberLazyListState()
    val activeLine = ListeningTools.activeLine(lyricLines,player.position,lyricOffset)
    val lyricDragged = lyricList.interactionSource.collectIsDraggedAsState().value
    LaunchedEffect(lyricDragged) { if (lyricDragged) followPaused = true }
    LaunchedEffect(settings.lyricsFollow) { followPaused = false }
    LaunchedEffect(track.id, lyrics) { selectedLines = emptySet(); selecting = false }
    LaunchedEffect(track.id){vm.smartLyrics.clearWord()}
    LaunchedEffect(settings.smartTranslate){if(!settings.smartTranslate)vm.smartLyrics.cancelTranslation()}
    LaunchedEffect(track.id,lyrics,settings.smartLanguage,settings.smartTarget,smartActive,owner) {
        if(smartActive && lyrics!=null)vm.smartLyrics.prepare(track.id,if(lyricLines.isNotEmpty())lyricLines.map {it.text}else plainLyrics.lines(),settings.smartLanguage,settings.smartTarget)
    }
    LaunchedEffect(smart.key,smart.rows,smart.busy,smart.percent,settings.smartTranslate,settings.translationWifi,settings.translationConsent) {
        if(settings.smartTranslate && settings.translationConsent && smartReady && smart.rows.isNotEmpty() && smart.translations.isEmpty() && !smart.busy && smart.percent<100 && smart.error==null && smart.stage!="Dibatalkan")vm.smartLyrics.translate(settings.translationWifi,settings.smartTarget)
    }
    LaunchedEffect(tab, track.id) { if (tab == 1) vm.loadLyrics() }
    LaunchedEffect(tab, activeLine, settings.lyricsMotion, settings.lyricsFollow, followPaused, selecting,smart.rows.size,smart.translations.size) {
        if (tab == 1 && activeLine >= 0 && settings.lyricsFollow && !followPaused && !selecting) {
            val anchor = -(lyricList.layoutInfo.viewportSize.height / 3)
            if (settings.lyricsMotion && appMotion) lyricList.animateScrollToItem(activeLine+1,anchor) else lyricList.scrollToItem(activeLine+1,anchor)
        }
    }
    CompositionLocalProvider(LocalLyricsStyle provides lyricStyle(track.id,settings.lyricsStyle),LocalLyricsMotion provides settings.lyricsMotion) {
    MaterialTheme(colorScheme = base.copy(primary = Color(colors.accent), onPrimary = Color(colors.onAccent))) {
    ModalBottomSheet(containerColor = background, onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(.96f).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(colors.glow), background, base.surface)))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(Icons.Rounded.KeyboardArrowDown, tr("Tutup pemutar", "Close player"), onDismiss)
                Text(if (track.local) tr("MUSIK LOKAL", "LOCAL MUSIC") else "iSwara Music", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                ActionIcon(Icons.Rounded.MoreHoriz, tr("Opsi lagu", "Track options"), { onMenu(track) })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(tr("Pemutar", "Player"), tr("Lirik", "Lyrics"), tr("Antrean", "Queue")).forEachIndexed { index, label -> FilterChip(tab == index, { tab = index }, { Text(label) }) }
            }
            if(player.roomRole!=null) TextButton({vm.togetherOpen.value=true}) {Icon(Icons.Rounded.Groups,null);Text(if(member)tr("Mengikuti host - buka room","Following host - open room") else tr("Kamu host - buka room","You are the host - open room"))}
            when (tab) {
                0 -> Box(Modifier.weight(1f)) {CompactPlayer(vm,player,track,{onMenu(track)},onEqualizer,{studio=true},{timer=true},{car=true})}
                1 -> Column(Modifier.weight(1f)) {
                    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip({cardPicker=true},{Text(tr("Kartu lirik","Lyrics card"))},enabled=lyricLines.isNotEmpty() || plainLyrics.isNotBlank())
                        AssistChip({smartSettings=true},{Text(tr("Lirik pintar","Smart lyrics"))},leadingIcon={Icon(Icons.Rounded.Translate,null,Modifier.size(18.dp))})
                        OutlinedButton({pickLyrics=true}) { Icon(Icons.Rounded.ManageSearch,null);Text(tr("Cari / impor lirik","Find / import lyrics")) }
                        FilterChip(settings.lyricsMotion,{vm.action {vm.preferences.toggle("lyricsMotion",!settings.lyricsMotion)}},{Text(tr("Animasi","Motion"))})
                        AssistChip({lyricTiming=true},{Text(tr("Atur waktu","Timing") + if(lyricOffset!=0L) " ${ListeningTools.offsetLabel(lyricOffset)} s" else "")},leadingIcon={Icon(Icons.Rounded.Tune,null,Modifier.size(18.dp))},enabled=lyricLines.isNotEmpty())
                        if (followPaused && settings.lyricsFollow) AssistChip({followPaused=false},{Text(tr("Ikuti baris aktif","Follow current line"))},leadingIcon={Icon(Icons.Rounded.MyLocation,null,Modifier.size(18.dp))})
                    }
                    if(smartActive && smartReady && smart.busy)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=20.dp))
                    if(smartActive && smartReady && smart.error!=null)TextButton({smartSettings=true},Modifier.padding(horizontal=12.dp)){Text(tr("Lirik pintar belum siap · Opsi","Smart lyrics unavailable · Options"))}
                    if(smartActive && smartReady && settings.smartTranslate && smart.translations.isNotEmpty())Box(Modifier.padding(horizontal=20.dp,vertical=6.dp)){TranslationAttribution()}
                    when {
                    lyricsLoading -> LoadingRows()
                    lyricsError != null -> EmptyState(tr("Lirik belum tersedia", "Lyrics unavailable"), lyricsError, action = tr("Coba lagi", "Retry"), onAction = {vm.loadLyrics(true)})
                    lyrics?.instrumental == true -> EmptyState(tr("Instrumental", "Instrumental"), tr("Lagu ini tidak memiliki vokal.", "This track has no vocals."), Icons.Rounded.Piano)
                    lyricLines.isNotEmpty() -> LazyColumn(Modifier.weight(1f), state = lyricList, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selecting, { selecting = !selecting; selectedLines = emptySet() }, { Text(tr("Pilih lirik", "Select lyrics")) })
                            OutlinedButton({ vm.action { val lines = selectedLines.sorted().mapNotNull { lyricLines.getOrNull(it)?.text }; cardLines = lines; cardTimings=selectedLines.sorted().mapNotNull {lyricLines.getOrNull(it)}.map {it.copy(time=ListeningTools.lyricSeek(it.time,lyricOffset,player.duration))} } }, enabled = selectedLines.isNotEmpty()) { Text(tr("Buat kartu", "Create card")) }
                            TextButton({ val text = if (selectedLines.isEmpty()) lyricLines.joinToString("\n") { it.text }.take(2500) else selectedLines.sorted().mapNotNull { lyricLines.getOrNull(it)?.text }.joinToString("\n"); context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://translate.google.com/?sl=auto&tl=id&text=" + android.net.Uri.encode(text)))) }) { Text(tr("Terjemahkan di browser", "Translate in browser")) }
                        } }
                        itemsIndexed(lyricLines,key={index,line -> "${track.id}:$index:${line.time}"}) { index, line ->
                            val smartRow=if(smartActive && smartReady && !selecting)smart.rows[line.text]else null
                            if(smartRow!=null)SmartLyricLine(smartRow,smart.translations[line.text].takeIf {settings.smartTranslate},index==activeLine,settings.smartRomanize,vm.smartLyrics::selectWord){if(!member)vm.playback.seek(ListeningTools.lyricSeek(line.time,lyricOffset,player.duration))else vm.togetherOpen.value=true}
                            else AnimatedLyricLine(line.text,index == activeLine,index in selectedLines,settings.lyricsMotion,lyricStyle(track.id,settings.lyricsStyle),settings.lyricsLarge) { if (selecting) selectedLines = if (index in selectedLines) selectedLines - index else selectedLines + index; else if(!member) vm.playback.seek(ListeningTools.lyricSeek(line.time,lyricOffset,player.duration)) else vm.togetherOpen.value=true }
                        }
                        item { Text("iSwara Music", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    plainLyrics.isNotBlank() -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp)) {
                        item {Text(tr("Lirik ini belum tersinkron.","These lyrics are not synced."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        itemsIndexed(plainLyrics.lines().filter {it.isNotBlank()}) {_,text->val row=if(smartActive && smartReady)smart.rows[text]else null
                            if(row!=null)SmartLyricLine(row,smart.translations[text].takeIf {settings.smartTranslate},false,settings.smartRomanize,vm.smartLyrics::selectWord,null)
                            else Text(text,style=if(settings.lyricsLarge)MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,modifier=Modifier.padding(vertical=8.dp))
                        }
                    }
                    else -> EmptyState(tr("Belum ada lirik", "No lyrics found"), tr("Coba cari lirik lain atau impor berkas LRC.", "Try another lyric or import an LRC file."), Icons.Rounded.Lyrics)
                }
                }
                2 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (player.undo != null) item { Surface(color = MaterialTheme.colorScheme.secondaryContainer) { Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(player.undo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(vm.playback::undo) { Text(tr("Urungkan", "Undo")) }
                    } } }
                    item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text("${player.queue.size} ${tr("lagu", "tracks")}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); TextButton({ vm.playback.clear(); onDismiss() },enabled=!member) { Text(tr("Kosongkan", "Clear")) } } }
                    item {
                        val ordered = player.order.ifEmpty { player.queue.indices.toList() }
                        val duration = ordered.drop(ordered.indexOf(player.index) + 1).sumOf { player.queue.getOrNull(it)?.duration?.coerceAtLeast(0) ?: 0 } + ((player.duration - player.position).coerceAtLeast(0) / 1000)
                        Text("${tr("Sisa antrean", "Queue remaining")} · ${duration / 60} ${tr("menit", "minutes")}", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (player.radioLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp)); Text(tr("Mencari lagu berikutnya...", "Finding your next song..."), Modifier.padding(horizontal = 20.dp)) }
                    if (player.radioError != null) item { Text(player.radioError!!, Modifier.padding(20.dp)); TextButton(vm.playback::radio) { Text(tr("Coba radio lagi", "Retry radio")) } }
                    item { OutlinedTextField(queueQuery,{queueQuery=it.take(120)},label={Text(tr("Cari dalam antrean","Search queue"))},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),trailingIcon={if(queueQuery.isNotBlank())ActionIcon(Icons.Rounded.Close,tr("Hapus pencarian antrean","Clear queue search"),{queueQuery=""})}) }
                    item { FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(vm::saveQueue, enabled = player.queue.isNotEmpty()) { Text(tr("Simpan playlist", "Save playlist")) }
                        OutlinedButton({banks=true}) { Text(tr("Antrean tersimpan","Saved queues")) }
                        OutlinedButton({vm.smartQueueOpen.value=true},enabled=player.roomRole==null){Text(tr("Kurasi antrean","Queue curator"))}
                        val ordered = player.order.ifEmpty { player.queue.indices.toList() }
                        OutlinedButton(vm.playback::clearUpcoming, enabled = !member && ordered.indexOf(player.index) < ordered.lastIndex) { Text(tr("Hapus berikutnya", "Clear upcoming")) }
                        OutlinedButton(vm.playback::clearPlayed, enabled = !member && ordered.indexOf(player.index) > 0) { Text(tr("Hapus yang lewat", "Clear played")) }
                        FilterChip(settings.autoplay && player.roomRole==null, { vm.action { vm.preferences.toggle("autoplay", !settings.autoplay) } }, { Text(tr("Radio otomatis", "Autoplay radio")) },enabled=player.roomRole==null)
                    } }
                    val ordered = player.order.ifEmpty { player.queue.indices.toList() }
                    val visibleOrder = ListeningTools.queueMatches(player.queue,ordered,queueQuery)
                    if (visibleOrder.isEmpty()) item { EmptyState(tr("Lagu tidak ditemukan","No matching tracks"),tr("Coba judul atau nama artis lain.","Try another title or artist."),Icons.Rounded.Search) }
                    itemsIndexed(visibleOrder, key = { _, index -> "$index:${player.queue[index].id}" }) { _, index ->
                        val row = ordered.indexOf(index)
                        val t = player.queue[index]
                        if (index == player.index) Text(tr("SEDANG DIPUTAR", "NOW PLAYING"), Modifier.padding(start = 20.dp, top = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (row == ordered.indexOf(player.index) + 1) Text(tr("BERIKUTNYA", "UP NEXT"), Modifier.padding(start = 20.dp, top = 16.dp), style = MaterialTheme.typography.labelSmall)
                        if (t.id in player.radioIds) Text(tr("Dari radio", "From radio"), Modifier.padding(start = 20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        TrackRow(t, { vm.playback.jump(index) }, { onMenu(t) }, active = index == player.index,enabled=!member)
                        Row(Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.End) {
                            ActionIcon(Icons.Rounded.KeyboardArrowUp, tr("Geser ke atas", "Move up"), { vm.playback.move(index, ordered[row - 1]) }, enabled = !member && row > 0 && queueQuery.isBlank())
                            ActionIcon(Icons.Rounded.KeyboardArrowDown, tr("Geser ke bawah", "Move down"), { vm.playback.move(index, ordered[row + 1]) }, enabled = !member && row < ordered.lastIndex && queueQuery.isBlank())
                            ActionIcon(Icons.Rounded.RemoveCircleOutline, tr("Hapus dari antrean", "Remove from queue"), { vm.playback.remove(index) },enabled=!member)
                        }
                    }
                }
            }
        }
    }
    }
    }
    if(smartSettings)SmartLyricsSettings(vm){smartSettings=false}
    WordMeaningDialog(vm)
    if (lyricTiming) AlertDialog(onDismissRequest={lyricTiming=false},title={Text(tr("Sinkronkan lirik","Sync lyrics"))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(tr("Khusus lagu ini. Majukan jika sorotan lirik terlambat.","For this track. Advance if the lyric highlight is late."))
        Text("${ListeningTools.offsetLabel(lyricOffset)} ${tr("detik","seconds")}",style=MaterialTheme.typography.headlineMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton({vm.adjustLyricOffset(-500)},enabled=lyricOffset > -15_000) { Text(tr("Mundurkan 0,5 dtk","Delay 0.5 sec")) }
            OutlinedButton({vm.adjustLyricOffset(500)},enabled=lyricOffset < 15_000) { Text(tr("Majukan 0,5 dtk","Advance 0.5 sec")) }
        }
        TextButton({vm.adjustLyricOffset(0,true)}) {Text(tr("Reset waktu","Reset timing"))}
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(settings.lyricsFollow,{vm.action {vm.preferences.toggle("lyricsFollow",it)}});Text(tr("Ikuti lirik otomatis","Follow lyrics automatically"),Modifier.padding(start=12.dp))}
    }},confirmButton={TextButton({lyricTiming=false}) {Text(tr("Selesai","Done"))}})
    if(cardPicker) LyricsExcerptPicker(if(lyricLines.isNotEmpty())lyricLines.map {it.text} else plainLyrics.lines().filter {it.isNotBlank()},{cardPicker=false}) {indices -> cardTimings=if(lyricLines.isNotEmpty())indices.mapNotNull {lyricLines.getOrNull(it)}.map {it.copy(time=ListeningTools.lyricSeek(it.time,lyricOffset,player.duration))}else emptyList();cardLines=indices.mapNotNull {index->if(lyricLines.isNotEmpty())lyricLines.getOrNull(index)?.text else plainLyrics.lines().filter {it.isNotBlank()}.getOrNull(index)};cardPicker=false}
    cardLines?.let {lines -> LyricsCardStudio(vm,track,lines,cardTimings) {cardLines=null}}
    if(studio) SoundStudio(vm){studio=false}
    if (car) CarMode(vm) { car = false }
    if (banks) SavedQueuesDialog(vm) { banks = false }
    if (pickLyrics) LyricsPicker(vm,track) { pickLyrics = false }
    if (timer) AlertDialog(onDismissRequest = { timer = false }, title = { Text(tr("Timer tidur", "Sleep timer")) }, text = { Column {
        TextButton({ vm.playback.sleepAfterTrack(); timer = false }, Modifier.fillMaxWidth()) { Text(tr("Sampai akhir lagu ini", "End of this track")) }
        listOf(0, 15, 30, 60, 120).forEach { minutes -> TextButton({ vm.playback.sleep(minutes); timer = false }, Modifier.fillMaxWidth()) { Text(if (minutes == 0) tr("Matikan timer", "Turn off timer") else "$minutes ${tr("menit", "minutes")}") } }
    } }, confirmButton = { TextButton({ timer = false }) { Text(tr("Tutup", "Close")) } })
}
