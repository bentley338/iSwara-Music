package id.iswara.music.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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

@Composable fun AccountScreen(vm: MusicViewModel, onSettings: () -> Unit) {
    val account = vm.auth.account.collectAsStateWithLifecycle().value
    val sync = vm.sync.status.collectAsStateWithLifecycle().value
    val busy = vm.authBusy.collectAsStateWithLifecycle().value
    val access = vm.adminAccess.collectAsStateWithLifecycle().value
    var adminOpen by remember(account?.id) { mutableStateOf(false) }
    LaunchedEffect(account?.id) { vm.refreshAdmin() }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("account-list"), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { PageHeading(tr("Akun", "Account"), account?.email ?: tr("Dengarkan dengan caramu.", "Listen your way.")) }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!vm.auth.configured) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                        Text(tr("Koleksimu tersimpan di perangkat", "Your library is saved on this device"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Layanan akun cloud belum tersedia di versi ini. Streaming, playlist, favorit, dan backup tetap dapat digunakan.", "Cloud accounts are not available in this version. Streaming, playlists, favorites, and backups remain available."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                } else if (account == null) {
                    Text(if (register) tr("Buat akun iSwara Music", "Create an iSwara Music account") else tr("Masuk ke iSwara Music", "Sign in to iSwara Music"), style = MaterialTheme.typography.titleLarge)
                    Text(tr("Sinkronkan playlist dan favorit antarperangkat. Koleksi tamu tetap terpisah; gunakan backup untuk memindahkannya.", "Sync playlists and favorites across devices. Guest collections remain separate; use backups to move them."), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy)
                    OutlinedTextField(password, { password = it }, label = { Text(tr("Password", "Password")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { ActionIcon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, tr("Tampilkan atau sembunyikan password", "Show or hide password"), { visible = !visible }) }, enabled = !busy)
                    Button({ vm.login(email, password, register) }, enabled = !busy && email.contains('@') && password.length >= 8, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (register) tr("Daftar akun", "Create account") else tr("Masuk", "Sign in")) }
                    FlowRow { TextButton({ register = !register }, enabled = !busy) { Text(if (register) tr("Sudah punya akun", "Already have an account") else tr("Buat akun", "Create account")) }; TextButton({ vm.reset(email) }, enabled = !busy && email.contains('@')) { Text(tr("Lupa password", "Forgot password")) } }
                } else {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (sync == "Koleksi tersinkron") Icons.Rounded.CloudDone else Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.primary)
                        Text(account!!.email, style = MaterialTheme.typography.titleMedium)
                        Text(sync.ifBlank { tr("Koleksi disimpan di perangkat dan menunggu sinkronisasi.", "Library saved on this device, waiting to sync.") }, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton({ vm.action { vm.sync.sync() } }) { Text(tr("Sinkronkan sekarang", "Sync now")) }
                    } }
                    TextButton({ vm.action { vm.auth.logout() } }) { Text(tr("Keluar dari akun", "Sign out")) }
                }
            }
        }
        if (access.allowed && access.owner == account?.id) item { MenuAction(Icons.Rounded.AdminPanelSettings, tr("Panel admin", "Admin panel"), { adminOpen = true }) }
        item { MenuAction(Icons.Rounded.Settings, tr("Buka setelan", "Open settings"), onSettings) }
        if (account != null) item { TextButton({ delete = true }, Modifier.padding(horizontal = 12.dp)) { Text(tr("Hapus akun dan data cloud", "Delete account and cloud data"), color = MaterialTheme.colorScheme.error) } }
    }
    if (adminOpen && access.allowed && access.owner == account?.id) AdminScreen(vm, access.owner) { adminOpen = false }
    if (delete) ConfirmDialog(tr("Hapus akun permanen?", "Permanently delete account?"), tr("Akun, playlist, favorit, dan riwayat cloud akan dihapus. Ekspor backup dahulu jika ingin menyimpannya.", "Your account, playlists, favorites, and cloud history will be deleted. Export a backup first if you want to keep them."), { delete = false }) { delete = false; vm.deleteAccount() }

}

@Composable fun SettingChoice(label: String, selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { choices.forEach { (key, value) -> FilterChip(selected == key, { onSelect(key) }, { Text(value) }) } }
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier = this.then(Modifier.semantics { contentDescription = label })
