package id.iswara.music.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.iswara.music.R
import id.iswara.music.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun TranslationAttribution() {
    val context=LocalContext.current
    val dark=MaterialTheme.colorScheme.surface.luminance()<.3f
    Image(painterResource(if(dark)R.drawable.google_translate_attribution_white else R.drawable.google_translate_attribution),"powered by Google Translate",
        modifier=Modifier.width(230.dp).height(26.dp).clickable {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://translate.google.com/")))})
}

@Composable fun SmartLyricsSettings(vm:MusicViewModel,close:()->Unit) {
    val settings=vm.settings.collectAsStateWithLifecycle().value
    val smart=vm.smartLyrics.state.collectAsStateWithLifecycle().value
    var confirmNetwork by remember {mutableStateOf(false)}
    var languagePicker by remember {mutableStateOf(false)}
    var targetPicker by remember {mutableStateOf(false)}
    if(languagePicker)LanguagePicker(settings.smartLanguage,true,{languagePicker=false}){vm.action {vm.preferences.set("smartLanguage",it)}}
    if(targetPicker)LanguagePicker(settings.smartTarget,false,{targetPicker=false}){vm.action {vm.preferences.set("smartTarget",it)}}
    Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp).heightIn(max=820.dp),shape=MaterialTheme.shapes.extraLarge) {
            Column {
                PageHeading(tr("Lirik pintar","Smart lyrics"),tr("Cara baca, arti baris, dan arti kata.","Readings, line translations, and word meanings.")){ActionIcon(Icons.Rounded.Close,tr("Tutup lirik pintar","Close smart lyrics"),close)}
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
                    item {SettingToggle(tr("Tampilkan cara baca","Show romanization"),tr("Romaji, romanisasi Korea, dan pinyin di atas teks asli.","Romaji, Korean romanization, and pinyin above the original text."),settings.smartRomanize){vm.action {vm.preferences.toggle("smartRomanize",it)}}}
                    item {SettingChoice(tr("Tema lirik","Lyrics theme"),settings.lyricsTheme,LyricsVisuals.themes){vm.action {vm.preferences.set("lyricsTheme",it)}}}
                    item {SettingChoice(tr("Gerak baris aktif","Active line movement"),settings.lyricsStyle,listOf("auto" to tr("Beda tiap lagu","Per song"))+LyricsVisuals.styles){vm.action {vm.preferences.set("lyricsStyle",it)}}}
                    item {MenuAction(Icons.Rounded.Language,tr("Bahasa lirik: ","Lyric language: ")+TranslationLanguages.label(settings.smartLanguage,LocalEnglish.current)){languagePicker=true}}
                    item {MenuAction(Icons.Rounded.Translate,tr("Terjemahkan ke: ","Translate to: ")+TranslationLanguages.label(settings.smartTarget,LocalEnglish.current)){targetPicker=true}}
                    item {SettingToggle(tr("Tampilkan terjemahan","Show translations"),tr("Terjemahkan baris dengan konteks dan tampilkan di bawah teks asli.","Translate lines with context below the original text."),settings.smartTranslate){enabled->if(enabled && !settings.translationConsent)confirmNetwork=true else vm.action {vm.preferences.toggle("smartTranslate",enabled)}}}
                    item {SettingToggle(tr("Terjemahkan hanya lewat Wi-Fi","Translate on Wi-Fi only"),tr("Batasi pengiriman terjemahan ke jaringan Wi-Fi.","Limit translation requests to Wi-Fi networks."),settings.translationWifi){vm.action {vm.preferences.toggle("translationWifi",it)}}}
                    item {Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(tr("Aktifkan cara baca atau terjemahan, lalu ketuk kata pada lirik untuk melihat artinya. Cara baca nama dan terjemahan kata dapat bergantung pada konteks.","Enable readings or translation, then tap a lyric word for its meaning. Name readings and word meanings can depend on context."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tr("Cara baca dihitung di perangkat. Terjemahan membutuhkan internet dan mengirim teks ke Google Translate setelah kamu mengaktifkannya.","Readings are computed on your device. Translation needs internet and sends text to Google Translate after you enable it."),style=MaterialTheme.typography.bodySmall)
                        if(smart.busy){Text(smart.stage);LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(vm.smartLyrics::cancel){Text(tr("Batalkan proses lirik","Cancel lyric processing"))}}
                        smart.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                        OutlinedButton({if(!settings.translationConsent)confirmNetwork=true else {vm.action {vm.preferences.toggle("smartTranslate",true)};vm.smartLyrics.translate(settings.translationWifi,settings.smartTarget)}},enabled=smart.rows.isNotEmpty() && !smart.busy){Text("Translate with Google")}
                        TranslationAttribution()
                        Text(tr("Terjemahan mesin dapat kurang tepat sesuai konteks.","Machine translation may be inaccurate depending on context."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }}
                }
            }
        }
    }
    if(confirmNetwork)AlertDialog(onDismissRequest={confirmNetwork=false},title={Text(tr("Aktifkan terjemahan?","Enable translation?"))},text={Text(tr("Teks lirik dikirim ke Google Translate saat terjemahan aktif, termasuk lagu berikutnya. Cara baca tetap lokal. Kamu dapat mematikannya kapan saja.","Lyric text is sent to Google Translate while translation is enabled, including subsequent songs. Readings stay local. You can turn this off at any time."))},confirmButton={TextButton({confirmNetwork=false;vm.action {vm.preferences.toggle("translationConsent",true);vm.preferences.toggle("smartTranslate",true)}}){Text(tr("Aktifkan","Enable"))}},dismissButton={TextButton({confirmNetwork=false}){Text(tr("Batal","Cancel"))}})
}

