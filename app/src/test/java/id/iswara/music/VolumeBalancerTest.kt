@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import id.iswara.music.playback.VolumeBalancer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VolumeBalancerTest {
    @Test fun drainingBeforeFirstAudioAndAfterFlushIsSafe() {
        for (enabled in listOf(false, true)) {
            val processor = VolumeBalancer().apply { this.enabled = enabled }
            processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
            repeat(3) {
                processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
                repeat(5) { processor.queueInput(AudioProcessor.EMPTY_BUFFER); assertFalse(processor.output.hasRemaining()) }
                processor.queueEndOfStream()
                assertTrue(processor.isEnded)
            }
        }
    }
    private fun run(amplitude: Int, enabled: Boolean): IntArray {
        val processor = VolumeBalancer().apply { this.enabled = enabled; configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); flush() }
        val input = ByteBuffer.allocateDirect(96000 * 2).order(ByteOrder.nativeOrder())
        repeat(96000) { input.putShort((if (it % 2 == 0) amplitude else -amplitude).toShort()) }; input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        return IntArray(output.remaining() / 2) { output.short.toInt() }
    }
    @Test fun disabledLeavesPcmUnchanged() { val samples = run(12000, false); assertEquals(12000, samples[0]); assertEquals(-12000, samples[1]) }
    @Test fun loudSignalIsReducedWithoutClipping() { val samples = run(30000, true); assertTrue(samples.maxOf { kotlin.math.abs(it) } <= 32000); assertTrue(kotlin.math.abs(samples.last()) < 10000) }
    @Test fun silenceRemainsSilenceAndQuietSignalIsBounded() { assertTrue(run(0, true).all { it == 0 }); assertTrue(run(500, true).maxOf { kotlin.math.abs(it) } <= 1000) }
}
