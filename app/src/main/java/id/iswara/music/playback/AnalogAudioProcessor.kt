@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.*

data class AnalogSound(val warmth:Float=0f,val vinyl:Float=0f,val space:Float=0f) {
    fun bounded()=AnalogSound(warmth.coerceIn(0f,1f),vinyl.coerceIn(0f,1f),space.coerceIn(0f,1f))
    val enabled get()=warmth>0f || vinyl>0f || space>0f
}

/** Local PCM coloration and stereo crossfeed. It does not change the shared playback clock. */
class AnalogAudioProcessor:BaseAudioProcessor() {
    @Volatile var sound=AnalogSound()
    private var channels=0;private var rate=44100
    private var blend=0f;private var phase=0.0;private var random=0x37a31f
    private var low=FloatArray(0);private var dc=FloatArray(0);private var frame=FloatArray(0)
    private var delayL=FloatArray(0);private var delayR=FloatArray(0);private var cursor=0
    private var crackle=0f;private var energy=0f
    private var previous=AnalogSound()
    override fun onConfigure(inputAudioFormat:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat {
        if(inputAudioFormat.channelCount !in 1..8 || inputAudioFormat.encoding!=C.ENCODING_PCM_16BIT)return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }
    override fun onFlush(streamMetadata:AudioProcessor.StreamMetadata) {
        channels=inputAudioFormat.channelCount.coerceIn(1,8);rate=inputAudioFormat.sampleRate.coerceIn(8000,192000)
        low=FloatArray(channels);dc=FloatArray(channels);frame=FloatArray(channels)
        delayL=FloatArray((rate*.002).toInt().coerceAtLeast(32));delayR=FloatArray(delayL.size)
        cursor=0;phase=0.0;crackle=0f;energy=0f;blend=if(sound.enabled)1f else 0f;previous=sound.bounded();random=0x37a31f
    }
    private fun noise():Float {random=random xor (random shl 13);random=random xor (random ushr 17);random=random xor (random shl 5);return (random ushr 8)/16777216f}
    override fun queueInput(inputBuffer:ByteBuffer) {
        if(!inputBuffer.hasRemaining())return
        val output=replaceOutputBuffer(inputBuffer.remaining())
        val requested=sound.bounded()
        if((!requested.enabled && blend==0f) || channels==0 || frame.size!=channels){output.put(inputBuffer);output.flip();return}
        val target=if(requested.enabled)requested.also {previous=it} else previous
        val tone=(1-exp(-2*PI*900/rate)).toFloat()
        val smooth=1f/(rate*.05f)
        val drive=1.0+target.warmth*1.8
        val bias=.08*target.warmth
        val normalization=1.0/tanh(drive)
        val offset=tanh(bias*drive)
        while(inputBuffer.remaining()>=channels*2) {
            blend=(blend+if(requested.enabled)smooth else -smooth).coerceIn(0f,1f)
            var average=0f
            for(channel in 0 until channels){frame[channel]=inputBuffer.short/32768f;average+=abs(frame[channel])}
            energy+=(average/channels-energy)*.005f
            if(noise()<2.0f/rate)crackle=(noise()*2f-1f)*.018f
            crackle*=.77f
            val surface=(crackle+(noise()-.5f)*.0012f)*target.vinyl*(energy*80).coerceIn(0f,1f)
            phase+=2*PI/(rate*18.0);if(phase>2*PI)phase-=2*PI
            val pan=sin(phase).toFloat()*.18f*target.space
            val lag=(rate*(.0002+.00008*(sin(phase)+1))).toInt().coerceIn(1,delayL.lastIndex)
            val index=(cursor-lag+delayL.size)%delayL.size
            val crossL=delayR[index];val crossR=delayL[index]
            delayL[cursor]=frame[0];delayR[cursor]=frame.getOrElse(1){frame[0]};cursor=(cursor+1)%delayL.size
            for(channel in 0 until channels) {
                val dry=frame[channel]
                low[channel]+=tone*(dry-low[channel])
                val saturated=((tanh((dry+bias)*drive)-offset)*normalization).toFloat()
                var wet=dry+(saturated-dry)*target.warmth*.55f+low[channel]*target.warmth*.06f
                dc[channel]+=(wet-dc[channel])*.0003f
                wet-=dc[channel]*target.warmth
                if(channels>=2 && channel<2) {
                    wet=wet*(1-.12f*target.space)+(if(channel==0)crossL else crossR)*.12f*target.space
                    wet*=sqrt((if(channel==0)1-pan else 1+pan).coerceAtLeast(0f))
                }
                wet+=surface
                output.putShort(((dry+(wet-dry)*blend).coerceIn(-1f,1f)*32767).roundToInt().toShort())
            }
        }
        while(inputBuffer.hasRemaining())output.put(inputBuffer.get())
        output.flip()
    }
}
