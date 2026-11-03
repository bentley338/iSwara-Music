@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import id.iswara.music.data.Settings
import java.nio.ByteBuffer
import kotlin.math.*

data class RemixPreset(val id:String,val title:String,val description:String,val speed:Float=1f,val pitch:Float=1f,val hall:Boolean=false)
object RemixRules {
    val presets=listOf(RemixPreset("off","Tanpa remix","Gunakan tempo pilihanmu."),RemixPreset("slowed","Slowed + Reverb","0,85x · Hall · Nada lebih rendah",.85f,.9f,true),RemixPreset("nightcore","Nightcore","1,25x · Nada naik 2 semitone",1.25f,2.0.pow(2.0/12).toFloat()),RemixPreset("speedup","Speed Up Natural","1,10x · Nada tetap",1.1f),RemixPreset("practice","Latihan vokal","0,80x · Nada tetap",.8f),RemixPreset("deep","Deep Voice","Tempo tetap · Nada turun 3 semitone",1f,2.0.pow(-3.0/12).toFloat()),RemixPreset("dream","Dreamscape","0,92x · Hall lembut",.92f,.97f,true),RemixPreset("concert","Concert Hall","Tempo dan nada asli · Hall",1f,1f,true))
    fun hall(settings:Settings,room:Boolean)=!room && presets.find {it.id==settings.remix}?.hall==true
    fun parameters(settings:Settings,room:Boolean):PlaybackParameters {
        if(room)return PlaybackParameters.DEFAULT
        val semitones=(settings.pitch.toFloatOrNull() ?: 0f).coerceIn(-12f,12f)
        val shift=2.0.pow(semitones/12.0).toFloat()
        val preset=presets.find {it.id==settings.remix && it.id!="off"}
        val speed=preset?.speed ?: (settings.speed.toFloatOrNull() ?: 1f).coerceIn(.75f,2f)
        val pitch=preset?.pitch ?: 1f
        return PlaybackParameters(speed,(pitch*shift).coerceIn(.4f,2.5f))
    }
}

/** A bounded Schroeder hall in the PCM path; no dependency on vendor AudioEffect support. */
class RemixAudioProcessor:BaseAudioProcessor() {
    @Volatile var hall=false
    private var active=false
    private var channels=0
    private var rate=44100
    private var gain=0f
    private var low=FloatArray(0)
    private var combs=emptyArray<Array<Delay>>()
    private var allpass=emptyArray<Array<Delay>>()
    private class Delay(size:Int,val feedback:Float) {
        val data=FloatArray(size.coerceAtLeast(1));var index=0;var damp=0f
        fun comb(input:Float):Float {val out=data[index];damp=.55f*out+.45f*damp;data[index]=input+damp*feedback;index=(index+1)%data.size;return out}
        fun diffuse(input:Float):Float {val out=data[index];val result=out-input;data[index]=input+out*feedback;index=(index+1)%data.size;return result}
        fun clear(){data.fill(0f);index=0;damp=0f}
    }
    override fun onConfigure(inputAudioFormat:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat {
        require(inputAudioFormat.channelCount in 1..8)
        return if(inputAudioFormat.encoding==C.ENCODING_PCM_16BIT)inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }
    override fun onFlush(streamMetadata:AudioProcessor.StreamMetadata) {
        channels=inputAudioFormat.channelCount.coerceAtLeast(1);rate=inputAudioFormat.sampleRate.coerceAtLeast(8000)
        low=FloatArray(channels)
        combs=Array(channels){ch->arrayOf(.0297,.0371,.0411,.0437).mapIndexed {i,sec->Delay(((sec+ch*.0013)*rate).toInt(),.78f-i*.012f)}.toTypedArray()}
        allpass=Array(channels){ch->arrayOf(Delay((rate*(.005+ch*.0002)).toInt(),.5f),Delay((rate*.0017).toInt(),.5f))}
        gain=if(hall)1f else 0f;active=hall
    }
    override fun queueInput(inputBuffer:ByteBuffer) {
        if(!inputBuffer.hasRemaining())return
        val output=replaceOutputBuffer(inputBuffer.remaining())
        if(!hall && gain==0f){output.put(inputBuffer);output.flip();return}
        if(combs.size!=channels || channels<1){output.put(inputBuffer);output.flip();return}
        if(hall && !active){combs.forEach {it.forEach(Delay::clear)};allpass.forEach {it.forEach(Delay::clear)};low.fill(0f)}
        active=hall
        val alpha=(1-exp(-2*PI*220/rate)).toFloat()
        while(inputBuffer.remaining()>=channels*2) {
            gain=(gain+(if(hall)1f else -1f)/(rate*.04f)).coerceIn(0f,1f)
            for(ch in 0 until channels) {
                val dry=inputBuffer.short/32768f
                low[ch]+=alpha*(dry-low[ch])
                val warm=dry+.3f*low[ch]
                var wet=0f
                for(delay in combs[ch])wet+=delay.comb(warm*.24f)
                for(delay in allpass[ch])wet=delay.diffuse(wet)
                val mixed=tanh((warm*.84f+wet*.25f).toDouble()).toFloat()
                output.putShort(((dry+(mixed-dry)*gain)*32767).roundToInt().coerceIn(-32768,32767).toShort())
            }
        }
        while(inputBuffer.hasRemaining())output.put(inputBuffer.get())
        output.flip()
    }
}
