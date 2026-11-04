package id.iswara.music.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.model.Track
import id.iswara.music.source.LyricHit
import id.iswara.music.source.PlaybackRecognitionService
import id.iswara.music.source.RecognitionEntry
import id.iswara.music.source.RecognitionFailure
import id.iswara.music.source.RecognitionIssue
import id.iswara.music.source.RecognizedSong
import id.iswara.music.source.SongRecognition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun LyricPhraseDialog(vm: MusicViewModel, close: () -> Unit) {
    val owner = vm.libraryRepository.scope.collectAsStateWithLifecycle().value
    var query by remember(owner) { mutableStateOf("") }
    var results by remember(owner) { mutableStateOf<List<LyricHit>?>(null) }
    var busy by remember(owner) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = close, title = { Text(tr("Cari dari lirik", "Search lyrics")) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Cari potongan dari lirik yang sudah tersimpan di perangkat ini.", "Find a line in lyrics stored on this device."))
            OutlinedTextField(query, { query = it.take(160) }, label = { Text(tr("Potongan lirik", "Lyric fragment")) }, enabled = !busy)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (results?.isEmpty() == true) Text(tr("Potongan ini belum ada di lirik tersimpan.", "No match in saved lyrics."))
            results.orEmpty().forEach { hit ->
                TextButton({
                    vm.playback.playAt(hit.track, hit.position ?: 0)
                    close()
                }) { Text(hit.track.title + " · " + hit.track.artist + "\n" + hit.excerpt) }
            }
        }
    }, confirmButton = {
        Button({
            busy = true
            scope.launch {
                try { results = vm.findLyricPhrase(query) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { results = emptyList() }
                finally { busy = false }
            }
        }, enabled = !busy && query.trim().length >= 3) { Text(tr("Cari", "Search")) }
    }, dismissButton = { TextButton(close) { Text(tr("Tutup", "Close")) } })
}

@Composable internal fun TrackExcerptDialog(vm: MusicViewModel, track: Track, close: () -> Unit) {
    var playing by remember(track.id) { mutableStateOf(false) }
    var finished by remember(track.id) { mutableStateOf(false) }
    var failed by remember(track.id) { mutableStateOf(false) }
    LaunchedEffect(track.id) {
        try { vm.playback.previewExcerpt(track) { playing = true } }
        catch (error: kotlinx.coroutines.TimeoutCancellationException) { failed = true }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
        finally { finished = true }
    }
    AlertDialog(onDismissRequest = close, title = { Text(track.title) }, text = {
        Text(when {
            failed -> tr("Cuplikan belum bisa diputar.", "The preview could not play.")
            finished -> tr("Cuplikan selesai.", "Preview finished.")
            playing -> tr("Memutar cuplikan hingga 15 detik.", "Playing up to 15 seconds.")
            else -> tr("Memuat cuplikan…", "Loading preview…")
        })
    }, confirmButton = {
        Column {
            TextButton({ vm.playback.enqueue(track, true); close() }) { Text(tr("Putar berikutnya", "Play next")) }
            val collection = vm.library.collectAsStateWithLifecycle().value
            if (collection.favorites.none { it.id == track.id }) {
                TextButton({ vm.favorite(track) }) { Text(tr("Simpan ke favorit", "Save to favorites")) }
            }
        }
    }, dismissButton = { TextButton(close) { Text(tr("Tutup", "Close")) } })
}

