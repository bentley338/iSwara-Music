package id.iswara.music.data

import android.content.Context
import java.util.UUID

object DeviceIdentity {
    @Synchronized fun id(context: Context): String {
        val preferences = context.getSharedPreferences("installation", Context.MODE_PRIVATE)
        return preferences.getString("id", null) ?: UUID.randomUUID().toString().also { preferences.edit().putString("id", it).commit() }
    }
}
