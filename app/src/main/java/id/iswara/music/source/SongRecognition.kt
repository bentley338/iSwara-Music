package id.iswara.music.source

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
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
import okhttp3.MultipartBody
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
            ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + pcm.size)
                put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
                putShort(1); putShort(2); putInt(StemDsp.RATE); putInt(StemDsp.RATE * 4)
                putShort(4); putShort(16)
                put("data".toByteArray(Charsets.US_ASCII)); putInt(pcm.size); put(pcm)
            }.array()
        } finally {
            output.delete()
        }
    }

    suspend fun identify(audio: ByteArray, microphone: Boolean, token: String): RecognizedSong? {
        require(token.isNotBlank() && token.length <= 256)
        require(audio.size in 1..2_000_044)
        val type = if (microphone) "audio/mp4" else "audio/wav"
        val name = if (microphone) "clip.m4a" else "clip.wav"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("api_token", token)
            .addFormDataPart("file", name, audio.toRequestBody(type.toMediaType()))
            .build()
        val request = Request.Builder().url("https://api.audd.io/").post(body).build()
        return suspendCancellableCoroutine { result ->
            val call = client.newCall(request)
            result.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = result.resumeWithException(e)
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val song = response.use {
                            check(it.isSuccessful) { "Layanan pengenal lagu tidak tersedia." }
                            val source = it.body?.source() ?: error("Respons kosong.")
                            require(!source.request(65_537)) { "Respons terlalu besar." }
                            parse(source.readUtf8())
                        }
                        result.resume(song)
                    } catch (error: Exception) {
                        result.resumeWithException(error)
                    }
                }
            })
        }
    }

    internal fun parse(json: String): RecognizedSong? {
        val root = AppJson.parseToJsonElement(json) as? JsonObject ?: error("Respons tidak valid.")
        fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty().trim().take(200)
        check(root.text("status") == "success") { "Periksa token atau kuota AudD." }
        val song = root["result"] as? JsonObject ?: return null
        val title = song.text("title")
        val artist = song.text("artist")
        if (title.isBlank() || artist.isBlank()) return null
        return RecognizedSong(title, artist, song.text("album"))
    }
}
