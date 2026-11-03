@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.*

/** Smooth RMS leveling with peak limiting; no fabricated ReplayGain metadata. */
class VolumeBalancer : BaseAudioProcessor() {
    @Volatile var enabled = false
    private var gain = 1.0
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 drains processors with its shared EMPTY_BUFFER, also our initial output buffer.
        // Allocating/copying here would call EMPTY_BUFFER.put(EMPTY_BUFFER) and stop playback.
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (!enabled) { output.put(inputBuffer); output.flip(); gain = 1.0; return }
        val samples = inputBuffer.remaining() / 2
        if (samples == 0) return
        var square = 0.0; var peak = 1.0
        val start = inputBuffer.position()
        repeat(samples) { val value = inputBuffer.short.toDouble(); square += value * value; peak = max(peak, abs(value)) }
        inputBuffer.position(start)
        val rms = sqrt(square / samples)
        val target = if (rms < 32) 1.0 else min((4125.0 / rms).coerceIn(.25, 2.0), 32000.0 / peak)
        repeat(samples) {
            gain += (target - gain) * if (target < gain) .005 else .00002
            output.putShort((inputBuffer.short * min(gain, 32000.0 / peak)).roundToInt().coerceIn(-32768,32767).toShort())
        }
        output.flip()
    }
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) { gain = 1.0 }
}
