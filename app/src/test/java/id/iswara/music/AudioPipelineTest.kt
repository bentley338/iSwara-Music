@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.audio.*
import id.iswara.music.playback.VolumeBalancer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34, 36], application = Application::class)
class AudioPipelineTest {
    @Test fun pcmPlaybackSurvivesSeekSpeedSilenceAndTrackTransition() {
        val context = RuntimeEnvironment.getApplication()
        val processor = VolumeBalancer()
        val remix=id.iswara.music.playback.RemixAudioProcessor()
        val analog=id.iswara.music.playback.AnalogAudioProcessor()
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setAudioProcessors(arrayOf(remix,analog,processor)).build()
        }
        val file = File(context.cacheDir, "pipeline.wav")
        val frames = 48000 * 2
        val pcmSize = frames * 4
        file.outputStream().use { stream ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + pcmSize).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(2).putInt(48000).putInt(192000).putShort(4).putShort(16)
                .put("data".toByteArray()).putInt(pcmSize)
            stream.write(header.array())
            val audio = ByteBuffer.allocate(pcmSize).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames) { audio.putShort((if (it % 2 == 0) 3000 else -3000).toShort()).putShort(1000) }
            stream.write(audio.array())
        }
        val errors = mutableListOf<PlaybackException>()
        val player = ExoPlayer.Builder(context, renderers).build()
        try {
            player.addListener(object : Player.Listener { override fun onPlayerError(error: PlaybackException) { errors += error } })
            player.setMediaItems(listOf(MediaItem.fromUri(file.toURI().toString()), MediaItem.fromUri(file.toURI().toString())))
            player.prepare(); player.play()
            pump(player, errors)
            remix.hall=true
            analog.sound=id.iswara.music.playback.AnalogSound(.5f,.25f,.5f)
            player.playbackParameters=PlaybackParameters(.85f,.9f)
            pump(player,errors)
            processor.enabled = true
            player.playbackParameters=PlaybackParameters(1.25f,1.12246f); player.skipSilenceEnabled = true; player.seekTo(0, 400)
            pump(player, errors)
            remix.hall=false
            player.seekToNextMediaItem(); player.setPlaybackSpeed(1f); player.skipSilenceEnabled = false
            pump(player, errors)
            assertTrue(errors.joinToString { it.stackTraceToString() }, errors.isEmpty())
            assertEquals(1, player.currentMediaItemIndex)
        } finally { player.release(); file.delete() }
    }
    private fun pump(player: ExoPlayer, errors: List<PlaybackException>) {
        var readyTicks = 0
        val deadline = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            Thread.sleep(10)
            if (errors.isNotEmpty()) throw AssertionError(errors.first().stackTraceToString(), errors.first())
            if (player.playbackState == Player.STATE_READY && ++readyTicks >= 20) return
        }
        assertEquals("PCM reaches ready", Player.STATE_READY, player.playbackState)
    }
}
