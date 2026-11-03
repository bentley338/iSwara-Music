package id.iswara.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.playback.QueueRules

@Composable fun SmartQueueScreen(vm:MusicViewModel,close:()->Unit) {
    val preview=vm.queuePreview.collectAsStateWithLifecycle().value
    val player=vm.screenPlayback.collectAsStateWithLifecycle().value
    val owner=vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    val settings=vm.settings.collectAsStateWithLifecycle().value
    var selected by remember(preview.tracks){mutableStateOf(preview.tracks.take(10).map {it.id}.toSet())}
    var applied by remember(preview.tracks){mutableStateOf(false)}
    LaunchedEffect(owner){if(preview.owner.isNotBlank() && preview.owner!=owner)close()}
    fun add(next:Boolean) {if(preview.owner!=owner || player.roomRole!=null)return;val existing=player.queue.map {it.id}.toSet();val tracks=preview.tracks.filter {it.id in selected && it.id !in existing};(if(next)tracks.asReversed()else tracks).forEach {vm.playback.enqueue(it,next)};applied=true}
    Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {Surface(Modifier.fillMaxSize()) {LazyColumn(Modifier.safeDrawingPadding(),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item{PageHeading(tr("Kurasi antrean","Queue curator"),player.track?.title ?: tr("Mulai dari lagu yang kamu suka.","Start with a song you like.")){ActionIcon(Icons.Rounded.Close,tr("Tutup kurasi antrean","Close queue curator"),close)}}
        item{Text(tr("Peringkat memakai lagu terkait, riwayat, dan favorit. Artis dibuat lebih beragam; urutan yang sudah kamu susun tetap utuh.","Ranking uses related tracks, listening history and favorites. It varies artists and keeps your existing order intact."),Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodyMedium)}
        item{SettingChoice(tr("Arah rekomendasi","Recommendation style"),settings.radioMode,QueueRules.modes){vm.action {vm.preferences.set("radioMode",it)}}}
        item{Row(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(vm::previewQueue,enabled=player.track!=null && !preview.loading){Text(tr("Susun rekomendasi","Find recommendations"))};if(preview.loading)TextButton(vm::cancelQueuePreview){Text(tr("Batal","Cancel"))}}}
        if(preview.loading)item{LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=20.dp))}
        preview.error?.let {item{Text(it,Modifier.padding(20.dp),color=MaterialTheme.colorScheme.error)}}
        if(preview.tracks.isNotEmpty()) {
            item{Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${selected.size} ${tr("dipilih","selected")}")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton({selected=preview.tracks.map {it.id}.toSet()}){Text(tr("Pilih semua","Select all"))};TextButton({selected=emptySet()}){Text(tr("Kosongkan pilihan","Clear selection"))}}
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({add(false)},enabled=selected.isNotEmpty() && !applied && player.roomRole==null){Text(tr("Tambahkan di akhir","Append to queue"))};OutlinedButton({add(true)},enabled=selected.isNotEmpty() && !applied && player.roomRole==null){Text(tr("Putar berikutnya","Play next"))}}
                if(applied)Text(tr("Pilihan masuk antrean.","Your picks are queued."))
            }}
            items(preview.tracks,key={it.id}) {track->ListItem(headlineContent={Text(track.title)},supportingContent={Text(track.artist)},leadingContent={Checkbox(track.id in selected,{selected=if(it)selected+track.id else selected-track.id})},trailingContent={Art(track.artwork,track.title,Modifier.size(48.dp))})}
        }
        if(!preview.loading && preview.seed.isNotBlank() && preview.tracks.isEmpty() && preview.error==null)item{EmptyState(tr("Belum ada pilihan baru","No fresh picks yet"),tr("Coba lagu lain atau ubah arah rekomendasi.","Try another seed or recommendation style."),Icons.Rounded.GraphicEq)}
    }}}
}
