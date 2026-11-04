package id.iswara.music

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import id.iswara.music.ui.*
import id.iswara.music.source.RecognitionEntry
import id.iswara.music.source.sharedTikTokLink

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
        if (intent?.action == "id.iswara.recognition.RESULT") {
            if (model.recognition.value == null) model.recognition.value = RecognitionEntry()
            return
        }
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type?.let { it.startsWith("audio/") || it.startsWith("video/") } == true) {
                val file = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                file?.takeIf { it.scheme == "content" }?.let { model.openRecognition(RecognitionEntry(attachment = it)) }
                return
            }
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            val link = text?.let(::sharedTikTokLink)
            if (link != null) model.openRecognition(RecognitionEntry(videoLink = link)) else text?.let(model::shared)
        }
        intent?.data?.takeIf { it.scheme == "iswara" }?.let { if (it.host == "auth") model.callback(it) else if(it.host=="updates")model.updatesOpen.value=true else model.shared(it.toString()) }
    }
}
