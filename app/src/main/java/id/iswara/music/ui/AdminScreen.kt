package id.iswara.music.ui

import androidx.compose.foundation.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.iswara.music.cloud.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Composable fun AdminScreen(vm: MusicViewModel, owner: String, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contentQuery by remember { mutableStateOf("") }
    var contentStatus by remember { mutableStateOf("all") }
    var auditQuery by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Pair<Boolean,JsonObject>?>(null) }
    var publishing by remember { mutableStateOf<Pair<Boolean,JsonObject>?>(null) }
    var userDetails by remember { mutableStateOf<JsonObject?>(null) }
    var auditDetails by remember { mutableStateOf<JsonObject?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var csvExport by remember { mutableStateOf("") }
    val exportAudit = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.action {
            val access = vm.admin.access()
            check(access.allowed && access.owner == owner && vm.auth.account.value?.id == owner) { "Akses admin berubah. Ekspor dibatalkan." }
            val csv = csvExport
            check(csv.isNotBlank())
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csv) } ?: error("Berkas tidak dapat ditulis.") }
            notice = "Audit berhasil diekspor"; csvExport = ""
        }
    }
    var tab by remember { mutableIntStateOf(0) }
    var releases by remember {mutableStateOf(false)}
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<JsonObject?>(null) }
    var announcements by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var featured by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var audit by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var users by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var editing by remember { mutableStateOf<Pair<Boolean, JsonObject?>?>(null) }
    var removing by remember { mutableStateOf<Pair<Boolean, JsonObject>?>(null) }
    var userAction by remember { mutableStateOf<Pair<String, JsonObject>?>(null) }
    fun launch(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; notice = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Panel belum tersambung."; vm.refreshAdmin() }
            finally { busy = false }
        }
    }
    suspend fun reload() {
        when (tab) {
            0 -> summary = vm.admin.summary(owner)
            1 -> { announcements = vm.admin.content(owner, false); featured = vm.admin.content(owner, true) }
            2 -> users = vm.admin.users(owner, query, page)
            3 -> audit = vm.admin.audit(owner)
        }
    }
    LaunchedEffect(tab, page) { launch { reload() } }
    if(releases)AdminReleaseScreen(vm,owner){releases=false}
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Panel admin", Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.titleLarge)
                    ActionIcon(Icons.Rounded.SystemUpdate,"Rilis aplikasi",{releases=true})
                    ActionIcon(Icons.Rounded.Refresh, "Muat ulang", { launch { reload() } }, enabled = !busy)
                    ActionIcon(Icons.Rounded.Close, "Tutup admin", close)
                }
                ScrollableTabRow(tab, edgePadding = 12.dp) { listOf("Ringkasan", "Beranda", "Pengguna", "Aktivitas").forEachIndexed { index, label -> Tab(tab == index, { if (!busy) tab = index }, text = { Text(label) }) } }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
                notice?.let { Text(it,Modifier.padding(horizontal=20.dp,vertical=8.dp),color=MaterialTheme.colorScheme.primary) }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tab) {
                        0 -> {
                            item { Text("Data cloud", style = MaterialTheme.typography.headlineSmall); Text("Jumlah terbaru dari server.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            val values = summary
                            if (values != null) items(listOf("accounts" to "Akun", "verified" to "Email terverifikasi", "suspended" to "Akun ditangguhkan", "playlists" to "Playlist", "favorites" to "Favorit", "history" to "Riwayat tersimpan")) { (key, label) ->
                                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(values.text(key), style = MaterialTheme.typography.titleLarge) } }
                            }
                        }
                        1 -> {
                            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button({ editing = false to null }, enabled = !busy) { Text("Buat pengumuman") }
                                OutlinedButton({ editing = true to null }, enabled = !busy) { Text("Tambah pilihan musik") }
                            } }
                            item {
                                OutlinedTextField(contentQuery,{contentQuery=it.take(120)},label={Text("Cari konten Beranda")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("all" to "Semua","published" to "Terbit","draft" to "Draf").forEach { (key,label) -> FilterChip(contentStatus==key,{contentStatus=key},{Text(label)}) } }
                            }
                            val shownAnnouncements = announcements.filter { AdminTools.matches(it,contentQuery,contentStatus) }
                            val shownFeatured = featured.filter { AdminTools.matches(it,contentQuery,contentStatus) }
                            item { Text("Pengumuman (${shownAnnouncements.size})", style = MaterialTheme.typography.titleLarge) }
                            items(shownAnnouncements, key = { "notice:${it.text("id")}" }) { row -> AdminContentRow(row, !busy,
                                edit={editing=false to row},delete={removing=false to row},preview={preview=false to row},publish={publishing=false to row},duplicate={launch {vm.admin.save(owner,false,null,AdminTools.draft(false,row));reload();notice="Salinan disimpan sebagai draf"}}) }
                            item { Text("Pilihan musik (${shownFeatured.size})", style = MaterialTheme.typography.titleLarge) }
                            items(shownFeatured, key = { "featured:${it.text("id")}" }) { row -> AdminContentRow(row, !busy,
                                edit={editing=true to row},delete={removing=true to row},preview={preview=true to row},publish={publishing=true to row},duplicate={launch {vm.admin.save(owner,true,null,AdminTools.draft(true,row));reload();notice="Salinan disimpan sebagai draf"}}) }
                            if (!busy && shownAnnouncements.isEmpty() && shownFeatured.isEmpty() && (contentQuery.isNotBlank() || contentStatus!="all")) item {Text("Tidak ada konten yang cocok dengan filter.") }
                            if (!busy && announcements.isEmpty() && featured.isEmpty()) item { Text("Belum ada konten. Simpan sebagai draf atau terbitkan langsung.") }
                        }
                        2 -> {
                            item { OutlinedTextField(query, { query = it.take(100) }, label = { Text("Cari email pengguna") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                TextButton({ if (page == 1) launch { reload() } else page = 1 }, enabled = !busy) { Text("Cari") } }
                            items(users, key = { it.text("id") }) { row ->
                                val protected = row.flag("protected_admin") || row.text("id") == owner
                                val suspended = runCatching { java.time.Instant.parse(row.text("banned_until")).isAfter(java.time.Instant.now()) }.getOrDefault(false)
                                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(row.text("email").ifBlank { "Tanpa email" }, style = MaterialTheme.typography.titleSmall)
                                    Text(if (protected) "Admin · dilindungi" else if (suspended) "Ditangguhkan" else "Aktif", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Bergabung: ${row.text("created_at").take(10)}", style = MaterialTheme.typography.bodySmall)
                                    TextButton({userDetails=row},enabled=!busy) {Text("Detail pengguna")}
                                    if (!protected) FlowRow { TextButton({ userAction = (if (suspended) "restore" else "suspend") to row }, enabled = !busy) { Text(if (suspended) "Aktifkan kembali" else "Tangguhkan 30 hari") }; TextButton({ userAction = "delete" to row }, enabled = !busy) { Text("Hapus akun", color = MaterialTheme.colorScheme.error) } }
                                } }
                            }
                            if (users.isEmpty() && !busy) item { Text("Tidak ada pengguna yang cocok.") }
                            item { Row(verticalAlignment = Alignment.CenterVertically) { TextButton({ page-- }, enabled = page > 1 && !busy) { Text("Sebelumnya") }; Text("Halaman $page"); TextButton({ page++ }, enabled = users.size == 50 && !busy) { Text("Berikutnya") } } }
                        }
                        3 -> {
                            item {
                                Text("100 aktivitas terbaru", style=MaterialTheme.typography.titleLarge)
                                OutlinedTextField(auditQuery,{auditQuery=it.take(120)},label={Text("Cari tindakan, jenis, atau UID")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                TextButton({csvExport=AdminTools.auditCsv(audit);exportAudit.launch("iswara-admin-audit-${java.time.LocalDate.now()}.csv")},enabled=!busy && audit.isNotEmpty()) {Icon(Icons.Rounded.FileDownload,null);Text("Ekspor audit yang dimuat (CSV)")}
                            }
                            val visibleAudit = audit.filter { row -> listOf("action","entity","entity_id","actor","target_user").any { row.text(it).contains(auditQuery.trim(),true) } }
                            items(visibleAudit, key = { it.text("id") }) { row -> ListItem(headlineContent = { Text(row.text("action")) }, supportingContent = { Text("${row.text("created_at")}\n${row.text("entity")}") },trailingContent={Icon(Icons.Rounded.ChevronRight,null)},modifier=Modifier.clickable {auditDetails=row}) }
                            if (visibleAudit.isEmpty() && audit.isNotEmpty()) item {Text("Tidak ada aktivitas yang cocok.")}
                            if (audit.isEmpty() && !busy) item { Text("Belum ada aktivitas admin.") }
                        }
                    }
                }
            }
        }
    }
    preview?.let { (isFeatured,row) -> AdminPreview(isFeatured,row) {preview=null} }
    publishing?.let { (isFeatured,row) -> ConfirmDialog(if(row.flag("published"))"Sembunyikan dari Beranda?" else "Terbitkan ke Beranda?",row.text("title"),{publishing=null}) {
        publishing=null
        launch {vm.admin.save(owner,isFeatured,row.text("id"),buildJsonObject {put("published",!row.flag("published"))},row.text("modified_at"));reload();vm.refreshContent();notice="Status konten diperbarui"}
    } }
    userDetails?.let { row -> AlertDialog(onDismissRequest={userDetails=null},title={Text("Detail pengguna")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(row.text("email"),style=MaterialTheme.typography.titleMedium)
        Text("UID: ${row.text("id")}")
        Text("Email: ${if(row.text("email_confirmed_at").isNotBlank()) "Terverifikasi" else "Belum terverifikasi"}")
        Text("Bergabung: ${row.text("created_at")}")
        Text("Masuk terakhir: ${row.text("last_sign_in_at").ifBlank {"Belum tercatat"}}")
        TextButton({context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("iSwara Music UID",row.text("id")));notice="UID disalin"}) {Text("Salin UID")}
    }},confirmButton={TextButton({userDetails=null}) {Text("Tutup")}}) }
    auditDetails?.let { row -> AlertDialog(onDismissRequest={auditDetails=null},title={Text("Detail aktivitas")},text={androidx.compose.foundation.text.selection.SelectionContainer {Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("created_at" to "Waktu","action" to "Tindakan","entity" to "Jenis","entity_id" to "ID konten","actor" to "Pelaku","target_user" to "Target").forEach { (key,label) -> Text("$label: ${row.text(key).ifBlank {"-"}}") }
        Text("Hasil: ${(row["detail"] as? JsonObject)?.text("result").orEmpty().ifBlank {"-"}}")
    }}},confirmButton={TextButton({auditDetails=null}) {Text("Tutup")}}) }
    editing?.let { (isFeatured, row) -> AdminEditor(isFeatured, row, busy, error, { editing = null }) { body ->
        launch { vm.admin.save(owner, isFeatured, row?.text("id"), body,row?.text("modified_at")); editing = null; reload(); vm.refreshContent(); notice="Konten disimpan" }
    } }
    removing?.let { (isFeatured, row) -> ConfirmDialog("Hapus konten?", row.text("title"), { removing = null }) {
        removing = null; launch { vm.admin.remove(owner, isFeatured, row.text("id"),row.text("modified_at")); reload(); vm.refreshContent() }
    } }
    userAction?.let { (action, row) ->
        var confirmation by remember(row.text("id"), action) { mutableStateOf("") }
        val isDelete = action == "delete"
        AlertDialog(onDismissRequest = { userAction = null }, title = { Text(when (action) { "delete" -> "Hapus akun permanen?"; "suspend" -> "Tangguhkan 30 hari?"; else -> "Aktifkan akun kembali?" }) }, text = {
            Column { Text(row.text("email")); Text(if (isDelete) "Akun dan koleksi cloud pengguna akan dihapus. Ketik email lengkap untuk melanjutkan." else if (action == "suspend") "Akses akun dan sinkronisasi akan dihentikan selama 30 hari." else "Pengguna dapat masuk dan menyinkronkan koleksinya kembali.")
                if (isDelete) OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Ketik email pengguna") }) }
        }, confirmButton = { TextButton({ userAction = null; launch { vm.admin.userAction(owner, row.text("id"), action, confirmation); reload() } }, enabled = !busy && (!isDelete || confirmation.equals(row.text("email"), true))) { Text("Konfirmasi") } }, dismissButton = { TextButton({ userAction = null }) { Text("Batal") } })
    }
}