@Composable internal fun RecognitionDialog(entry: RecognitionEntry, close: () -> Unit, openSearch: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val internal by PlaybackRecognitionService.state.collectAsStateWithLifecycle()
    var stage by remember { mutableIntStateOf(0) }
    var canRecognize by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<RecognitionIssue?>(null) }
    var completed by remember { mutableStateOf(false) }
    var song by remember { mutableStateOf<RecognizedSong?>(null) }
    var internalPermission by remember { mutableStateOf(false) }
    val busy = stage != 0 || internal.active
    fun verifyService() {
        if (checking) return
        checking = true
        issue = null
        scope.launch {
            try { SongRecognition.available(); canRecognize = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: RecognitionFailure) { issue = failure.issue; canRecognize = false }
            catch (_: Exception) { issue = RecognitionIssue.NETWORK; canRecognize = false }
            finally { checking = false }
        }
    }
    LaunchedEffect(Unit) { verifyService() }
    fun clearResult() {
        PlaybackRecognitionService.reset(); completed = false; song = null; issue = null
    }
    fun identify(uri: Uri?) {
        if (busy || !canRecognize) return
        clearResult()
        stage = 1
        scope.launch {
            try {
                val audio = if (uri == null) SongRecognition.microphone(context) else SongRecognition.file(context, uri)
                stage = 2
                song = SongRecognition.identify(audio, microphone = uri == null)
                completed = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: RecognitionFailure) { issue = failure.issue }
            catch (_: Exception) { issue = if (stage == 1) if (uri == null) RecognitionIssue.PERMISSION else RecognitionIssue.FILE else RecognitionIssue.NETWORK }
            finally { stage = 0 }
        }
    }
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val consent = result.data
        if (result.resultCode != Activity.RESULT_OK || consent == null) issue = RecognitionIssue.PERMISSION
        else if (PlaybackRecognitionService.start(context, consent)) entry.videoLink?.let { link ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
        }
    }
    fun askProjection() {
        if (Build.VERSION.SDK_INT < 29) return
        clearResult()
        try { projection.launch(checkNotNull(context.getSystemService(MediaProjectionManager::class.java)).createScreenCaptureIntent()) }
        catch (_: RuntimeException) { issue = RecognitionIssue.PERMISSION }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) { if (internalPermission) askProjection() else identify(null) }
        else issue = RecognitionIssue.PERMISSION
    }
    fun chooseCapture(deviceAudio: Boolean) {
        internalPermission = deviceAudio
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else if (deviceAudio) askProjection() else identify(null)
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::identify) }
    val activeIssue = internal.issue ?: issue
    val recognized = internal.song ?: song
    val enabled = canRecognize && !checking && !busy
    val closeDialog: () -> Unit = { if (internal.active) PlaybackRecognitionService.stop(context); close() }
    AlertDialog(onDismissRequest = closeDialog, title = { Text(tr("Kenali lagu", "Identify song")) }, text = {
        Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (entry.videoLink != null) tr("Buka lagi video TikTok dan putar suaranya setelah mengizinkan audio perangkat.", "Reopen the TikTok video and play it after allowing device audio.")
                 else tr("Pilih sumber suara. iSwara mengirim cuplikan pendek untuk mencocokkan judul dan artis.", "Choose an audio source. iSwara sends a short clip to match the title and artist."))
            if (entry.attachment != null) Button({ identify(entry.attachment) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(tr("Kenali file yang dibagikan", "Identify shared file"))
            }
            if (Build.VERSION.SDK_INT >= 29) Button({ chooseCapture(true) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (entry.videoLink != null) tr("Kenali dari TikTok", "Identify from TikTok") else tr("Audio perangkat", "Device audio"))
            } else Text(tr("Audio perangkat memerlukan Android 10 atau lebih baru.", "Device audio requires Android 10 or newer."), style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ chooseCapture(false) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(tr("Mikrofon", "Microphone"))
            }
            OutlinedButton({ file.launch(arrayOf("audio/*", "video/*")) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(tr("Pilih file audio atau video", "Choose an audio or video file"))
            }
            if (checking || busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(when {
                    checking -> tr("Menghubungkan…", "Connecting…")
                    internal.matching || stage == 2 -> tr("Mencocokkan audio…", "Matching audio…")
                    internal.seconds != null -> tr("Mendengarkan (${internal.seconds}/10 detik)…", "Listening (${internal.seconds}/10 seconds)…")
                    internal.active -> tr("Pindah ke aplikasi sumber dan putar suaranya sekarang.", "Switch to the source app and play its audio now.")
                    else -> tr("Mengambil cuplikan…", "Preparing audio clip…")
                })
            }
            if (!busy && !checking) {
                activeIssue?.let { error -> Text(when (error) {
                    RecognitionIssue.UNAVAILABLE -> tr("Pengenal lagu belum tersedia. Coba lagi nanti.", "Song recognition is unavailable. Try again later.")
                    RecognitionIssue.LIMIT -> tr("Batas pengenalan tercapai. Coba lagi nanti.", "Recognition limit reached. Try again later.")
                    RecognitionIssue.NETWORK -> tr("Koneksi gagal. Coba lagi.", "Connection failed. Try again.")
                    RecognitionIssue.NO_AUDIO -> tr("Audio perangkat tidak tertangkap. Putar lewat speaker dan gunakan mikrofon, atau pilih file.", "No device audio was captured. Use the speaker and microphone, or choose a file.")
                    RecognitionIssue.STOPPED -> tr("Perekaman dihentikan.", "Recording stopped.")
                    RecognitionIssue.PERMISSION -> tr("Akses audio tidak tersedia. Izinkan akses atau pilih file.", "Audio access is unavailable. Allow access or choose a file.")
                    RecognitionIssue.FILE -> tr("Trek audio file tidak dapat dibaca. Pilih file lain.", "The file's audio track could not be read. Choose another file.")
                }, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (recognized == null && (internal.finished || completed)) Text(tr("Belum ketemu. Coba bagian lagu yang lebih jelas.", "No match. Try a clearer part of the song."))
                recognized?.let { result ->
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(result.artist, result.album).filter { it.isNotBlank() }.joinToString(" · "))
                    TextButton({ openSearch(result.title, result.artist) }) { Text(tr("Cari di iSwara", "Find in iSwara")) }
                }
            }
        }
    }, confirmButton = {
        if (internal.active) TextButton({ PlaybackRecognitionService.stop(context) }) { Text(tr("Hentikan", "Stop")) }
        else if (!canRecognize && !checking) TextButton(::verifyService) { Text(tr("Coba lagi", "Retry")) }
    }, dismissButton = { TextButton(closeDialog) { Text(tr("Tutup", "Close")) } })
}
