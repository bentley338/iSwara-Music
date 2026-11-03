package id.iswara.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import id.iswara.music.cloud.*
import kotlinx.serialization.json.JsonObject

@Composable internal fun AdminPreview(featured: Boolean, row: JsonObject, close: () -> Unit) {
    Dialog(close) {
        Surface(shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.widthIn(max=480.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Pratinjau Beranda",style=MaterialTheme.typography.titleLarge)
                Text("Pratinjau tidak menyimpan atau menerbitkan perubahan.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(featured) {
                    Art(row.text("artwork"),row.text("title"),Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(row.text("title"),style=MaterialTheme.typography.titleLarge)
                    Text(row.text("subtitle"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(row.text("title"),style=MaterialTheme.typography.titleMedium)
                        Text(row.text("body"),style=MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(close,Modifier.fillMaxWidth()) {Text("Tutup pratinjau")}
            }
        }
    }
}
