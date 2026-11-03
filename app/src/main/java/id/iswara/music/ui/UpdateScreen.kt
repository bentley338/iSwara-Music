package id.iswara.music.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import id.iswara.music.BuildConfig
import id.iswara.music.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.*

@Composable fun AppUpdateBanner(vm:MusicViewModel,open:()->Unit) {
    val release=remember(vm){vm.updates.state.map {it.release}.distinctUntilChanged()}.collectAsStateWithLifecycle(initialValue=null).value ?: return
    var dismissed by remember(release.versionCode){mutableStateOf(false)}
    if(!dismissed)Surface(color=MaterialTheme.colorScheme.secondaryContainer){Row(Modifier.fillMaxWidth().padding(start=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(open,Modifier.weight(1f)){Icon(Icons.Rounded.SystemUpdate,null);Spacer(Modifier.width(8.dp));Text("iSwara ${release.versionName} ${tr("tersedia","available")}")}
        ActionIcon(Icons.Rounded.Close,tr("Ingatkan nanti","Remind me later"),{dismissed=true})
    }}
}

@Composable fun UpdateScreen(vm:MusicViewModel,close:()->Unit) {
    val state=vm.updates.state.collectAsStateWithLifecycle().value
    val settings=vm.settings.collectAsStateWithLifecycle().value
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var error by remember {mutableStateOf<String?>(null)}
    var autoInstall by remember {mutableStateOf(false)}
    val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    fun install(){val release=state.release ?: return;scope.launch {try{context.startActivity(vm.updates.installIntent(release))}catch(_:Exception){error="Pemasangan belum dapat dibuka. Periksa izin atau unduh ulang pembaruan."}}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){if(context.packageManager.canRequestPackageInstalls())install()else error="Izinkan pemasangan dari iSwara untuk melanjutkan."}
    fun requestInstall(){if(!context.packageManager.canRequestPackageInstalls())permission.launch(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))else install()}
    LaunchedEffect(Unit){vm.updates.check()}
    LaunchedEffect(state.ready){if(state.ready && autoInstall && (context.applicationContext as? id.iswara.music.IswaraApp)?.visible==true){autoInstall=false;requestInstall()}}
    Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            LazyColumn(Modifier.safeDrawingPadding().testTag("update-list"),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item{PageHeading(tr("Pembaruan aplikasi","App updates"),"iSwara Music ${BuildConfig.VERSION_NAME}"){ActionIcon(Icons.Rounded.Close,tr("Tutup pembaruan","Close updates"),close)}}
                item{Surface(Modifier.fillMaxWidth().padding(horizontal=20.dp),shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.primaryContainer){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.SystemUpdate,null,Modifier.size(36.dp))
                    Text(state.release?.let {"iSwara ${it.versionName}"} ?: tr("Tetap di putaran terbaru","Stay up to date"),style=MaterialTheme.typography.headlineMedium)
                    Text(if(state.checking)tr("Mencari versi terbaru…","Checking for updates…")else if(state.release==null)tr("Cek pembaruan tanpa mengubah koleksimu.","Check for updates while keeping your library.")else tr("Pembaruan dipasang di atas versi sekarang. Koleksimu tetap tersimpan.","The update installs over your current version and keeps your library."))
                    if(state.release!=null){val release=state.release!!;Text("${release.bytes/1_000_000} MB · ${release.channel}",style=MaterialTheme.typography.labelLarge)}
                    Button({vm.action {vm.updates.check()}},enabled=!state.checking && !state.downloading){Text(tr("Cek pembaruan","Check for updates"))}
                }}}
                state.release?.let {release->
                    item{Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(tr("Yang baru","What's new"),style=MaterialTheme.typography.titleLarge);Text(release.notes.ifBlank {tr("Peningkatan iSwara Music.","iSwara Music improvements.")})
                        if(state.downloading){LinearProgressIndicator(progress={state.percent/100f},modifier=Modifier.fillMaxWidth());Text("${state.percent}% · ${tr("Menunggu Wi-Fi jika diperlukan","Waiting for Wi-Fi when required")}");OutlinedButton({autoInstall=false;vm.updates.cancel()}){Text(tr("Batalkan unduhan","Cancel download"))}}
                        else Button({if(state.ready)requestInstall()else{autoInstall=true;vm.updates.download(release)}},Modifier.fillMaxWidth()){Text(if(state.ready)tr("Pasang pembaruan","Install update")else tr("Unduh & pasang","Download & install"))}
                    }}
                }
                state.error?.let {item{Text(it,Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.error)}}
                error?.let {item{Text(it,Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.error)}}
                if(state.checked>0 && state.release==null && state.error==null)item{Text(tr("Belum ada versi yang lebih baru di kanal ini.","No newer release is available on this channel."),Modifier.padding(horizontal=20.dp))}
                item{SettingToggle(tr("Periksa otomatis","Check automatically"),tr("Pemeriksaan harian dan saat aplikasi dibuka.","Daily checks and checks when opening the app."),settings.updateAuto){vm.action {vm.preferences.toggle("updateAuto",it)};if(it && Build.VERSION.SDK_INT>=33)notification.launch(Manifest.permission.POST_NOTIFICATIONS)}}
                item{SettingToggle(tr("Unduh hanya lewat Wi-Fi","Download on Wi-Fi only"),tr("Unduhan menunggu jaringan tanpa batas kuota.","Wait for an unmetered network."),settings.updateWifi){vm.action {vm.preferences.toggle("updateWifi",it)}}}
                item{SettingChoice(tr("Kanal pembaruan","Update channel"),settings.updateChannel,listOf("stable" to tr("Stabil","Stable"),"beta" to "Beta")){vm.action {vm.preferences.set("updateChannel",it);vm.updates.check()}}}
                item{Text(tr("Berkas diperiksa sebelum dipasang. Android tetap meminta konfirmasi pemasangan.","The file is checked before installation. Android still asks for confirmation."),Modifier.padding(20.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
    }
}

@Composable fun AdminReleaseScreen(vm:MusicViewModel,owner:String,close:()->Unit) {
    if (!id.iswara.music.BuildConfig.ADMIN_TOOLS_ENABLED) return
    var code by remember {mutableStateOf("")};var version by remember {mutableStateOf("")};var url by remember {mutableStateOf("")};var sha by remember {mutableStateOf("")};var bytes by remember {mutableStateOf("")};var notes by remember {mutableStateOf("")};var channel by remember {mutableStateOf("stable")};var published by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)};var confirm by remember {mutableStateOf(false)};var success by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val draft=AppRelease(code.toIntOrNull() ?: 0,version.trim(),url.trim(),sha.trim().lowercase(),bytes.toLongOrNull() ?: 0,notes,channel)
    Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {Surface(Modifier.fillMaxSize()) {LazyColumn(Modifier.safeDrawingPadding(),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item{PageHeading(tr("Rilis aplikasi","App releases"),tr("Unggah APK ke GitHub Releases, lalu daftarkan versinya.","Upload to GitHub Releases, then register the version.")){ActionIcon(Icons.Rounded.Close,tr("Tutup rilis aplikasi","Close app releases"),close)}}
        listOf(Triple("Version code",code,{v:String->code=v.filter(Char::isDigit).take(9)}),Triple("Versi",version,{v:String->version=v.take(32)}),Triple("Tautan APK GitHub Releases",url,{v:String->url=v.take(2000)}),Triple("SHA-256",sha,{v:String->sha=v.take(64)}),Triple("Ukuran dalam byte",bytes,{v:String->bytes=v.filter(Char::isDigit).take(10)})).forEach {(label,value,change)->item{OutlinedTextField(value,change,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))}}
        item{OutlinedTextField(notes,{notes=it.take(12000)},label={Text(tr("Catatan pembaruan","Release notes"))},modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp),minLines=4)}
        item{SettingChoice(tr("Kanal","Channel"),channel,listOf("stable" to "Stable","beta" to "Beta")){channel=it}}
        item{SettingToggle(tr("Terbitkan versi ini","Publish this version"),tr("Versi terbit dapat ditemukan aplikasi pengguna.","Published releases can be discovered by users."),published){published=it}}
        error?.let {item{Text(it,Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.error)}}
        if(success)item{Text(tr("Info rilis tersimpan.","Release information saved."),Modifier.padding(20.dp))}
        item{Button({confirm=true},enabled=UpdateRules.valid(draft) && !busy,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp)){Text(tr("Tinjau & simpan","Review & save"))}}
    }}}
    if(confirm)AlertDialog(onDismissRequest={if(!busy)confirm=false},title={Text("${if(published)"Terbitkan"else "Simpan draft"} ${draft.versionName}?")},text={Text("Code ${draft.versionCode} · ${draft.bytes/1_000_000} MB · ${draft.channel}\n${draft.url}\n\n${draft.notes.take(500)}")},confirmButton={TextButton({busy=true;scope.launch {try {
        check(vm.auth.account.value?.id==owner && vm.admin.access().allowed){"Akses admin berubah."};val token=vm.auth.token();check(vm.auth.account.value?.id==owner)
        vm.auth.request("/rest/v1/rpc/save_app_release",buildJsonObject {put("p_version_code",draft.versionCode);put("p_version_name",draft.versionName);put("p_download_url",draft.url);put("p_sha256",draft.sha256);put("p_bytes",draft.bytes);put("p_notes",draft.notes);put("p_channel",draft.channel);put("p_min_sdk",draft.minSdk);put("p_published",published)},token)
        success=true;confirm=false;error=null
    }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error="Rilis belum tersimpan. Periksa akses admin dan pemasangan SQL pembaruan.";confirm=false}finally{busy=false}}},enabled=!busy){Text(tr("Simpan","Save"))}},dismissButton={TextButton({confirm=false},enabled=!busy){Text(tr("Batal","Cancel"))}})
}
