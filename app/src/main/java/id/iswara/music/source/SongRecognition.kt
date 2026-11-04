package id.iswara.music.source

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import id.iswara.music.BuildConfig
import id.iswara.music.model.AppJson
import id.iswara.music.playback.StemDecoder
import id.iswara.music.playback.StemDsp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class RecognizedSong(val title: String, val artist: String, val album: String)
internal enum class RecognitionIssue { UNAVAILABLE, LIMIT, NETWORK, NO_AUDIO, STOPPED, PERMISSION, FILE }
internal class RecognitionFailure(val issue: RecognitionIssue) : IOException()

internal object SongRecognition {
    private val client = OkHttpClient.Builder().followRedirects(false).callTimeout(22, TimeUnit.SECONDS).build()

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    suspend fun microphone(context: Context): ByteArray = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val file = File.createTempFile("recognition-", ".m4a", context.cacheDir)
        try {
            val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(96_000)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                recorder.start()
                delay(10_000)
                recorder.stop()
            } finally {
                recorder.release()
            }
            file.readBytes()
        } finally {
            file.delete()
        }
    }

    suspend fun file(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val output = File.createTempFile("recognition-", ".pcm", context.cacheDir)
        val job = currentCoroutineContext()
        try {
            val frames = StemDecoder.clip(context, uri, output) { job.ensureActive() }
            require(frames >= StemDsp.RATE) { "Audio terlalu pendek." }
            require(output.length() in 1..2_000_000)
            val pcm = output.readBytes()
            wav(pcm, StemDsp.RATE, 2)
        } finally {
            output.delete()
        }
    }

    internal fun wav(pcm: ByteArray, rate: Int, channels: Int): ByteArray {
        require(rate in 8_000..48_000 && channels in 1..2 && pcm.size % (channels * 2) == 0)
        return ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + pcm.size)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1); putShort(channels.toShort()); putInt(rate); putInt(rate * channels * 2)
            putShort((channels * 2).toShort()); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(pcm.size); put(pcm)
        }.array()
    }

    private fun request(): Request.Builder {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (!url.startsWith("https://") || key.isBlank()) throw RecognitionFailure(RecognitionIssue.UNAVAILABLE)
        return Request.Builder().url("$url/functions/v1/recognize-song").header("apikey", key).apply {
            if (key.startsWith("eyJ")) header("Authorization", "Bearer $key")
        }
    }

    suspend fun available() { execute(request().head().build()) { Unit } }

    suspend fun identify(audio: ByteArray, microphone: Boolean): RecognizedSong? {
        require(audio.size in 1..2_000_044)
        val type = if (microphone) "audio/mp4" else "audio/wav"
        return execute(request().post(audio.toRequestBody(type.toMediaType())).build()) {
            val source = it.body?.source() ?: error("Empty recognition response")
            require(!source.request(65_537)) { "Recognition response too large" }
            parse(source.readUtf8())
        }
    }

    private suspend fun <T> execute(request: Request, read: (Response) -> T): T = suspendCancellableCoroutine { result ->
            val call = client.newCall(request)
            result.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = result.resumeWithException(e)
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            if (!it.isSuccessful) throw RecognitionFailure(when (it.code) {
                                429 -> RecognitionIssue.LIMIT
                                401, 403, 404, 503 -> RecognitionIssue.UNAVAILABLE
                                else -> RecognitionIssue.NETWORK
                            })
                            read(it)
                        }
                        result.resume(value)
                    } catch (error: Exception) {
                        result.resumeWithException(error)
                    }
                }
            })
        }
    internal fun parse(json: String): RecognizedSong? {
        val root = AppJson.parseToJsonElement(json) as? JsonObject ?: error("Respons tidak valid.")
        fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty().trim().take(200)
        check(root.text("status") == "success") { "Recognition failed" }
        val song = root["result"] as? JsonObject ?: return null
        val title = song.text("title")
        val artist = song.text("artist")
        if (title.isBlank() || artist.isBlank()) return null
        return RecognizedSong(title, artist, song.text("album"))
    }
}
