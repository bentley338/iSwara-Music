package id.iswara.music.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.model.Track

@Composable fun SavedQueuesDialog(vm: MusicViewModel, close: () -> Unit) {
    val banks = vm.savedQueues.collectAsStateWithLifecycle().value
    val player = vm.playback.state.collectAsStateWithLifecycle().value
    var name by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<Pair<String,String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<String,String>?>(null) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
                item { PageHeading(tr("Antrean tersimpan", "Saved queues"), tr("Urutan dan posisi dengar, masing-masing.", "Each keeps its order and listening position."), { ActionIcon(Icons.Rounded.Close,tr("Tutup antrean tersimpan","Close saved queues"),close) }) }
                item { Button({ name = "" },Modifier.fillMaxWidth().padding(horizontal = 20.dp),enabled = player.queue.isNotEmpty()) { Icon(Icons.Rounded.Add,null); Text(tr("Simpan antrean sekarang","Save current queue")) } }
                item { Text(tr("Tersimpan di perangkat, terpisah untuk setiap akun.","Saved on this device, separately for each account."),Modifier.padding(20.dp),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (banks.isEmpty()) item { EmptyState(tr("Belum ada antrean tersimpan","No saved queues"),tr("Isi antrean lalu simpan sebagai Kerja, Santai, atau nama pilihanmu.","Fill a queue, then save it as Work, Relax, or your own name."),Icons.Rounded.QueueMusic) }
                items(banks,key = { it.id }) { bank ->
                    val track = bank.snapshot.tracks.getOrNull(bank.snapshot.index) ?: bank.snapshot.tracks.firstOrNull()
                    Row(Modifier.fillMaxWidth().clickable { vm.loadBank(bank.id); close() }.padding(horizontal = 20.dp,vertical = 12.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Art(track?.artwork.orEmpty(),bank.title,Modifier.size(58.dp))
                        Column(Modifier.weight(1f)) { Text(bank.title,style = MaterialTheme.typography.titleMedium,color = if (player.activeBank == bank.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,maxLines = 2,overflow = TextOverflow.Ellipsis); Text("${bank.snapshot.tracks.size} ${tr("lagu","tracks")} · ${timeLabel(bank.snapshot.position)}",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant); Text(track?.title.orEmpty(),style = MaterialTheme.typography.bodySmall,maxLines = 1,overflow = TextOverflow.Ellipsis) }
                        ActionIcon(Icons.Rounded.Edit,tr("Ubah nama antrean","Rename queue"),{ rename = bank.id to bank.title })
                        ActionIcon(Icons.Rounded.DeleteOutline,tr("Hapus antrean tersimpan","Delete saved queue"),{ deleting = bank.id to bank.title })
                    }
                }
            }
        }
    }
    name?.let { value -> TextPrompt(tr("Simpan antrean","Save queue"),tr("Nama antrean","Queue name"),value,{name=it},{name=null}) { vm.saveBank(name.orEmpty()); name = null } }
    rename?.let { edit -> TextPrompt(tr("Ubah nama antrean","Rename queue"),tr("Nama antrean","Queue name"),edit.second,{rename=edit.first to it},{rename=null}) { val title = rename?.second.orEmpty(); vm.action { vm.queueBanks.rename(edit.first,title) }; rename=null } }
    deleting?.let { item -> ConfirmDialog(tr("Hapus antrean tersimpan?","Delete saved queue?"),item.second,{deleting=null}) { vm.deleteBank(item.first); deleting=null } }
}

@Composable fun LyricsPicker(vm: MusicViewModel, track: Track, close: () -> Unit) {
    val context = LocalContext.current
    var query by remember(track.id) { mutableStateOf("${track.title} ${track.artist}".take(200)) }
    val candidates = vm.lyricCandidates.collectAsStateWithLifecycle().value
    val busy = vm.lyricSearchBusy.collectAsStateWithLifecycle().value
    val error = vm.lyricSearchError.collectAsStateWithLifecycle().value
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { vm.importLyrics(track,uri,context); close() } }
    LaunchedEffect(track.id) { vm.searchLyrics(query) }
    Dialog(onDismissRequest = close,properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(),color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                PageHeading(tr("Pilih lirik","Choose lyrics"),track.title,{ActionIcon(Icons.Rounded.Close,tr("Tutup pencarian lirik","Close lyrics search"),close)})
                OutlinedTextField(query,{query=it.take(200)},label={Text(tr("Judul dan artis","Title and artist"))},modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp),singleLine=true)
                FlowRow(Modifier.padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button({vm.searchLyrics(query)},enabled=!busy && query.length>=2) { Text(tr("Cari","Search")) }
                    OutlinedButton({importer.launch(arrayOf("text/plain","application/octet-stream","*/*"))}) { Icon(Icons.Rounded.FileOpen,null);Text(tr("Impor LRC","Import LRC")) }
                    TextButton({vm.resetLyrics(track);close()}) { Text(tr("Kembali otomatis","Use automatic lyrics")) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it,Modifier.padding(20.dp),color=MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
                    if (!busy && candidates.isEmpty()) item { EmptyState(tr("Belum ada hasil","No results"),tr("Coba judul singkat, ganti nama artis, atau impor LRC.","Try a shorter title, another artist name, or import LRC."),Icons.Rounded.Lyrics) }
                    items(candidates,key={it.id}) { candidate ->
                        ListItem(headlineContent={Text(candidate.title)},supportingContent={Text("${candidate.artist} · ${timeLabel(candidate.duration*1000)}")},trailingContent={Icon(Icons.Rounded.ChevronRight,null)},modifier=Modifier.clickable {vm.chooseLyrics(track,candidate);close()})
                    }
                }
            }
        }
    }
}

