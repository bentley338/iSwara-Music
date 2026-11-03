package id.iswara.music.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.model.Track
import kotlinx.coroutines.*

@Composable fun PlaylistImportScreen(vm:MusicViewModel,close:()->Unit) {
    val state=vm.playlistImporter.state.collectAsStateWithLifecycle().value
    val owner=vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val openingOwner=remember {owner}
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var input by remember {mutableStateOf("")};var link by remember {mutableStateOf(true)};var limit by remember {mutableStateOf("100")}
    var fileInput by remember {mutableStateOf<String?>(null)}
    var title by remember {mutableStateOf("Playlist impor")};var error by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    var choosing by remember {mutableStateOf<Int?>(null)};var query by remember {mutableStateOf("")};var results by remember {mutableStateOf<List<Track>>(emptyList())}
    LaunchedEffect(owner){if(owner!=openingOwner)close()}
    LaunchedEffect(state.title){if(state.title.isNotBlank())title=state.title}
    val file=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch {try {
        val raw=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use {stream->val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=stream.read(buffer);if(n<0)break;require(output.size()+n<=2_000_000){"Batas berkas 2 MB."};output.write(buffer,0,n)};output.toString("UTF-8")} ?: error("Berkas tidak dapat dibaca.")};fileInput=raw;input=raw.take(20000);link=false;error=null
    }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "Berkas belum dapat dibaca."}}}
    Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {Surface(Modifier.fillMaxSize()) {Column(Modifier.safeDrawingPadding()) {
        PageHeading(tr("Impor playlist","Import playlists"),tr("Cek kecocokan sebelum masuk koleksi.","Review matches before saving.")){ActionIcon(Icons.Rounded.Close,tr("Tutup impor playlist","Close playlist import"),close)}
        if(choosing!=null) {
            val index=choosing!!;val row=state.rows.getOrNull(index)
            OutlinedTextField(query,{query=it.take(200)},label={Text(tr("Cari pengganti","Find a match"))},modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))
            Row(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button({busy=true;scope.launch {try{results=vm.playlistImporter.search(query);error=null}catch(e:CancellationException){throw e}catch(_:Exception){error="Pencarian belum berhasil."}finally{busy=false}}},enabled=!busy && query.isNotBlank()){Text(tr("Cari","Search"))}
                OutlinedButton({choosing=null;error=null}){Text(tr("Kembali","Back"))}
            }
            error?.let {Text(it,Modifier.padding(20.dp),color=MaterialTheme.colorScheme.error)}
            LazyColumn(Modifier.weight(1f)){item{Text("${row?.song?.title.orEmpty()} · ${row?.song?.artist.orEmpty()}",Modifier.padding(20.dp))};items(results,key={it.id}){track->TrackRow(track,{vm.playlistImporter.choose(index,track);choosing=null},{vm.playlistImporter.choose(index,track);choosing=null})}}
        } else LazyColumn(Modifier.weight(1f).testTag("playlist-import-list"),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(state.rows.isEmpty()) {
                item{Row(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(link,{link=true},{Text(tr("Tautan publik","Public link"))});FilterChip(!link,{link=false},{Text(tr("Teks / berkas","Text / file"))})}}
                item{OutlinedTextField(input,{input=it.take(20000);fileInput=null},label={Text(if(link)tr("Tempel tautan playlist","Paste playlist link")else tr("Artis - Judul, satu lagu per baris","Artist - Title, one track per line"))},modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp),minLines=if(link)2 else 6,maxLines=8)}
                if(fileInput!=null)item{Text(tr("Berkas dimuat utuh; pratinjau teks maksimal 20.000 karakter.","The full file is loaded; text preview is limited to 20,000 characters."),Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodySmall)}
                item{OutlinedButton({file.launch(arrayOf("text/*","application/json","application/octet-stream","audio/x-mpegurl","*/*"))},Modifier.fillMaxWidth().padding(horizontal=20.dp)){Icon(Icons.Rounded.FileOpen,null);Spacer(Modifier.width(8.dp));Text(tr("Pilih CSV, JSON, M3U atau TXT","Choose CSV, JSON, M3U or TXT"))}}
                item{SettingChoice(tr("Maksimal lagu per impor","Tracks per import"),limit,listOf("50","100","250","500").map {it to it}){limit=it}}
                item{Text(tr("Tautan publik: playlist iSwara Music, Spotify, SoundCloud, dan Deezer. Untuk platform lain atau daftar privat, gunakan ekspor CSV/JSON/M3U atau teks. Hanya lagu yang dipilih dan cocok yang disimpan.","Public links: iSwara Music, Spotify, SoundCloud and Deezer playlists. For other services or private lists, use CSV/JSON/M3U or text. Only selected matches are saved."),Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodySmall)}
                item{Button({vm.playlistImporter.preview(fileInput ?: input,link,limit.toInt());error=null},enabled=input.isNotBlank() && !state.loading,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp)){Text(tr("Baca & cocokkan lagu","Read & match tracks"))}}
            } else {
                item{OutlinedTextField(title,{title=it.take(120)},label={Text(tr("Nama playlist baru","New playlist name"))},modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))}
                item{Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("${state.rows.count {it.selected}} ${tr("dipilih","selected")} · ${state.completed}/${state.rows.size} ${tr("diperiksa","checked")}")
                    if(state.warning.isNotBlank())Text(state.warning,style=MaterialTheme.typography.bodySmall)
                    if(state.matching){LinearProgressIndicator(progress={state.completed.toFloat()/state.rows.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth());TextButton(vm.playlistImporter::cancel){Text(tr("Hentikan pencocokan","Stop matching"))}}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({vm.playlistImporter.clear()}){Text(tr("Impor lain","Another import"))};Button({busy=true;scope.launch {try{vm.playlistImporter.save(title);error=null}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}},enabled=!state.matching && !busy && state.saved==null && state.rows.any {it.selected}){Text(tr("Simpan pilihan","Save selected"))}}
                    if(state.saved!=null)Text(tr("Tersimpan: ","Saved: ")+state.saved)
                }}
                itemsIndexed(state.rows,key={i,_->i}) {index,row->ListItem(headlineContent={Text(row.song.title)},supportingContent={Column {Text(row.song.artist);Text(row.chosen?.let {"→ ${it.title} · ${it.artist}"} ?: if(row.done)tr("Perlu diperiksa","Needs review")else tr("Menunggu pencocokan","Waiting for match"),style=MaterialTheme.typography.bodySmall)}},leadingContent={Checkbox(row.selected,{vm.playlistImporter.select(index,it)},enabled=row.chosen!=null)},trailingContent={ActionIcon(Icons.Rounded.ManageSearch,tr("Periksa kecocokan","Review match"),{choosing=index;query="${row.song.artist} ${row.song.title}";results=row.candidates},enabled=row.done)})}
            }
            if(state.loading)item{LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))}
            state.error?.let {item{Text(it,Modifier.padding(20.dp),color=MaterialTheme.colorScheme.error)}}
            error?.let {item{Text(it,Modifier.padding(20.dp),color=MaterialTheme.colorScheme.error)}}
        }
    }}}
}
