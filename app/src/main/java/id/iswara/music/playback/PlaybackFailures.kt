@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

enum class Recovery { AUDIO, REFRESH_URL, RETRY, SKIP, STOP }
object PlaybackFailures {
    fun recovery(error: PlaybackException): Recovery = when (error.errorCode) {
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> Recovery.AUDIO
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val response = generateSequence(error as Throwable) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
            when (response) { 401, 403, 410 -> Recovery.REFRESH_URL; 404 -> Recovery.SKIP; 429, in 500..599 -> Recovery.RETRY; else -> Recovery.STOP }
        }
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> Recovery.RETRY
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> Recovery.SKIP
        else -> Recovery.STOP
    }
    fun message(error: PlaybackException): String = when (recovery(error)) {
        Recovery.AUDIO -> "Keluaran audio gagal. Coba putar lagi atau matikan efek audio."
        Recovery.REFRESH_URL -> "Akses audio kedaluwarsa atau dibatasi. Coba putar lagi."
        Recovery.RETRY -> "Koneksi audio terputus. Periksa jaringan lalu coba lagi."
        Recovery.SKIP -> "Audio lagu ini tidak tersedia atau belum didukung."
        Recovery.STOP -> "Pemutaran terhenti. Coba putar lagi."
    } + " (${error.errorCodeName})"

    // Exception messages can contain signed CDN URLs. Export only types and source locations.
    fun report(error: PlaybackException): String = buildString {
        appendLine("iSwara ${id.iswara.music.BuildConfig.VERSION_NAME}")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} / SDK ${android.os.Build.VERSION.SDK_INT}")
        appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine(error.errorCodeName)
        generateSequence(error as Throwable) { it.cause }.take(5).forEach { cause ->
            appendLine(cause.javaClass.name)
            cause.stackTrace.take(8).forEach { appendLine("  $it") }
        }
    }
}
