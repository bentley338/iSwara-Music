package id.iswara.music.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import id.iswara.music.playback.MusicWidgets

@Composable fun WidgetGallery(onDismiss:()->Unit) {
    val context=LocalContext.current
    val manager=remember {AppWidgetManager.getInstance(context)}
    var status by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr("Widget layar utama","Home screen widgets"))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf(tr("Minimal transparan","Transparent minimal"),"Retro Classic","Vinyl").forEachIndexed {style,label ->
                Text(label,style=MaterialTheme.typography.titleMedium)
                AndroidView(factory={ctx -> MusicWidgets.views(ctx,style,playing=false).apply(ctx,null).also {root ->
                    fun disable(view:android.view.View) {view.isClickable=false;view.isEnabled=false;if(view is android.view.ViewGroup)for(i in 0 until view.childCount)disable(view.getChildAt(i))};disable(root)
                }},modifier=Modifier.fillMaxWidth().height(if(style==1)280.dp else if(style==2)180.dp else 144.dp))
                OutlinedButton({
                    status=if(manager.isRequestPinAppWidgetSupported) {
                        val accepted=manager.requestPinAppWidget(ComponentName(context,MusicWidgets.types[style]),null,null)
                        if(accepted) "Konfirmasi pemasangan di layar utama." else "Tekan lama layar utama, pilih Widget lalu iSwara."
                    } else "Tekan lama layar utama, pilih Widget lalu iSwara."
                },modifier=Modifier.fillMaxWidth()) {Text(tr("Tambahkan $label","Add $label"))}
            }
            status?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={TextButton(onDismiss){Text(tr("Tutup","Close"))}})
}
