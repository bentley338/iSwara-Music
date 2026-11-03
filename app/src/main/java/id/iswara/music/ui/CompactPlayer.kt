package id.iswara.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.model.Track
import id.iswara.music.playback.PlaybackState

@Composable fun CompactPlayer(vm:MusicViewModel,player:PlaybackState,track:Track,onMenu:()->Unit,onEqualizer:()->Unit,onStudio:()->Unit,onTimer:()->Unit,onCar:()->Unit) {
    val settings=vm.settings.collectAsStateWithLifecycle().value
    val library=vm.library.collectAsStateWithLifecycle().value
    val stem=vm.stems.selection.collectAsStateWithLifecycle().value
    val substitutions=vm.resilient.substitutions.collectAsStateWithLifecycle().value
    val alternative=substitutions[track.id]
    val member=player.roomRole=="member"
    val context=LocalContext.current
    var details by remember {mutableStateOf(false)}
    var slider by remember(track.id) {mutableStateOf<Float?>(null)}
    BoxWithConstraints(Modifier.fillMaxSize().testTag("compact-player")) {
        val short=maxHeight<420.dp || LocalDensity.current.fontScale>1.3f && maxHeight<540.dp
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(if(short)2.dp else 6.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val side=minOf(maxWidth*.82f,maxHeight,360.dp)
                    if(side>72.dp)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Art(track.artwork,track.title,Modifier.size(side))}
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title,style=if(short)MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,minLines=if(short)1 else 2,maxLines=if(short)1 else 2,overflow=TextOverflow.Ellipsis)
                    Text(track.artist+if(alternative!=null)" · ${tr("Cadangan iSwara","iSwara alternative")}"else "",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(stem.track==track.id && stem.mode!="original")TextButton(onStudio,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=32.dp)){Text(if(stem.mode=="vocal")tr("Vokal saja","Vocals only")else tr("Tanpa vokal","No vocals"),style=MaterialTheme.typography.labelSmall)}
                }
                val liked=library.favorites.any {it.id==track.id}
                ActionIcon(if(liked)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,tr("Simpan atau hapus favorit","Toggle favorite"),{vm.favorite(track)},active=liked)
            }
            Column {
                Slider((slider ?: player.position.toFloat()).coerceIn(0f,player.duration.coerceAtLeast(1).toFloat()),{slider=it},onValueChangeFinished={slider?.let {vm.playback.seek(it.toLong())};slider=null},valueRange=0f..player.duration.coerceAtLeast(1).toFloat(),enabled=player.duration>0 && !member,modifier=Modifier.fillMaxWidth().height(40.dp).semantics {contentDescription="Posisi pemutaran"})
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(timeLabel(slider?.toLong() ?: player.position),style=MaterialTheme.typography.labelSmall);Text(timeLabel(player.duration),style=MaterialTheme.typography.labelSmall)}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                ActionIcon(Icons.Rounded.Shuffle,tr("Acak","Shuffle"),vm.playback::shuffle,active=player.shuffle,enabled=player.roomRole==null)
                ActionIcon(Icons.Rounded.SkipPrevious,tr("Lagu sebelumnya","Previous track"),vm.playback::previous,enabled=!member)
                FilledIconButton(vm.playback::toggle,Modifier.size(if(short)56.dp else 68.dp).semantics {contentDescription="Putar atau jeda"},shape=CircleShape) {
                    if(player.loading)CircularProgressIndicator(Modifier.size(24.dp),color=MaterialTheme.colorScheme.onPrimary,strokeWidth=3.dp)else Icon(if(player.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,Modifier.size(36.dp))
                }
                ActionIcon(Icons.Rounded.SkipNext,tr("Lagu berikutnya","Next track"),vm.playback::next,enabled=!member && (player.canNext || player.roomRole==null && settings.autoplay && player.repeat==0 && !track.local))
                ActionIcon(if(player.repeat==1)Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,tr("Ulang: mati, semua, atau satu","Repeat: off, all, or one"),vm.playback::repeat,active=player.repeat!=0,enabled=player.roomRole==null)
            }
            if(!short)Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){RemixChips(vm)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                Column(horizontalAlignment=Alignment.CenterHorizontally){ActionIcon(Icons.Rounded.Timer,tr("Timer tidur","Sleep timer"),onTimer,active=player.timerEnds!=null || player.sleepAtEnd);Text(tr("Timer","Timer"),style=MaterialTheme.typography.labelSmall)}
                Column(horizontalAlignment=Alignment.CenterHorizontally){ActionIcon(Icons.Rounded.Equalizer,"Equalizer",onEqualizer);Text("EQ",style=MaterialTheme.typography.labelSmall)}
                Column(horizontalAlignment=Alignment.CenterHorizontally){ActionIcon(Icons.Rounded.Tune,tr("Studio suara & karaoke","Sound studio & karaoke"),onStudio,active=settings.remix!="off" || settings.pitch!="0" || settings.warmth>0 || settings.vinyl>0 || settings.spatial>0);Text("Studio",style=MaterialTheme.typography.labelSmall)}
                Column(horizontalAlignment=Alignment.CenterHorizontally){ActionIcon(Icons.Rounded.DirectionsCar,tr("Mode berkendara","Car mode"),onCar);Text(tr("Mobil","Car"),style=MaterialTheme.typography.labelSmall)}
            }
            if(player.error!=null)TextButton({details=true},Modifier.fillMaxWidth()){Icon(Icons.Rounded.Info,null,Modifier.size(18.dp));Text(tr("Pemutaran belum berhasil · Detail","Playback unsuccessful · Details"),maxLines=1,overflow=TextOverflow.Ellipsis)}
            else if(player.pauseReason!=null && !player.playing)Text(player.pauseReason,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
        }
    }
    if(details)AlertDialog(onDismissRequest={details=false},title={Text(tr("Pemutaran belum berhasil","Playback unsuccessful"))},text={Text(player.error.orEmpty())},confirmButton={TextButton({details=false;vm.playback.toggle()}){Text(tr("Coba putar lagi","Retry playback"))}},dismissButton={Row {
        player.errorDetails?.let {report->TextButton({context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("iSwara",report))}){Text(tr("Salin detail","Copy details"))}}
        TextButton({details=false}){Text(tr("Tutup","Close"))}
    }})
}
