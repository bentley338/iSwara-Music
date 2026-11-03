package id.iswara.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable fun RemixChips(vm:MusicViewModel) {
    val settings=vm.settings.collectAsStateWithLifecycle().value
    val player=vm.screenPlayback.collectAsStateWithLifecycle().value
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        id.iswara.music.playback.RemixRules.presets.filter {it.id!="off"}.forEach {preset->
            FilterChip(settings.remix==preset.id,{vm.action {vm.preferences.remix(if(settings.remix==preset.id)"off" else preset.id)}},{Text(preset.title)},enabled=player.roomRole==null)
        }
    }
}

@Composable fun SoundStudio(vm:MusicViewModel,close:()->Unit) {
    val player=vm.playback.state.collectAsStateWithLifecycle().value
    val settings=vm.settings.collectAsStateWithLifecycle().value
    val jobs=vm.stems.jobs.collectAsStateWithLifecycle().value
    val entries=vm.stems.entries.collectAsStateWithLifecycle().value
    val notice=vm.stems.notice.collectAsStateWithLifecycle().value
    val selection=vm.stems.selection.collectAsStateWithLifecycle().value
    val track=player.track
    val current=jobs.lastOrNull {it.track==track?.id && it.running} ?: jobs.lastOrNull {it.track==track?.id}
    val working=jobs.any {it.running}
    val result=entries.find {it.track.id==track?.id}
    val mode=selection.takeIf {it.track==track?.id}?.mode ?: "original"
    val room=player.roomRole!=null
    var pitchDrag by remember {mutableStateOf<Float?>(null)}
    val pitch=settings.pitch.toIntOrNull()?.coerceIn(-12,12) ?: 0
    var confirmProcess by remember {mutableStateOf(false)}
    var confirmDelete by remember {mutableStateOf(false)}
    Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.widthIn(max=720.dp).fillMaxWidth().padding(12.dp).heightIn(max=820.dp),shape=MaterialTheme.shapes.extraLarge) {
            Column {
            PageHeading(tr("Studio suara","Sound studio"),tr("Remix dan karaoke, caramu sendiri.","Remix and karaoke, your way."),{ActionIcon(Icons.Rounded.Close,tr("Tutup studio suara","Close sound studio"),close)})
            LazyColumn(Modifier.weight(1f).testTag("studio-list"),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(track!=null)item {Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Art(track.artwork,track.title,Modifier.size(64.dp));Column(Modifier.weight(1f)){Text(track.title,style=MaterialTheme.typography.titleMedium,maxLines=2);Text(track.artist,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    ActionIcon(if(player.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,tr("Putar atau jeda","Play or pause"),vm.playback::toggle)
                }}
                if(room)item {Text(tr("Keluar room untuk memakai remix dan hasil AI.","Leave the room to use remix and AI audio."),Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.primary)}
                item {SectionTitle("Instant Remix")}
                id.iswara.music.playback.RemixRules.presets.forEach {preset->item {PresetOption(preset.title,preset.description,settings.remix==preset.id,!room){vm.action {vm.preferences.remix(if(settings.remix==preset.id)"off" else preset.id)}}}}
                item {SectionTitle(tr("Nada dasar","Pitch"))}
                item {Column(Modifier.padding(horizontal=20.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        ActionIcon(Icons.Rounded.Remove,tr("Turunkan nada","Lower pitch"),{vm.action {vm.preferences.adjustPitch(-1)}},enabled=pitch> -12 && !room)
                        val display=pitchDrag?.roundToInt() ?: pitch
                        Text("${if(display>0)"+" else ""}$display semitone",style=MaterialTheme.typography.titleLarge)
                        ActionIcon(Icons.Rounded.Add,tr("Naikkan nada","Raise pitch"),{vm.action {vm.preferences.adjustPitch(1)}},enabled=pitch<12 && !room)
                    }
                    Slider(pitchDrag ?: pitch.toFloat(),{pitchDrag=it},onValueChangeFinished={val chosen=pitchDrag?.roundToInt() ?: pitch;vm.action {vm.preferences.pitch(chosen)};pitchDrag=null},valueRange=-12f..12f,steps=23,enabled=!room,modifier=Modifier.semantics {contentDescription="Nada dasar"})
                    Text(tr("Tempo tetap. Nilai ini ditambahkan ke nada preset.","Tempo stays the same. This adds to the preset's pitch."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({vm.action {vm.preferences.pitch(0)}},enabled=!room){Text(tr("Reset nada","Reset pitch"))}
                }}
                item {AnalogControls(vm)}
                item {SectionTitle(tr("Karaoke AI","AI karaoke"))}
                item {Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(tr("Pisahkan vokal dan instrumen di perangkat ini.","Separate vocals and instruments on this device."),style=MaterialTheme.typography.bodyMedium)
                    if(track==null)Text(tr("Putar lagu untuk mulai.","Play a song to begin."))
                    if(current?.running==true){
                        Text(current.stage);LinearProgressIndicator(progress={current.percent/100f},modifier=Modifier.fillMaxWidth())
                        TextButton({track?.let {vm.stems.cancel(it.id)}}){Text(tr("Batalkan proses","Cancel processing"))}
                    } else if(working)Text(tr("Lagu lain sedang diproses. Hasil akan tersedia setelah selesai.","Another song is being processed. Its result will be available when finished."))
                    notice?.takeIf {it.first==track?.id}?.let {Text(it.second,color=MaterialTheme.colorScheme.error)}
                    if(current?.error!=null)Text(current.error,color=MaterialTheme.colorScheme.error)
                    if(result!=null) {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("original" to tr("Asli","Original"),"instrumental" to tr("Tanpa vokal","No vocals"),"vocal" to tr("Vokal saja","Vocals only")).forEach {(key,label)->FilterChip(mode==key,{vm.action {track?.let {vm.stems.select(it.id,key)}}},{Text(label)},enabled=!room)}
                        }
                        Text("${timeLabel(result.duration)} · ${result.bytes/1024/1024} MB",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tr("Sedikit vokal atau instrumen bisa masih terdengar.","Some vocal or instrumental bleed may remain."),style=MaterialTheme.typography.bodySmall)
                    }
                    Button({confirmProcess=true},enabled=track!=null && !working,modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.MicOff,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(result==null)tr("Pisahkan vokal & musik","Separate vocals & music") else tr("Proses ulang","Process again"))
                    }
                    if(result!=null)TextButton({confirmDelete=true},enabled=!working){Text(tr("Hapus hasil AI lagu ini","Delete this song's AI audio"))}
                    TextButton({vm.action {vm.preferences.resetRemix();vm.preferences.resetAnalog();vm.stems.original()}},enabled=!room){Text(tr("Reset studio","Reset studio"))}
                }}
            }
            }
        }
    }
    if(confirmProcess && track!=null)AlertDialog(onDismissRequest={confirmProcess=false},title={Text(tr("Pisahkan lagu ini?","Separate this song?"))},text={Text(tr("Audio diproses di HP dan tidak diunggah. Proses bisa memerlukan waktu serta mengunduh audio jika belum tersedia. Maksimal 8 menit per lagu; sediakan 350 MB ruang kosong. Musik asli tetap berjalan.","Audio is processed on your phone without uploading. It can take time and may download the audio. Up to 8 minutes per song; keep 350 MB free. Original playback continues."))},confirmButton={TextButton({vm.action {vm.stems.enqueue(track)};confirmProcess=false}){Text(tr("Mulai proses","Start processing"))}},dismissButton={TextButton({confirmProcess=false}){Text(tr("Batal","Cancel"))}})
    if(confirmDelete && track!=null)AlertDialog(onDismissRequest={confirmDelete=false},title={Text(tr("Hapus hasil AI?","Delete AI audio?"))},text={Text(tr("Versi vokal dan instrumen lagu ini akan dihapus dari perangkat.","This song's vocal and instrumental versions will be removed from the device."))},confirmButton={TextButton({vm.action {vm.stems.remove(track.id)};confirmDelete=false}){Text(tr("Hapus","Delete"))}},dismissButton={TextButton({confirmDelete=false}){Text(tr("Batal","Cancel"))}})
}

