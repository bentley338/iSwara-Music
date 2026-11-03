package id.iswara.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import id.iswara.music.model.Track

@Composable fun Art(url: String, title: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
        if (url.isNotEmpty()) {
            val context=androidx.compose.ui.platform.LocalContext.current
            val motion=LocalAppMotion.current
            val request=remember(url,motion,context){coil3.request.ImageRequest.Builder(context).data(url).apply {if(motion)crossfade(180)}.build()}
            AsyncImage(model = request, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
@Composable fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, enabled: Boolean = true) {
    IconButton(onClick, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { if (active) stateDescription = "Aktif" }, enabled = enabled) { Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
}
@Composable fun TrackRow(track: Track, onPlay: () -> Unit, onMenu: () -> Unit, active: Boolean = false, enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled=enabled,onClickLabel = tr("Putar", "Play"), onClick = onPlay).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(track.artwork, track.title, Modifier.size(54.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist.ifBlank { tr("Artis tidak diketahui", "Unknown artist") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else ActionIcon(Icons.Rounded.MoreHoriz, tr("Opsi lagu", "Track options"), onMenu)
    }
}
@Composable fun EmptyState(title: String, message: String, icon: ImageVector = Icons.Rounded.LibraryMusic, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) OutlinedButton(onAction) { Text(action) }
    }
}
@Composable fun LoadingRows() {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Memuat musik" })
        repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.fillMaxWidth(.8f).height(16.dp).background(MaterialTheme.colorScheme.surfaceContainer))
                Box(Modifier.fillMaxWidth(.5f).height(12.dp).background(MaterialTheme.colorScheme.surfaceContainer))
            }
        } }
    }
}
@Composable fun PageHeading(title: String, subtitle: String = "", action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() }); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        action?.invoke()
    }
}
fun timeLabel(ms: Long): String { val seconds = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(seconds / 60, seconds % 60) }
