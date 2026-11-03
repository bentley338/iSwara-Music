package id.iswara.music.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun SettingsScreen(vm: MusicViewModel, onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    var widgets by remember { mutableStateOf(false) }
    var studio by remember { mutableStateOf(false) }
    var smartLyrics by remember { mutableStateOf(false) }
    var updates by remember {mutableStateOf(false)}
    var eq by remember { mutableStateOf(false) }
    var recap by remember { mutableStateOf(false) }
    var car by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf<String?>(null) }
    var legal by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
    val floating = vm.preferences.raw("floatingLyrics").collectAsStateWithLifecycle(initialValue = "false").value
    val floatingSize = vm.preferences.raw("floatingSize").collectAsStateWithLifecycle(initialValue="20").value ?: "20"
    val floatingOpacity = vm.preferences.raw("floatingOpacity").collectAsStateWithLifecycle(initialValue="235").value ?: "235"
    val overlayPermission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(android.provider.Settings.canDrawOverlays(context)) vm.action {vm.preferences.set("floatingLyrics","true")}
    }
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { PageHeading(tr("Setelan", "Settings"), tr("Atur suara, tampilan, dan koleksimu.", "Your sound, appearance, and library."), action = { ActionIcon(Icons.Rounded.Close, tr("Tutup setelan", "Close settings"), onBack) }) }
        item { SectionTitle(tr("Tampilan & bahasa", "Appearance & language")) }
        item {MenuAction(Icons.Rounded.SystemUpdate,tr("Pembaruan aplikasi","App updates")){updates=true}}
        item { SettingChoice(tr("Tema", "Theme"), settings.theme, listOf("system" to tr("Sistem", "System"), "dark" to tr("Gelap", "Dark"), "light" to tr("Terang", "Light"),"midnight" to "Midnight","forest" to tr("Hutan","Forest"),"dawn" to tr("Fajar","Dawn"))) { vm.action { vm.preferences.set("theme", it) } } }
        item { SettingChoice(tr("Warna aksen","Accent color"),settings.accent,listOf("orange" to tr("Jingga","Orange"),"ocean" to tr("Samudra","Ocean"),"mint" to "Mint","rose" to "Rose","lavender" to "Lavender","gold" to tr("Emas","Gold"))){vm.action {vm.preferences.set("accent",it)}} }
        item { SettingToggle(tr("Animasi antarmuka","Interface motion"),tr("Peralihan halaman, sampul, dan reaksi siaran.","Page transitions, artwork, and lounge reactions."),settings.animations){vm.action {vm.preferences.toggle("animations",it)}} }
        item { SettingChoice(tr("Bahasa", "Language"), settings.language, listOf("id" to "Indonesia", "en" to "English")) { vm.action { vm.preferences.set("language", it) } } }
        item { SectionTitle(tr("Audio & koneksi", "Audio & connection")) }
        item { SettingChoice(tr("Kualitas streaming", "Streaming quality"), settings.quality, listOf("low" to tr("Rendah", "Low"), "medium" to tr("Sedang", "Medium"), "high" to tr("Tertinggi tersedia", "Highest available"))) { vm.action { vm.preferences.set("quality", it) } } }
        item { SettingToggle(tr("Hemat data", "Data saver"), tr("Gunakan bitrate terendah yang tersedia.", "Use the lowest available bitrate."), settings.dataSaver) { vm.action { vm.preferences.dataSaver(it) } } }
        item { Text(tr("Perubahan kualitas berlaku saat stream berikutnya dimuat. Kualitas mengikuti audio yang tersedia dari sumber.", "Quality changes apply when the next stream loads. Quality depends on the source."), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SettingToggle(tr("Siapkan lagu berikutnya", "Prepare next track"), tr("Siapkan awal audio lagu berikutnya.", "Preload the beginning of the next track."), settings.preload) { vm.action { vm.preferences.toggle("preload", it) } } }
        item { SettingToggle(tr("Lewati bagian hening", "Skip silence"), tr("Potong jeda tanpa suara.", "Skip silent passages. Listening duration may change."), settings.skipSilence) { vm.action { vm.preferences.toggle("skipSilence", it) } } }
        item { SettingChoice(tr("Kecepatan pemutaran", "Playback speed"), settings.speed, listOf("0.75", "1", "1.25", "1.5", "2").map { it to "${it}x" }) { vm.action { vm.preferences.set("speed", it) } } }
        item { MenuAction(Icons.Rounded.Tune,tr("Studio suara & karaoke","Sound studio & karaoke")){studio=true} }
        item { MenuAction(Icons.Rounded.Equalizer, tr("Equalizer & bass", "Equalizer & bass")) { eq = true } }
        item { SettingToggle(tr("Volume seimbang", "Balanced volume"), tr("Ratakan keras-pelan audio.", "Smooth loud and quiet audio."), settings.normalize) { vm.action { vm.preferences.toggle("normalize", it) } } }
        item { SectionTitle(tr("Lirik & gerakan","Lyrics & motion")) }
        item { MenuAction(Icons.Rounded.Translate,tr("Cara baca & arti lirik","Lyric readings & meanings")){smartLyrics=true} }
        item { SettingToggle(tr("Animasi lirik","Lyric animation"),tr("Peralihan halus saat baris berganti.","Smooth transitions between lyric lines."),settings.lyricsMotion) {vm.action {vm.preferences.toggle("lyricsMotion",it)}} }
        item { SettingToggle(tr("Ikuti lirik otomatis","Follow lyrics automatically"),tr("Berhenti mengikuti saat lirik digeser manual.","Pause following when you scroll lyrics manually."),settings.lyricsFollow) {vm.action {vm.preferences.toggle("lyricsFollow",it)}} }
        item { SettingChoice(tr("Tema lirik","Lyrics theme"),settings.lyricsTheme,LyricsVisuals.themes){vm.action {vm.preferences.set("lyricsTheme",it)}} }
        item { SettingChoice(tr("Gaya lirik","Lyric style"),settings.lyricsStyle,listOf("auto" to tr("Beda tiap lagu","Per song"))+LyricsVisuals.styles) {vm.action {vm.preferences.set("lyricsStyle",it)}} }
        item { SettingToggle(tr("Lirik lebih besar","Larger lyrics"),tr("Ukuran nyaman untuk ikut bernyanyi.","More room to sing along."),settings.lyricsLarge) {vm.action {vm.preferences.toggle("lyricsLarge",it)}} }
        item { SectionTitle(tr("Pemutaran", "Playback")) }
        item { SettingToggle(tr("Cari sumber cadangan","Find alternative sources"),tr("Cocokkan judul, artis, versi, dan durasi saat audio gagal.","Match title, artist, version, and duration when audio fails."),settings.sourceFallback){vm.action {vm.preferences.toggle("sourceFallback",it)}} }
        item { SettingToggle(tr("Izinkan lagu berulang", "Allow duplicates"), tr("Tambahkan lagu yang sama ke antrean.", "Allow the same track more than once in the queue."), settings.allowDuplicates) { vm.action { vm.preferences.toggle("allowDuplicates", it) } } }
        item { SettingToggle(tr("Radio otomatis", "Autoplay radio"), tr("Lanjutkan dengan lagu serupa.", "Keep listening to similar songs."), settings.autoplay) { vm.action { vm.preferences.toggle("autoplay", it) } } }
        item { SettingToggle(tr("Lewati lagu tidak tersedia", "Skip unavailable songs"), tr("Coba lagu berikutnya setelah gagal.", "Try the next song after a failure."), settings.skipUnavailable) { vm.action { vm.preferences.toggle("skipUnavailable", it) } } }
        item { SettingToggle(tr("Warna dari sampul", "Artwork colors"), tr("Warnai tampilan pemutar.", "Color your player."), settings.playerColors) { vm.action { vm.preferences.toggle("playerColors", it) } } }
        item { MenuAction(Icons.Rounded.DirectionsCar, tr("Mode berkendara", "Car mode")) { car = true } }
        item { SettingToggle(tr("Lirik melayang", "Floating lyrics"), tr("Lirik di atas aplikasi lain.", "Lyrics above other apps."), floating == "true") { enabled ->
            if (enabled && !android.provider.Settings.canDrawOverlays(context)) overlayPermission.launch(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            else vm.action { vm.preferences.set("floatingLyrics", enabled.toString()) }
        } }
        if(floating=="true") {
            item { SettingChoice(tr("Ukuran lirik melayang","Floating lyric size"),floatingSize,listOf("16" to tr("Kecil","Small"),"20" to tr("Sedang","Medium"),"26" to tr("Besar","Large"))) {vm.action {vm.preferences.set("floatingSize",it)}} }
            item { SettingChoice(tr("Latar lirik melayang","Floating lyric background"),floatingOpacity,listOf("170" to tr("Transparan","Transparent"),"235" to tr("Lembut","Soft"),"250" to tr("Pekat","Solid"))) {vm.action {vm.preferences.set("floatingOpacity",it)}} }
        }
        item { MenuAction(Icons.Rounded.Widgets,tr("Koleksi widget","Widget collection")) {widgets=true} }
        item { SettingToggle(tr("Animasi vinyl","Vinyl animation"),tr("Putar piringan saat musik berjalan.","Rotate the record while music plays."),settings.widgetMotion) {vm.action {vm.preferences.toggle("widgetMotion",it)}} }
        item { MenuAction(Icons.Rounded.Insights, tr("Rekap dengar", "Listening recap")) { recap = true } }
        item { SectionTitle(tr("Data & koleksi", "Data & library")) }
        item { MenuAction(Icons.Rounded.UploadFile, tr("Ekspor backup koleksi", "Export library backup"), onExport) }
        item { MenuAction(Icons.Rounded.FileOpen, tr("Impor backup koleksi", "Import library backup"), onImport) }
        item { MenuAction(Icons.Rounded.Link, tr("Buka tautan musik", "Open music link")) { link = "" } }
        item { MenuAction(Icons.Rounded.PrivacyTip, tr("Privasi & sumber musik", "Privacy & music sources")) { legal = "Privasi / Privacy" to context.assets.open("legal/PRIVACY.txt").bufferedReader().use { it.readText() } } }
        item { MenuAction(Icons.Rounded.Description, tr("Lisensi sumber terbuka", "Open-source licenses")) { legal = "GPL-3.0-or-later" to context.assets.open("legal/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } + "\n\n" + context.assets.open("legal/LICENSE.txt").bufferedReader().use { it.readText() } + listOf("SPLEETER_LICENSE.txt","SHERPA_ONNX_LICENSE.txt","ONNXRUNTIME_LICENSE.txt","ONNXRUNTIME_THIRD_PARTY.txt","KUROMOJI_LICENSE.txt","KUROMOJI_IPADIC_NOTICE.txt","ICU_LICENSE.txt").joinToString("\n\n") {name->context.assets.open("legal/$name").bufferedReader().use {it.readText()}} } }
        item { Text("iSwara Music ${id.iswara.music.BuildConfig.VERSION_NAME}", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

    }
    if(widgets) WidgetGallery {widgets=false}
    if(studio) SoundStudio(vm){studio=false}
      if(smartLyrics) SmartLyricsSettings(vm){smartLyrics=false}
      if(updates) UpdateScreen(vm){updates=false}
    if (eq) EqualizerDialog(vm) { eq = false }
    if (recap) ListeningRecap(vm) { recap = false }
    if (car) CarMode(vm) { car = false }
    if (link != null) TextPrompt(tr("Buka tautan musik", "Open music link"), tr("Tempel tautan lagu atau playlist","Paste a track or playlist link"), link!!, { link = it }, { link = null }) { vm.shared(link.orEmpty()); link = null; onBack() }
    if (legal != null) AlertDialog(onDismissRequest = { legal = null }, title = { Text(legal!!.first) }, text = { Text(legal!!.second, Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) }, confirmButton = { TextButton({ legal = null }) { Text(tr("Tutup", "Close")) } })
}

@Composable fun SettingToggle(title: String, description: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = value, role = Role.Switch, onValueChange = onChange).semantics(mergeDescendants = true) { contentDescription = title }.padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, null, modifier = Modifier.clearAndSetSemantics {})
    }
}