@Composable internal fun AdminContentRow(row: JsonObject, enabled: Boolean, edit: () -> Unit, delete: () -> Unit, preview: () -> Unit, publish: () -> Unit, duplicate: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(row.text("title"), style = MaterialTheme.typography.titleMedium)
            Text("${if (row.flag("published")) "Terbit" else "Draf"} \u00b7 Urutan ${row.text("position")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(preview,enabled=enabled) {Text("Pratinjau")}
                TextButton(edit,enabled=enabled) {Text("Edit")}
                TextButton(publish,enabled=enabled) {Text(if(row.flag("published"))"Sembunyikan" else "Terbitkan")}
                TextButton(duplicate,enabled=enabled) {Text("Duplikasi ke draf")}
                TextButton(delete,enabled=enabled) {Text("Hapus",color=MaterialTheme.colorScheme.error)}
            }
        }
    }
}

@Composable private fun AdminEditor(featured: Boolean, row: JsonObject?, busy: Boolean, error: String?, close: () -> Unit, save: (JsonObject) -> Unit) {
    var title by remember { mutableStateOf(row?.text("title").orEmpty()) }
    var body by remember { mutableStateOf(row?.text(if (featured) "subtitle" else "body").orEmpty()) }
    var url by remember { mutableStateOf(row?.text("url").orEmpty()) }
    var art by remember { mutableStateOf(row?.text("artwork").orEmpty()) }
    var position by remember { mutableStateOf(row?.text("position") ?: "0") }
    var published by remember { mutableStateOf(row?.flag("published") ?: false) }
    var previewOpen by remember {mutableStateOf(false)}
    fun values() = buildJsonObject { put("title",title.trim());put(if(featured)"subtitle" else "body",body.trim());put("position",position.toIntOrNull() ?: 0);put("published",published);if(featured){put("url",url.trim());put("artwork",art.trim())} }
    if(previewOpen) AdminPreview(featured,values()) {previewOpen=false}
    val validUrl = runCatching { val u = java.net.URI(url); u.scheme == "https" && u.host in setOf("youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com", "youtu.be") }.getOrDefault(false)
    AlertDialog(onDismissRequest = {if(!busy)close()}, title = { Text(if (featured) "Pilihan musik" else "Pengumuman") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            OutlinedTextField(title, { title = it.take(if (featured) 100 else 80) }, label = { Text("Judul") },enabled=!busy)
            OutlinedTextField(body, { body = it.take(if (featured) 200 else 600) }, label = { Text(if (featured) "Deskripsi singkat" else "Isi pengumuman") },enabled=!busy)
            if (featured) { OutlinedTextField(url, { url = it.take(1000) }, label = { Text("Tautan lagu / playlist") }, supportingText = { Text("Gunakan tautan katalog musik yang didukung.") },enabled=!busy); OutlinedTextField(art, { art = it.take(2000) }, label = { Text("URL sampul (opsional)") },enabled=!busy) }
            OutlinedTextField(position, { position = it.filter(Char::isDigit).take(4) }, enabled=!busy,label = { Text("Urutan 0–1000") })
            TextButton({previewOpen=true},enabled=!busy) {Icon(Icons.Rounded.Preview,null);Text("Pratinjau sebelum simpan")}
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(published, { published = it },enabled=!busy); Text("Terbitkan", Modifier.padding(start = 12.dp)) }
        }
    }, confirmButton = { TextButton({ save(buildJsonObject {
        put("title", title.trim()); put(if (featured) "subtitle" else "body", body.trim()); put("position", position.toInt()); put("published", published)
        if (featured) { put("url", url.trim()); put("artwork", art.trim()) }
    }) }, enabled = !busy && title.isNotBlank() && (featured || body.isNotBlank()) && position.toIntOrNull() in 0..1000 && (!featured || (validUrl && (art.isBlank() || art.startsWith("https://"))))) { Text("Simpan") } }, dismissButton = { TextButton(close,enabled=!busy) { Text("Batal") } })
}
