package id.iswara.music.source

import android.net.Uri
import java.net.URI
import java.util.Locale

internal class RecognitionEntry(val attachment: Uri? = null, val videoLink: String? = null)

internal fun sharedTikTokLink(text: String): String? = Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE)
    .findAll(text.take(8_192)).firstNotNullOfOrNull { match ->
        val value = match.value.trimEnd(')', ']', '.', ',', ';', '!', '"', '\'')
        val url = runCatching { URI(value) }.getOrNull() ?: return@firstNotNullOfOrNull null
        val host = url.host?.lowercase(Locale.ROOT)
        value.takeIf { url.scheme.equals("https", true) && host in setOf("tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com") &&
            url.userInfo == null && url.port in setOf(-1, 443) && !url.path.isNullOrBlank() && url.path != "/" }
    }
