package id.iswara.music

import id.iswara.music.playback.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class StemDspTest {
    @Test fun fftRoundTripAndUnitMaskReconstructMiddleOfStereoSignal() {
        val real=FloatArray(4096){sin(it*.037).toFloat()};val original=real.copyOf();val imag=FloatArray(4096)
        StemDsp.fft(real,imag);StemDsp.fft(real,imag,true)
        assertTrue(real.indices.maxOf {abs(real[it]-original[it])}<.0002f)
        val audio=Array(2){ch->FloatArray(StemDsp.SAMPLES){i->(.3*sin(2*PI*(300+ch*200)*i/StemDsp.RATE)).toFloat()}}
        val shape=2*StemDsp.FRAMES*StemDsp.BINS
        val reconstructed=StemDsp.reconstructVocals(audio,FloatArray(shape){1f},FloatArray(shape))
        for(ch in 0..1)assertTrue((StemDsp.CONTEXT until StemDsp.CONTEXT+StemDsp.CORE).maxOf {abs(audio[ch][it]-reconstructed[ch][it])}<.002f)
    }
    @Test fun resamplingPreservesDurationAndStereoChannelsInBoundedChunks() {
        for(rate in listOf(22050,44100,48000)) {
            val stream=java.io.ByteArrayOutputStream();val converter=StemDecoder.Resampler(rate,stream)
            repeat(rate){converter.add(.25f,-.25f)};converter.finish()
            assertEquals(44100L,converter.frames);assertEquals(44100*4,stream.size())
            val bytes=java.nio.ByteBuffer.wrap(stream.toByteArray()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            repeat(44100){assertEquals(8192,bytes.short.toInt());assertEquals(-8192,bytes.short.toInt())}
        }
    }
}
