package id.iswara.music.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.iswara.music.model.Track
import kotlinx.coroutines.*
import java.io.File

@Composable fun LyricsExcerptPicker(lines:List<String>,onDismiss:()->Unit,onSelect:(List<Int>)->Unit) {
    var selected by remember {mutableStateOf<Set<Int>>(emptySet())}
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr("Pilih 2–4 baris","Choose 2–4 lines"))},text={
        Column {Row {TextButton({selected=lines.indices.toSet()}){Text(tr("Pilih semua","Select all"))};TextButton({selected=emptySet()}){Text(tr("Kosongkan pilihan","Clear selection"))}};Text("${selected.size} baris",style=MaterialTheme.typography.labelMedium)
        LazyColumn(Modifier.heightIn(max=400.dp)) {itemsIndexed(lines) {index,line ->
            val toggle={selected=if(index in selected)selected-index else selected+index}
            Row(Modifier.fillMaxWidth().clickable(onClick=toggle).padding(vertical=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(index in selected,null);Text(line,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            }
        }}}
    },confirmButton={TextButton({onSelect(selected.sorted())},enabled=selected.isNotEmpty()) {Text(tr("Lanjut","Continue"))}},dismissButton={TextButton(onDismiss){Text(tr("Batal","Cancel"))}})
}

