package id.iswara.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.iswara.music.source.TranslationLanguages

@Composable fun LanguagePicker(current:String,source:Boolean,close:()->Unit,choose:(String)->Unit) {
    var query by remember {mutableStateOf("")}
    val english=LocalEnglish.current
    val items=remember(query,source,english){(if(source)listOf("auto")else emptyList())+TranslationLanguages.codes.filter {it.contains(query,true) || TranslationLanguages.label(it,english).contains(query,true)}}
    Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {Surface(Modifier.fillMaxSize()) {Column(Modifier.safeDrawingPadding()) {
        PageHeading(if(source)tr("Bahasa lirik","Lyric language")else tr("Bahasa terjemahan","Translation language")){ActionIcon(Icons.Rounded.Close,tr("Tutup pilihan bahasa","Close languages"),close)}
        OutlinedTextField(query,{query=it.take(40)},label={Text(tr("Cari bahasa atau kode","Search language or code"))},singleLine=true,modifier=Modifier.fillMaxWidth().padding(20.dp))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
            items(items.distinct(),key={it}) {code->ListItem(headlineContent={Text(TranslationLanguages.label(code,english))},supportingContent={Text(code)},trailingContent={if(code==current)Icon(Icons.Rounded.Check,null)},modifier=Modifier.clickable {choose(code);close()})}
            if(TranslationLanguages.valid(query) && query !in items)item{TextButton({choose(query);close()},Modifier.fillMaxWidth()){Text(tr("Gunakan kode: ","Use code: ")+query)}}
            item{Text(tr("Pilihan mengikuti bahasa yang tersedia di layanan terjemahan. Kode khusus yang belum didukung akan menampilkan pesan.","Availability follows the translation service. Unsupported custom codes will show an error."),Modifier.padding(20.dp),style=MaterialTheme.typography.bodySmall)}
        }
    }}}
}
