@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import id.iswara.music.data.Settings
import id.iswara.music.playback.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class RemixTest {
    @Test fun presetsAndIndependentPitchAreBoundedAndRoomUsesOriginalTiming() {
        val slowed=RemixRules.parameters(Settings(remix="slowed",pitch="-2"),false)
        assertEquals(.85f,slowed.speed,.001f);assertEquals(.9f*2.0.pow(-2.0/12).toFloat(),slowed.pitch,.001f)
        val night=RemixRules.parameters(Settings(remix="nightcore"),false)
        assertEquals(1.25f,night.speed,.001f);assertEquals(2.0.pow(2.0/12).toFloat(),night.pitch,.001f)
        val custom=RemixRules.parameters(Settings(speed="1.5",pitch="-12"),false)
        assertEquals(1.5f,custom.speed,0f);assertEquals(.5f,custom.pitch,0f)
        val room=RemixRules.parameters(Settings(remix="slowed",pitch="-12"),true)
        assertEquals(1f,room.speed,0f);assertEquals(1f,room.pitch,0f)
    }
    @Test fun disabledIsBitExactAndEmptyDrainIsSafe() {
        val processor=RemixAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100,2,C.ENCODING_PCM_16BIT));processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        repeat(5){processor.queueInput(AudioProcessor.EMPTY_BUFFER);assertFalse(processor.output.hasRemaining())}
        val bytes=ByteArray(2048){(it%251).toByte()};processor.queueInput(ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {put(bytes);flip()})
        val result=ByteArray(bytes.size);processor.output.get(result);assertArrayEquals(bytes,result)
    }
    @Test fun hallHasTailNoClippingAndFlushRemovesPreviousSong() {
        val processor=RemixAudioProcessor().apply {hall=true;configure(AudioProcessor.AudioFormat(44100,2,C.ENCODING_PCM_16BIT));flush(AudioProcessor.StreamMetadata.DEFAULT)}
        val input=ByteBuffer.allocateDirect(44100*3*4).order(ByteOrder.nativeOrder())
        repeat(44100*3){i->val signal=if(i<1000)(sin(2*PI*180*i/44100)*26000).toInt() else 0;input.putShort(signal.toShort());input.putShort(signal.toShort())};input.flip()
        processor.queueInput(input);val output=processor.output.order(ByteOrder.nativeOrder());val values=ShortArray(output.remaining()/2){output.short}
        assertTrue(values.drop(44100).any {abs(it.toInt())>2})
        assertTrue(values.all {abs(it.toInt())<32768})
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(ByteBuffer.allocateDirect(4000).order(ByteOrder.nativeOrder()))
        val silent=processor.output;while(silent.hasRemaining())assertEquals(0,silent.get().toInt())
    }
}
