package id.iswara.music.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.model.Track
import id.iswara.music.source.LyricHit
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

@Composable internal fun RecognitionDialog(close: () -> Unit, openSearch: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var stage by remember { mutableIntStateOf(0) }
    var song by remember { mutableStateOf<RecognizedSong?>(null) }
    var status by remember { mutableIntStateOf(0) }
    fun identify(uri: Uri?) {
        if (stage != 0 || token.isBlank()) return
        val suppliedToken = token.trim()
        status = 0
        song = null
        stage = 1
        scope.launch {
            try {
                val clip = if (uri == null) SongRecognition.microphone(context) else SongRecognition.file(context, uri)
                stage = 2
                song = SongRecognition.identify(clip, uri == null, suppliedToken)
                if (song == null) status = 1
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                status = 2
            } finally {
                stage = 0
            }
        }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) identify(null) else status = 3
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::identify) }
    AlertDialog(onDismissRequest = close, title = { Text(tr("Kenali lagu", "Identify song")) }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("iSwara mengirim cuplikan sekitar 10 detik ke AudD untuk mencari judul dan artis. Masukkan token AudD milikmu.",
                "iSwara sends about 10 seconds of audio to AudD to identify the track. Enter your own AudD token."))
            OutlinedTextField(token, { token = it.take(256) }, enabled = stage == 0, singleLine = true,
                label = { Text(tr("Token AudD", "AudD token")) }, visualTransformation = PasswordVisualTransformation())
            OutlinedButton({ file.launch(arrayOf("audio/*")) }, enabled = stage == 0 && token.isNotBlank()) {
                Text(tr("Pilih file audio", "Choose audio file"))
            }
            if (stage != 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (stage == 1) tr("Mengambil cuplikan…", "Preparing audio clip…") else tr("Mencocokkan audio…", "Matching audio…"))
            }
            when (status) {
                1 -> Text(tr("Belum ada kecocokan. Coba bagian lagu yang lebih jelas.", "No match. Try a clearer part of the song."))
                2 -> Text(tr("Pengenalan gagal. Periksa file, koneksi, token, atau kuota AudD.", "Recognition failed. Check the file, connection, token, or AudD quota."))
                3 -> Text(tr("Izinkan mikrofon atau pilih file audio.", "Allow microphone access or select an audio file."))
            }
            song?.let { result ->
                Text(result.title, style = MaterialTheme.typography.titleMedium)
                Text(listOf(result.artist, result.album).filter { it.isNotBlank() }.joinToString(" · "))
                TextButton({ openSearch(result.title, result.artist) }) { Text(tr("Cari di iSwara", "Find in iSwara")) }
            }
        }
    }, confirmButton = {
        Button({
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                identify(null)
            } else microphone.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = stage == 0 && token.isNotBlank()) { Text(tr("Dengar lewat mikrofon", "Listen with microphone")) }
    }, dismissButton = { TextButton(close) { Text(tr("Tutup", "Close")) } })
}
