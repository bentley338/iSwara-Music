package id.iswara.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.model.Track
import id.iswara.music.source.SourceCandidate
import kotlinx.coroutines.*

@Composable fun SourcePicker(vm:MusicViewModel,track:Track,close:()->Unit) {
    val owner=vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val substitutions=vm.resilient.substitutions.collectAsStateWithLifecycle().value
    val originalOwner=remember {owner}
    if(owner!=originalOwner){LaunchedEffect(owner){close()};return}
    val scope=rememberCoroutineScope()
    var query by remember {mutableStateOf("${track.artist} ${track.title}".trim())}
    var link by remember {mutableStateOf("")}
    var results by remember {mutableStateOf<List<SourceCandidate>>(emptyList())}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var searched by remember {mutableStateOf(false)}
    var chosen by remember {mutableStateOf<Track?>(null)}
    var searchJob by remember {mutableStateOf<Job?>(null)}
    var generation by remember {mutableIntStateOf(0)}
    fun search(){searchJob?.cancel();val request=++generation;busy=true;error=null;searched=true;searchJob=scope.launch {
        try {results=vm.resilient.candidates(track,query)}catch(e:CancellationException){throw e}catch(_:Exception){error="Pencarian sumber belum tersambung."}finally {if(request==generation)busy=false}
    }}
    Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp).heightIn(max=820.dp),shape=MaterialTheme.shapes.extraLarge) {
            Column {
                PageHeading(tr("Sumber audio","Audio sources"),tr("Koleksimu tetap memakai identitas lagu asli.","Your library keeps the original track identity.")){ActionIcon(Icons.Rounded.Close,tr("Tutup sumber audio","Close audio sources"),close)}
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    item {Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Art(track.artwork,track.title,Modifier.size(64.dp));Column(Modifier.weight(1f)){Text(track.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);Text(track.artist,style=MaterialTheme.typography.bodySmall);Text(timeLabel(track.duration*1000),style=MaterialTheme.typography.labelSmall)}}}
                    substitutions[track.id]?.let {active->item {Text(tr("Cadangan aktif: ","Active alternative: ")+active.track.title+" · "+active.track.artist,Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}}
                    item {OutlinedTextField(query,{query=it.take(180)},label={Text(tr("Judul dan artis untuk dicocokkan","Title and artist to match"))},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))}
                    item {Button({search()},enabled=query.isNotBlank() && !busy,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp)){Text(tr("Cari sumber cadangan","Find alternative sources"))}}
                    item {Text(tr("Pilihan otomatis harus cocok judul, artis, versi, dan durasi. Kamu dapat memilih versi lain sendiri. Sumber yang sedang dibatasi mungkin tetap tidak tersedia.","Automatic choices must match title, artist, version, and duration. You can choose another version manually. Restricted sources may still be unavailable."),Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=20.dp))}
                    error?.let {item{Text(it,Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.error)}}
                    if(searched && !busy && results.isEmpty())item{EmptyState(tr("Belum ada kecocokan","No matches found"),tr("Periksa koneksi, gunakan judul lain, atau masukkan tautan.","Check your connection, try another title, or enter a link."),Icons.Rounded.ManageSearch)}
                    items(results,key={it.track.url}) {candidate->ListItem(headlineContent={Text(candidate.track.title,maxLines=2,overflow=TextOverflow.Ellipsis)},supportingContent={Column{Text(candidate.track.artist,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${timeLabel(candidate.track.duration*1000)} · ${if(candidate.confidence>=.88)tr("Kecocokan kuat","Strong match")else tr("Bandingkan versi","Compare versions")}",style=MaterialTheme.typography.labelSmall)}},leadingContent={Art(candidate.track.artwork,candidate.track.title,Modifier.size(52.dp))},trailingContent={ActionIcon(Icons.Rounded.CheckCircleOutline,tr("Pilih sumber ini","Choose this source"),{chosen=candidate.track},enabled=!busy)})}
                    item {SectionTitle(tr("Punya tautan pengganti?","Have another link?"))}
                    item {OutlinedTextField(link,{link=it.take(2000)},label={Text(tr("Tautan sumber","Source link"))},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))}
                    item {OutlinedButton({busy=true;error=null;scope.launch {try{chosen=vm.resilient.candidateFromUrl(link.trim())}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "Tautan belum bisa dibuka."}finally{busy=false}}},enabled=link.isNotBlank() && !busy,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp)){Text(tr("Periksa tautan","Check link"))}}
                    item {TextButton({busy=true;scope.launch {try{vm.resilient.original(track);vm.playback.reloadSource(track.id);close()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}},enabled=!busy,modifier=Modifier.padding(horizontal=12.dp)){Text(tr("Kembalikan sumber asli","Restore original source"))}}
                }
            }
        }
    }
    chosen?.let {candidate->AlertDialog(onDismissRequest={if(!busy)chosen=null},title={Text(tr("Gunakan sumber ini?","Use this source?"))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(candidate.title,style=MaterialTheme.typography.titleMedium);Text(candidate.artist);Text(timeLabel(candidate.duration*1000));Text(tr("Pilihan ini tersimpan untuk akun ini di perangkat ini. Playlist dan favorit tetap memakai lagu aslinya.","This choice is saved for this account on this device. Playlists and favorites keep the original track."),style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton({busy=true;error=null;scope.launch {try{vm.resilient.choose(track,candidate);vm.playback.reloadSource(track.id);chosen=null;close()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message;chosen=null}finally{busy=false}}},enabled=!busy){Text(tr("Gunakan sumber","Use source"))}},dismissButton={TextButton({chosen=null},enabled=!busy){Text(tr("Batal","Cancel"))}})}
}