@Composable fun SmartLyricLine(row:SmartLine,translation:String?,active:Boolean,romanize:Boolean,onWord:(LyricToken)->Unit,seek:(()->Unit)?) {
    val motion=LocalAppMotion.current && LocalLyricsMotion.current
    val style=LocalLyricsStyle.current
    val progress=remember {Animatable(0f)}
    LaunchedEffect(active,motion,style){if(motion)progress.animateTo(if(active)1f else 0f,tween(280))else progress.snapTo(if(active)1f else 0f)}
    val scale=1f+progress.value*when(style){"zoom"->.065f;"minimal"->0f;else->.025f}
    val color=if(active && style=="glow")MaterialTheme.colorScheme.primary else if(active)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val pressed=MaterialTheme.colorScheme.surfaceContainerHighest
    val text=remember(row,onWord,color,pressed){buildAnnotatedString {
        var cursor=0
        row.words.forEachIndexed {index,word->
            if(word.start>=cursor && word.end<=row.text.length){append(row.text.substring(cursor,word.start));withLink(LinkAnnotation.Clickable("word:$index",styles=TextLinkStyles(style=SpanStyle(color=color,textDecoration=TextDecoration.None),focusedStyle=SpanStyle(textDecoration=TextDecoration.Underline),pressedStyle=SpanStyle(background=pressed)),linkInteractionListener={onWord(word)})){append(word.text)};cursor=word.end}
        }
        append(row.text.substring(cursor))
    }}
    Row(Modifier.fillMaxWidth().background(if(style=="spotlight")MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha=progress.value*.6f)else androidx.compose.ui.graphics.Color.Transparent,MaterialTheme.shapes.medium).semantics {selected=active}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(seek!=null)IconButton(seek,modifier=Modifier.size(48.dp)){Icon(if(active)Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,tr("Putar dari baris lirik","Seek to lyric"),tint=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
        else Icon(Icons.Rounded.Lyrics,null,Modifier.size(48.dp).padding(12.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).graphicsLayer {scaleX=scale;scaleY=scale;translationX=if(style=="slide")progress.value*12 else 0f;translationY=if(style=="rise")-progress.value*8 else 0f;transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,.5f)},verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(romanize && row.reading.isNotBlank() && row.reading!=row.text)Text(row.reading,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
            Text(text,style=MaterialTheme.typography.headlineMedium.copy(fontWeight=if(active)FontWeight.Bold else FontWeight.Medium),color=color)
            if(!translation.isNullOrBlank())Text(translation,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun WordMeaningDialog(vm:MusicViewModel) {
    val word=vm.smartLyrics.word.collectAsStateWithLifecycle().value
    val settings=vm.settings.collectAsStateWithLifecycle().value
    if(word.text.isBlank())return
    AlertDialog(onDismissRequest=vm.smartLyrics::clearWord,title={Text(word.text)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(word.reading.isNotBlank() && word.reading!=word.text)Text(word.reading,color=MaterialTheme.colorScheme.primary)
        if(word.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(word.meaning.isNotBlank()){Text(word.meaning,style=MaterialTheme.typography.titleLarge);TranslationAttribution()}
        word.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(word.meaning.isBlank())Text(tr("Kata ini akan dikirim ke Google Translate saat kamu menekan tombol terjemahkan.","This word will be sent to Google Translate when you press Translate."),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton({vm.smartLyrics.translateWord(settings.translationWifi,settings.smartTarget)},enabled=!word.busy){Text("Translate with Google")}},dismissButton={TextButton(vm.smartLyrics::clearWord){Text(tr("Tutup","Close"))}})
}
