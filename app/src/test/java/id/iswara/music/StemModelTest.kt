package id.iswara.music

import id.iswara.music.playback.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class StemModelTest {
    @Test fun officialMusicExampleProducesNonzeroVocalAndInstrumentEstimates() {
        org.junit.Assume.assumeTrue(System.getenv("ISWARA_LIVE_TEST")=="1")
        val fixture=File("../.temp/stem-research/audio_example.wav")
        if(!fixture.exists()){fixture.parentFile.mkdirs();java.net.URL("https://github.com/k2-fsa/sherpa-onnx/releases/download/source-separation-models/audio_example.wav").openStream().use {input->fixture.outputStream().use {input.copyTo(it)}}}
        val audio=Array(2){FloatArray(StemDsp.SAMPLES)}
        java.io.RandomAccessFile(fixture,"r").use {input ->
            input.seek(12);var data=0L
            while(input.filePointer<input.length()) {val name=ByteArray(4);input.readFully(name);val size=Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                if(String(name)=="data"){data=input.filePointer;break};input.seek(input.filePointer+size+(size%2))
            };check(data>0);input.seek(data+4*44100*4)
            val bytes=ByteArray((StemDsp.SAMPLES-StemDsp.CONTEXT)*4);input.readFully(bytes);val buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for(i in StemDsp.CONTEXT until StemDsp.SAMPLES){audio[0][i]=buffer.short/32768f;audio[1][i]=buffer.short/32768f}
        }
        val folder=File("src/main/assets/stem-models")
        StemModel(File(folder,"vocals.onnx"),File(folder,"accompaniment.onnx")).use {model ->
            val result=model.separate(audio)
            val region=StemDsp.CONTEXT until StemDsp.CONTEXT+StemDsp.CORE
            for(ch in 0..1){assertTrue(region.sumOf {result[ch][it].toDouble().pow(2)}>1);assertTrue(region.sumOf {(audio[ch][it]-result[ch][it]).toDouble().pow(2)}>1)}
        }
    }
    @Test fun bundledAiModelsProduceFiniteDistinctStereoStemsAndKeepDuration() {
        val folder=File("src/main/assets/stem-models")
        val audio=Array(2){ch->FloatArray(StemDsp.SAMPLES){i->(.25*sin(2*PI*(240+ch*120)*i/44100)+.12*sin(2*PI*1100*i/44100)).toFloat()}}
        StemModel(File(folder,"vocals.onnx"),File(folder,"accompaniment.onnx")).use {model ->
            val vocals=model.separate(audio)
            for(ch in 0..1){assertEquals(audio[ch].size,vocals[ch].size);assertTrue(vocals[ch].all {it.isFinite()})
                val range=StemDsp.CONTEXT until StemDsp.CONTEXT+StemDsp.CORE
                assertTrue(range.any {abs(audio[ch][it]-vocals[ch][it])>.0001f})
                assertTrue(range.all {abs(vocals[ch][it]+(audio[ch][it]-vocals[ch][it])-audio[ch][it])<.00001f})
            }
            val silent=model.separate(Array(2){FloatArray(StemDsp.SAMPLES)})
            assertTrue(silent.all {samples->samples.all {abs(it)<.00001f}})
        }
    }
}
