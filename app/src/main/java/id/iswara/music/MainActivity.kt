package id.iswara.music

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import id.iswara.music.ui.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: MusicViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings = model.settings.collectAsStateWithLifecycle().value
            IswaraTheme(settings.theme, settings.language,settings.accent,settings.animations) { MusicApp(model) }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    private fun handle(intent: Intent?) {
        if (intent?.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            model.playSearch(intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty())
            return
        }
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let(model::shared)
        intent?.data?.takeIf { it.scheme == "iswara" }?.let { if (it.host == "auth") model.callback(it) else if(it.host=="updates")model.updatesOpen.value=true else model.shared(it.toString()) }
    }
}