fun lyricStyle(trackId: String, preference: String): String = if (preference == "auto") LyricsVisuals.styles[trackId.hashCode().ushr(1)%LyricsVisuals.styles.size].first else preference

@Composable fun AnimatedLyricLine(text: String,active: Boolean,selected: Boolean,motion: Boolean,style: String,large: Boolean,onClick: () -> Unit) {
    val enabled=motion && LocalAppMotion.current
    val emphasis = remember { Animatable(0f) }
    LaunchedEffect(active,enabled,style) {
        val target = if (active) 1f else 0f
        if (enabled) emphasis.animateTo(target,tween(if (style == "glow") 450 else 380,easing=FastOutSlowInEasing)) else emphasis.snapTo(target)
    }
    val progress = emphasis.value
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    val color = androidx.compose.ui.graphics.lerp(inactive,MaterialTheme.colorScheme.onSurface,progress)
    val accent = MaterialTheme.colorScheme.primary
    val slideDistance = with(androidx.compose.ui.platform.LocalDensity.current) { (if (style == "slide") 12.dp else 4.dp).toPx() }
    val scale = 1f + progress * when(style){"zoom"->.065f;"focus"->.045f;"minimal"->0f;else->.02f}
    Row(Modifier.fillMaxWidth().semantics { this.selected = active; stateDescription = if (active) "Baris aktif" else "" }
        .background(if(style=="spotlight")MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha=progress*.6f)else Color.Transparent,MaterialTheme.shapes.medium)
        .clickable(onClickLabel=tr("Putar dari baris lirik","Seek to lyric"),onClick=onClick).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height((8+20*progress).dp).background(if (selected) accent else accent.copy(alpha=progress),androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
        Text((if(selected)"\u2713 " else "")+text,style=if(large)MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
            color=if(selected)accent else color,modifier=Modifier.weight(1f).padding(start=12.dp,end=24.dp).graphicsLayer {
                translationX=if(style=="minimal" || style=="rise")0f else slideDistance*progress;translationY=if(style=="rise")-slideDistance*2*progress else 0f;scaleX=scale;scaleY=scale;transformOrigin=TransformOrigin(0f,.5f)
            })
    }
}
