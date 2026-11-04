package id.iswara.music.source

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import id.iswara.music.MainActivity
import id.iswara.music.R
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PlaybackRecognitionState(
    val active: Boolean = false, val seconds: Int? = null, val matching: Boolean = false,
    val finished: Boolean = false, val song: RecognizedSong? = null, val issue: RecognitionIssue? = null,
)

internal class PlaybackRecognitionService : Service() {
    companion object {
        private const val CHANNEL = "song_recognition"
        private const val NOTICE = 815
        private const val START = "id.iswara.recognition.START"
        private const val STOP = "id.iswara.recognition.STOP"
        private const val CONSENT = "projection_consent"
        private val mutableState = MutableStateFlow(PlaybackRecognitionState())
        val state = mutableState.asStateFlow()

        fun reset() { if (!state.value.active) mutableState.value = PlaybackRecognitionState() }

        fun start(context: Context, consent: Intent): Boolean {
            if (Build.VERSION.SDK_INT < 29 || state.value.active) return false
            mutableState.value = PlaybackRecognitionState(active = true)
            return try {
                ContextCompat.startForegroundService(context,
                    Intent(context, PlaybackRecognitionService::class.java).setAction(START).putExtra(CONSENT, consent))
                true
            } catch (_: RuntimeException) {
                mutableState.value = PlaybackRecognitionState(issue = RecognitionIssue.PERMISSION)
                false
            }
        }

        fun stop(context: Context) { context.stopService(Intent(context, PlaybackRecognitionService::class.java)) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var projection: MediaProjection? = null
    private val stopped = object : MediaProjection.Callback() {
        override fun onStop() { finish(RecognitionIssue.STOPPED) }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish(RecognitionIssue.STOPPED); return START_NOT_STICKY }
        if (job != null) return START_NOT_STICKY
        if (Build.VERSION.SDK_INT < 29 || intent?.action != START) { finish(RecognitionIssue.PERMISSION); return START_NOT_STICKY }
        val consent = IntentCompat.getParcelableExtra(intent, CONSENT, Intent::class.java)
        if (consent == null || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            finish(RecognitionIssue.PERMISSION)
            return START_NOT_STICKY
        }
        try {
            startNotice()
            val manager = checkNotNull(getSystemService(MediaProjectionManager::class.java))
            projection = manager.getMediaProjection(Activity.RESULT_OK, consent)
                ?.also { it.registerCallback(stopped, Handler(Looper.getMainLooper())) }
                ?: throw RecognitionFailure(RecognitionIssue.PERMISSION)
        } catch (_: Exception) {
            finish(RecognitionIssue.PERMISSION)
            return START_NOT_STICKY
        }
        val session = checkNotNull(projection)
        job = scope.launch {
            try {
                val clip = capture(session) { seconds -> mutableState.value = PlaybackRecognitionState(active = true, seconds = seconds) }
                stopProjection()
                mutableState.value = PlaybackRecognitionState(active = true, matching = true)
                notifyIfAllowed(NOTICE, notice(getString(R.string.recognition_matching)))
                val song = SongRecognition.identify(clip, microphone = false)
                mutableState.value = PlaybackRecognitionState(finished = true, song = song)
                notifyResult(song?.let { "${it.title} · ${it.artist}" } ?: getString(R.string.recognition_no_match))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: RecognitionFailure) {
                mutableState.value = PlaybackRecognitionState(issue = failure.issue)
                if (failure.issue == RecognitionIssue.NO_AUDIO) notifyResult(getString(R.string.recognition_no_audio))
            } catch (_: Exception) {
                mutableState.value = PlaybackRecognitionState(issue = RecognitionIssue.NETWORK)
            } finally {
                stopProjection()
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    @RequiresApi(29)
    private fun startNotice() {
        val notifications = checkNotNull(getSystemService(NotificationManager::class.java))
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.recognition_name), NotificationManager.IMPORTANCE_LOW))
        notifications.cancel(NOTICE + 1)
        ServiceCompat.startForeground(this, NOTICE, notice(getString(R.string.recognition_notice)), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun notice(text: String, ongoing: Boolean = true): android.app.Notification {
        val open = PendingIntent.getActivity(this, NOTICE, Intent(this, MainActivity::class.java).setAction("id.iswara.recognition.RESULT"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, NOTICE, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_logo_mono)
            .setContentTitle(getString(R.string.recognition_name)).setContentText(text).setContentIntent(open)
            .setOngoing(ongoing).setAutoCancel(!ongoing).apply { if (ongoing) addAction(0, getString(R.string.recognition_stop), cancel) }.build()
    }

    private fun notifyResult(text: String) {
        notifyIfAllowed(NOTICE + 1, notice(text, ongoing = false))
    }

    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            getSystemService(NotificationManager::class.java)?.notify(id, notification)
        }
    }

    private fun stopProjection() {
        projection?.let { session ->
            projection = null
            runCatching { session.unregisterCallback(stopped) }
            runCatching { session.stop() }
        }
    }

    private fun finish(issue: RecognitionIssue) {
        mutableState.value = PlaybackRecognitionState(issue = issue)
        job?.cancel()
        stopProjection()
        stopSelf()
    }

    override fun onDestroy() {
        if (mutableState.value.active) mutableState.value = PlaybackRecognitionState(issue = RecognitionIssue.STOPPED)
        scope.cancel()
        stopProjection()
        super.onDestroy()
    }

    @RequiresApi(29)
    @SuppressLint("MissingPermission")
    private suspend fun capture(session: MediaProjection, progress: (Int) -> Unit): ByteArray = withContext(Dispatchers.IO) {
        val rate = 44_100
        val format = AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
        val source = AudioPlaybackCaptureConfiguration.Builder(session).addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME).excludeUid(android.os.Process.myUid()).build()
        val buffer = ShortArray(rate / 10)
        val output = ByteArrayOutputStream(rate * 20)
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(minimum * 2, buffer.size * 4))
            .setAudioPlaybackCaptureConfig(source).build()
        val start = SystemClock.elapsedRealtime()
        var heard = 0
        var reported = 0
        val raw = ByteArray(buffer.size * 2)
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) throw RecognitionFailure(RecognitionIssue.PERMISSION)
            record.startRecording()
            while (SystemClock.elapsedRealtime() - start < 40_000 && output.size() < rate * 20) {
                ensureActive()
                val size = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                if (size < 0) throw RecognitionFailure(RecognitionIssue.PERMISSION)
                if (size == 0) { delay(20); continue }
                if (hasMusicSignal(buffer, size)) heard += size
                if (heard == 0) {
                    if (SystemClock.elapsedRealtime() - start > 28_000) break
                    continue
                }
                val frames = minOf(size, (rate * 20 - output.size()) / 2)
                for (i in 0 until frames) { raw[i * 2] = buffer[i].toByte(); raw[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte() }
                output.write(raw, 0, frames * 2)
                val seconds = output.size() / (rate * 2)
                if (seconds != reported) { reported = seconds; withContext(Dispatchers.Main.immediate) { progress(seconds) } }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        if (heard < rate * 2 || output.size() < rate * 8) throw RecognitionFailure(RecognitionIssue.NO_AUDIO)
        SongRecognition.wav(output.toByteArray(), rate, 1)
    }
}

internal fun hasMusicSignal(samples: ShortArray, count: Int): Boolean {
    if (count <= 0) return false
    var energy = 0L
    for (i in 0 until count) { val value = samples[i].toInt(); energy += value * value }
    return energy > count.toLong() * 64 * 64
}