@Composable fun AnalogControls(vm:MusicViewModel) {
    val settings=vm.settings.collectAsStateWithLifecycle().value
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SectionTitle(tr("Analog & ruang","Analog & space"))
        Text(tr("Warna tabung, tekstur vinyl, dan ruang stereo untuk headphone.","Tube coloration, vinyl texture, and stereo space for headphones."),Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.bodyMedium)
        listOf(Triple("warmth",tr("Kehangatan tabung","Tube warmth"),settings.warmth),Triple("vinyl",tr("Tekstur vinyl","Vinyl texture"),settings.vinyl),Triple("spatial",tr("Ruang headphone","Headphone space"),settings.spatial)).forEach {(key,label,current)->
            SettingChoice(label,current.toString(),listOf(0,25,50,75,100).map {it.toString() to if(it==0)tr("Mati","Off") else "$it%"}) {vm.action {vm.preferences.set(key,it)}}
        }
        TextButton({vm.action {vm.preferences.resetAnalog()}},Modifier.padding(horizontal=12.dp)){Text(tr("Matikan efek analog","Turn analog effects off"))}
    }
}

@Composable private fun PresetOption(title:String,description:String,selected:Boolean,enabled:Boolean,onClick:()->Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal=20.dp).clickable(enabled=enabled,onClick=onClick).semantics(mergeDescendants=true){this.selected=selected},color=if(selected)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,shape=MaterialTheme.shapes.large) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected,null,enabled=enabled);Column(Modifier.weight(1f).padding(start=8.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    }
}
