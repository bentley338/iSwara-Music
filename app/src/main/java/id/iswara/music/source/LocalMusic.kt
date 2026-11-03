package id.iswara.music.source

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import id.iswara.music.model.Track
import id.iswara.music.data.DeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalMusic @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Track>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID)
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val device = DeviceIdentity.id(context)
                result += Track("local:$device:$id", c.getString(1).orEmpty(), c.getString(2).orEmpty().replace("<unknown>", ""), ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), c.getLong(4)).toString(), c.getLong(3) / 1000, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(), true, device)
            }
        }
        result
    }
    suspend fun file(uri: Uri): Track = withContext(Dispatchers.IO) {
        var name = "Audio lokal"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) }
        val device = DeviceIdentity.id(context)
        Track("file:$device:$uri", name, "File lokal", url = uri.toString(), local = true, device = device)
    }
}
