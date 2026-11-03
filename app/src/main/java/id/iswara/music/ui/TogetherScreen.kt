package id.iswara.music.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.cloud.*
import id.iswara.music.model.Track
import kotlinx.coroutines.*

@Composable fun TogetherScreen(vm:MusicViewModel,close:()->Unit,onLogin:()->Unit) {
    val state=vm.together.state.collectAsStateWithLifecycle().value
    val account=vm.auth.account.collectAsStateWithLifecycle().value
    val player=vm.playback.state.collectAsStateWithLifecycle().value
    val lastRoom=vm.preferences.raw("lastTogether:${account?.id.orEmpty()}").collectAsStateWithLifecycle(initialValue=null).value
    val invite=vm.togetherInvite.collectAsStateWithLifecycle().value
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var title by remember {mutableStateOf("Denger bareng")}
    var code by remember(invite) {mutableStateOf(invite)}
    var query by remember {mutableStateOf("")}
    var results by remember {mutableStateOf<List<Track>>(emptyList())}
    var searching by remember {mutableStateOf(false)}
    var searchError by remember {mutableStateOf<String?>(null)}
    var searchJob by remember {mutableStateOf<Job?>(null)}
    var notice by remember {mutableStateOf<String?>(null)}
    var confirmLeave by remember {mutableStateOf(false)}
    LaunchedEffect(vm) {vm.together.messages.collect {notice=it}}
    val room=state.room
    val host=room?.host==account?.id && account!=null
    Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Groups,null,Modifier.padding(8.dp),tint=MaterialTheme.colorScheme.primary)
                    Text(tr("Denger bareng","Listen together"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    ActionIcon(Icons.Rounded.Close,tr("Tutup denger bareng","Close listen together"),close)
                }
                if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    state.error?.let {error -> item {Text(error,color=MaterialTheme.colorScheme.error)
                        if(vm.together.hasPending())TextButton(vm.together::retryEnqueue) {Text(tr("Ulangi penambahan","Retry adding tracks"))}
                        else if(room!=null)TextButton(vm.together::refresh) {Text(tr("Segarkan room","Refresh room"))}
                    }}
                    notice?.let {item {Text(it,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium)}}
                    if(account==null) item {EmptyState(tr("Masuk untuk denger bareng","Sign in to listen together"),tr("Gunakan akun dengan email yang sudah terverifikasi.","Use an account with a verified email."),Icons.Rounded.Person,tr("Buka akun","Open account"),onLogin)}
                    else if(room==null) {
                        item {Text(tr("Bagikan room, lalu dengarkan bersama.","Share a room and listen together."),style=MaterialTheme.typography.headlineSmall)
                            Text(tr("Host mengatur pemutaran. Semua anggota bisa menambah lagu streaming.","The host controls playback. Every member can add streaming tracks."),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        item {OutlinedTextField(title,{title=it.take(80)},label={Text(tr("Nama room","Room name"))},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
                            Button({vm.together.create(title)},enabled=!state.busy && title.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {Text(tr("Buat room dari antreanku","Create room from my queue"))}}
                        if(!lastRoom.isNullOrBlank()) item {TextButton({vm.together.join(lastRoom)},enabled=!state.busy) {Text(tr("Gabung room terakhir","Rejoin last room"))}}
                        item {HorizontalDivider();Spacer(Modifier.height(12.dp));OutlinedTextField(code,{code=TogetherRules.code(it)},label={Text(tr("Kode atau link room","Room code or link"))},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
                            OutlinedButton({vm.together.join(code)},enabled=!state.busy && code.length==12,modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {Text(tr("Gabung room","Join room"))}}
                        item {Text(tr("Maksimal 32 akun per room, berlaku 6 jam. Selisih jaringan bisa memengaruhi sinkronisasi.","Up to 32 accounts per room, valid for 6 hours. Network delays can affect synchronization."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    } else {
                        item {Text(room.title,style=MaterialTheme.typography.headlineMedium)
                            Text("${room.members} ${tr("anggota","members")} \u00b7 ${if(host) tr("Kamu host","You are the host") else tr("Mengikuti host","Following host")}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(room.code.chunked(4).joinToString("-"),style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=8.dp))
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("iSwara Music room",room.code));notice="Kode room disalin"}) {Icon(Icons.Rounded.ContentCopy,null);Text(tr("Salin kode","Copy code"))}
                                Button({context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Denger bareng di iSwara Music\niswara://room/${room.code}"),null))}) {Icon(Icons.Rounded.Share,null);Text(tr("Undang teman","Invite friends"))}
                            }
                            Text(if(state.realtime) tr("Terhubung realtime","Connected in realtime") else tr("Menghubungkan ulang; kontrol bisa terlambat.","Reconnecting; controls may be delayed."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(state.waitingHost)item {Text(tr("Host terputus. Musik dijeda sambil menunggu host kembali.","The host disconnected. Playback is paused while waiting."),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        if(state.localPaused)item {Text(tr("Dijeda di perangkat ini. Musik teman tetap berjalan.","Paused on this device. Others keep listening."),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        item {
                            player.track?.let {track -> Row(verticalAlignment=Alignment.CenterVertically) {
                                Art(track.artwork,track.title,Modifier.size(64.dp));Column(Modifier.weight(1f).padding(start=12.dp)) {Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium);Text(track.artist,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            }}
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                                if(host)ActionIcon(Icons.Rounded.SkipPrevious,tr("Lagu sebelumnya","Previous track"),vm.playback::previous,enabled=room.tracks.isNotEmpty())
                                FilledTonalButton({if(host)vm.playback.toggle() else vm.together.toggleLocal()},enabled=room.tracks.isNotEmpty() && (host || room.playing || state.localPaused)) {
                                    Icon(if(player.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null);Text(if(host)tr("Putar / jeda room","Play / pause room") else if(state.localPaused)tr("Lanjut ikuti host","Resume following host") else tr("Jeda perangkat ini","Pause this device"))
                                }
                                if(host)ActionIcon(Icons.Rounded.SkipNext,tr("Lagu berikutnya","Next track"),vm.playback::next,enabled=room.index<room.tracks.lastIndex)
                            }
                            Text("${timeLabel(player.position)} / ${timeLabel(player.duration)}",style=MaterialTheme.typography.bodySmall)
                        }
                        item {Text(tr("Tambah lagu bersama","Add tracks together"),style=MaterialTheme.typography.titleLarge)
                            OutlinedTextField(query,{query=it.take(120)},label={Text(tr("Judul atau artis","Title or artist"))},singleLine=true,modifier=Modifier.fillMaxWidth())
                            TextButton({searchJob?.cancel();searching=true;searchError=null;searchJob=scope.launch {try{results=vm.roomSearch(query)}catch(e:CancellationException){throw e}catch(_:Exception){searchError="Pencarian belum tersambung."}finally{searching=false}}},enabled=query.isNotBlank() && !searching) {Icon(Icons.Rounded.Search,null);Text(tr("Cari lagu untuk room","Search tracks for the room"))}
                            if(searching)LinearProgressIndicator(Modifier.fillMaxWidth())
                            searchError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                        }
                        items(results,key={"result:${it.id}"}) {track -> ListItem(headlineContent={Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis)},supportingContent={Text(track.artist,maxLines=1)},leadingContent={Art(track.artwork,track.title,Modifier.size(48.dp))},trailingContent={IconButton({vm.together.enqueue(listOf(track))}){Icon(Icons.Rounded.Add,tr("Tambah lagu ke room","Add track to room"))}})}
                        item {HorizontalDivider();Text("${room.tracks.size} ${tr("lagu di antrean room","tracks in room queue")}",style=MaterialTheme.typography.titleLarge)
                            if(host && player.undo!=null)TextButton(vm.playback::undo) {Text(tr("Urungkan perubahan antrean","Undo queue edit"))}}
                        itemsIndexed(room.tracks,key={index,t->"queue:$index:${t.id}"}) {index,track -> ListItem(headlineContent={Text(track.title,maxLines=2,overflow=TextOverflow.Ellipsis,color=if(index==room.index)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)},supportingContent={Text(track.artist,maxLines=1)},leadingContent={Art(track.artwork,track.title,Modifier.size(48.dp))},trailingContent={if(host)Row {ActionIcon(Icons.Rounded.PlayArrow,tr("Putar lagu di room","Play track in room"),{vm.playback.jump(index)});ActionIcon(Icons.Rounded.RemoveCircleOutline,tr("Hapus lagu dari room","Remove track from room"),{vm.playback.remove(index)})}})}
                        item {OutlinedButton({confirmLeave=true},Modifier.fillMaxWidth()) {Text(if(host)tr("Akhiri room","End room") else tr("Keluar dari room","Leave room"))} }
                    }
                }
            }
        }
    }
    if(confirmLeave)ConfirmDialog(if(host)tr("Akhiri room?","End room?") else tr("Keluar dari room?","Leave room?"),if(host)tr("Room ditutup untuk semua anggota. Antrean pribadimu dipulihkan dalam keadaan jeda.","The room closes for everyone. Your personal queue is restored paused.") else tr("Antrean pribadimu dipulihkan dalam keadaan jeda.","Your personal queue is restored paused."),{confirmLeave=false}) {confirmLeave=false;vm.together.leave()}
}