@Composable fun LyricsCardStudio(vm:MusicViewModel,track:Track,lines:List<String>,timed:List<id.iswara.music.model.LyricLine> = emptyList(),onDismiss:()->Unit) {
    if(lines.none {it.isNotBlank()}) {AlertDialog(onDismissRequest=onDismiss,title={Text(tr("Belum ada teks lirik","No lyric text"))},text={Text(tr("Pilih baris yang berisi teks.","Choose lines containing text."))},confirmButton={TextButton(onDismiss){Text(tr("Tutup","Close"))}});return}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val motion=vm.settings.collectAsState().value.lyricsMotion
    var followAudio by remember {mutableStateOf(timed.isNotEmpty())}
    val exports=vm.lyricMedia.status.collectAsState().value
    val state=exports.lastOrNull {it.track==track.id}
    var kind by remember {mutableStateOf("image")}
    var style by remember {mutableStateOf("charcoal")}
    var story by remember {mutableStateOf(true)}
    var page by remember {mutableIntStateOf(0)}
    val pages=remember(lines,story){vm.artwork.lyricPages(lines,story)}
    val selected=page.coerceIn(0,pages.lastIndex)
    var file by remember {mutableStateOf<File?>(null)}
    var preview by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var saving by remember {mutableStateOf(false)}
    var saved by remember {mutableStateOf(false)}
    var pendingSave by remember {mutableStateOf<File?>(null)}
    var allProgress by remember {mutableIntStateOf(0)}
    val duration=(track.duration*1000).takeIf {it>0} ?: 480_000L
    var start by remember {mutableLongStateOf((timed.firstOrNull()?.time ?: 0L).coerceIn(0,(duration-1000).coerceAtLeast(0)))}
    var startDrag by remember {mutableStateOf<Float?>(null)}
    var seconds by remember {mutableIntStateOf(30)}
    val clipDuration=minOf(seconds*1000L,duration-start).coerceAtLeast(1000)
    val request=id.iswara.music.data.LyricExport(track,pages[selected],if(followAudio)timed else emptyList(),start,clipDuration,kind=="video",motion)
    val fingerprint=remember(request){vm.lyricMedia.fingerprint(request)}
    val mediaFile=state?.takeIf {it.fingerprint==fingerprint}?.file?.takeIf {it.extension==if(kind=="video")"mp4" else "m4a"}
    val output=if(kind=="image")file else mediaFile
    LaunchedEffect(style,story,track.id,lines,selected,kind,start,followAudio,motion) {
        file=null;error=null;saved=false;preview=null
        try {
            if(kind=="image") {
                val result=vm.artwork.lyricCardFile(track,pages[selected],style,story)
                val image=withContext(Dispatchers.IO){android.graphics.BitmapFactory.decodeFile(result.path,android.graphics.BitmapFactory.Options().apply {inSampleSize=2})}
                ensureActive();file=result;preview=image
            } else if(kind=="video") {
                val cover=vm.artwork.bitmap(track.artwork)
                preview=withContext(Dispatchers.Default) {val painter=id.iswara.music.playback.LyricVideoPainter(context,track,pages[selected],if(followAudio)timed else emptyList(),cover,motion);try {painter.draw(start+300,300,clipDuration).copy(android.graphics.Bitmap.Config.ARGB_8888,false)}finally {painter.close()}}
            }
        } catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "Pratinjau belum tersedia."}
    }
    val saveResult:(android.net.Uri?)->Unit={uri ->
        val source=pendingSave;pendingSave=null
        if(uri!=null && source!=null)scope.launch {saving=true;try {withContext(Dispatchers.IO){if(source.extension in listOf("mp4","m4a"))vm.lyricMedia.validate(source);context.contentResolver.openOutputStream(uri)?.use {out->source.inputStream().use {it.copyTo(out)}} ?: error("Penyimpanan tidak tersedia.")};saved=true}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally {saving=false}}
    }
    val saveImage=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"),saveResult)
    val saveAudio=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4"),saveResult)
    val saveVideo=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4"),saveResult)
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr("Studio kartu lirik","Lyrics card studio"))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr("${lines.size} baris pilihanmu","${lines.size} selected lines"),style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("image" to tr("Gambar","Image"),"audio" to tr("Audio","Audio"),"video" to tr("Video + audio","Video + audio")).forEach {(key,label)->FilterChip(kind==key,{kind=key},{Text(label)},enabled=!saving && state?.running!=true)}
            }
            if(kind=="image") {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("charcoal" to "Charcoal","paper" to tr("Kertas","Paper"),"cover" to tr("Sampul","Cover")).forEach {(key,label)->FilterChip(style==key,{style=key},{Text(label)})}}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(story,{story=true;page=0},{Text("Story 9:16")});FilterChip(!story,{story=false;page=0},{Text("Post 4:5")})}
                if(pages.size>1)Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){TextButton({page=selected-1},enabled=selected>0){Text("‹")};Text("${selected+1} / ${pages.size}",Modifier.weight(1f));TextButton({page=selected+1},enabled=selected<pages.lastIndex){Text("›")}}
                if(file==null && error==null)LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text("${timeLabel(start)} – ${timeLabel(start+clipDuration)}",style=MaterialTheme.typography.titleMedium)
                Text(tr("Awal potongan","Clip start"),style=MaterialTheme.typography.labelMedium)
                Slider(startDrag ?: start.toFloat(),{startDrag=it},onValueChangeFinished={startDrag?.let {start=it.toLong()};startDrag=null},valueRange=0f..(duration-1000).coerceAtLeast(1).toFloat(),enabled=state?.running!=true)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(15,30,60).forEach {n->FilterChip(seconds==n,{seconds=n},{Text("$n ${tr("detik","sec")}")},enabled=state?.running!=true)}}
                if(kind=="video")FilterChip(followAudio,{followAudio=!followAudio},{Text(tr("Lirik mengikuti audio","Lyrics follow audio"))},enabled=timed.isNotEmpty() && state?.running!=true)
                Text(if(kind=="audio")tr("Potongan audio asli dalam M4A.","Original audio clip in M4A.")else if(!followAudio)tr("Video vertikal dengan audio dan teks pilihanmu yang tampil diam.","Vertical video with audio and static selected text.")else tr("Video vertikal dengan lirik per baris mengikuti timestamp yang tersedia.","Vertical video with lines following available lyric timestamps."),style=MaterialTheme.typography.bodySmall)
                Text(tr("Proses memakai audio asli, maksimal 60 detik per ekspor. Sediakan 350 MB ruang kosong.","Uses original audio, up to 60 seconds per export. Keep 350 MB free."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(state?.running==true){Text(state.stage);LinearProgressIndicator(progress={state.percent/100f},modifier=Modifier.fillMaxWidth());TextButton(vm.lyricMedia::cancel){Text(tr("Batalkan ekspor","Cancel export"))}}
                else Button({vm.action {vm.lyricMedia.enqueue(request)}},Modifier.fillMaxWidth()){Text(if(kind=="video")tr("Buat video lirik","Create lyric video")else tr("Buat potongan audio","Create audio clip"))}
                state?.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                if(mediaFile!=null)Text(tr("Hasil ekspor siap dibagikan.","Your export is ready to share."))
                val previous=state?.file?.takeIf {mediaFile==null}
                if(previous!=null)TextButton({runCatching {context.startActivity(Intent.createChooser(vm.lyricMedia.share(previous),null))}.onFailure {error=it.message}}){Text(tr("Bagikan hasil sebelumnya","Share previous export"))}
            }
            preview?.takeIf {kind!="image" || file!=null}?.let {Image(it.asImageBitmap(),tr("Pratinjau kartu lirik","Lyrics card preview"),Modifier.fillMaxWidth().heightIn(max=300.dp))}
            if(kind=="image" && pages.size>1)TextButton({scope.launch {
                saving=true;error=null
                try {val files=mutableListOf<File>();for((index,part)in pages.withIndex()){ensureActive();allProgress=index+1;files+=vm.artwork.lyricCardFile(track,part,style,story)};context.startActivity(Intent.createChooser(vm.artwork.shareCards(files),null))}
                catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally {saving=false;allProgress=0}
            }},enabled=!saving){Text(if(allProgress>0)"$allProgress / ${pages.size}"else tr("Bagikan semua ${pages.size} gambar","Share all ${pages.size} images"))}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(saved)Text(tr("Berkas tersimpan.","File saved."))
        }
    },confirmButton={TextButton({output?.let {source->runCatching {context.startActivity(Intent.createChooser(if(kind=="image")vm.artwork.shareCard(source)else vm.lyricMedia.share(source),null))}.onFailure {error="Tidak ada aplikasi berbagi yang tersedia."}}},enabled=output!=null && !saving){Text(tr("Bagikan","Share"))}},dismissButton={Row {
        TextButton({pendingSave=output;when(kind){"image"->saveImage.launch("iSwara-lirik-${selected+1}.png");"video"->saveVideo.launch("iSwara-lirik.mp4");else->saveAudio.launch("iSwara-audio.m4a")}},enabled=output!=null && !saving){Text(tr("Simpan","Save"))}
        TextButton(onDismiss){Text(tr("Tutup","Close"))}
    }})
}

