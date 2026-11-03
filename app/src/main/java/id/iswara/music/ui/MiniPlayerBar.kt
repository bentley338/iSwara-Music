package id.iswara.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun MiniPlayerBar(vm:MusicViewModel,open:()->Unit) {
    val state=vm.playback.state.collectAsStateWithLifecycle().value
    val settings=vm.settings.collectAsStateWithLifecycle().value
    val track=state.track ?: return
    Surface(onClick=open,color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(16.dp),modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp).fillMaxWidth()) {
        Column {
            Row(Modifier.padding(start=10.dp,top=8.dp,bottom=8.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically) {
                Art(track.artwork,track.title,Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                    Text(track.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(track.artist,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(state.loading)CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                ActionIcon(if(state.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,tr("Putar atau jeda","Play or pause"),vm.playback::toggle)
                ActionIcon(Icons.Rounded.SkipNext,tr("Lagu berikutnya","Next track"),vm.playback::next,enabled=state.roomRole!="member" && (state.canNext || state.roomRole==null && settings.autoplay && state.repeat==0 && !track.local))
            }
            LinearProgressIndicator(progress={if(state.duration>0)(state.position.toFloat()/state.duration).coerceIn(0f,1f)else 0f},modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    }
}
