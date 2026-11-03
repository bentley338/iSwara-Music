package id.iswara.music.playback

import android.content.Context
import android.media.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object StemDecoder {
    fun decode(context:Context,input:File,output:File,checkpoint:()->Unit):Long {
        val extractor=MediaExtractor();var decoder:MediaCodec?=null
        try {
            extractor.setDataSource(input.absolutePath)
            val index=(0 until extractor.trackCount).firstOrNull {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true} ?: error("Berkas tidak mempunyai trek audio.")
            extractor.selectTrack(index);val format=extractor.getTrackFormat(index)
            if(format.containsKey(MediaFormat.KEY_DURATION))require(format.getLong(MediaFormat.KEY_DURATION)<=480_000_000L){"Pemisahan maksimal 8 menit per lagu."}
            format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
            val codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!);decoder=codec
            codec.configure(format,null,null,0);codec.start()
            var done=false;var ended=false;var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);var encoding=AudioFormat.ENCODING_PCM_16BIT
            var writer:Resampler?=null;var last=android.os.SystemClock.elapsedRealtime()
            val info=MediaCodec.BufferInfo()
            output.outputStream().buffered().use {sink ->
                while(!done) {
                    checkpoint();check(android.os.SystemClock.elapsedRealtime()-last<30_000){"Decoder audio tidak merespons."}
                    if(!ended) {
                        val slot=codec.dequeueInputBuffer(10_000)
                        if(slot>=0){val buffer=codec.getInputBuffer(slot)!!;val size=extractor.readSampleData(buffer,0)
                            if(size<0){codec.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);ended=true}
                            else {codec.queueInputBuffer(slot,0,size,extractor.sampleTime,0);extractor.advance()};last=android.os.SystemClock.elapsedRealtime()
                        }
                    }
                    val slot=codec.dequeueOutputBuffer(info,10_000)
                    if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val next=codec.outputFormat;val nextRate=next.getInteger(MediaFormat.KEY_SAMPLE_RATE);val nextChannels=next.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        check(writer==null || nextRate==rate && nextChannels==channels){"Format audio berubah saat diproses."}
                        rate=nextRate;channels=nextChannels;encoding=if(next.containsKey(MediaFormat.KEY_PCM_ENCODING))next.getInteger(MediaFormat.KEY_PCM_ENCODING)else AudioFormat.ENCODING_PCM_16BIT
                    } else if(slot>=0) {
                        try {if(info.size>0){
                            require(channels in 1..8 && rate in 8000..192000){"Format audio belum didukung."}
                            val pcm=codec.getOutputBuffer(slot)!!.duplicate().order(ByteOrder.LITTLE_ENDIAN);pcm.position(info.offset);pcm.limit(info.offset+info.size)
                            val samples=writer ?: Resampler(rate,sink).also {writer=it}
                            val width=if(encoding==AudioFormat.ENCODING_PCM_FLOAT)4 else 2
                            require(encoding==AudioFormat.ENCODING_PCM_FLOAT || encoding==AudioFormat.ENCODING_PCM_16BIT){"Decoder belum mendukung PCM ini."}
                            val frame=FloatArray(channels)
                            while(pcm.remaining()>=channels*width){for(ch in frame.indices)frame[ch]=if(width==4)pcm.float.takeIf {it.isFinite()} ?: 0f else pcm.short/32768f
                                var left=frame[0];var right=frame.getOrElse(1){left}
                                if(channels>2){left+=frame[2]*.707f;right+=frame[2]*.707f;var scale=1.707f
                                    if(channels>3){left+=frame[3]*.3f;right+=frame[3]*.3f;scale+=.3f}
                                    if(channels>5){left+=frame[4]*.5f;right+=frame[5]*.5f;scale+=.5f}
                                    if(channels>7){left+=frame[6]*.5f;right+=frame[7]*.5f;scale+=.5f}
                                    left/=scale;right/=scale
                                };samples.add(left,right)
                            };last=android.os.SystemClock.elapsedRealtime()
                        }} finally {codec.releaseOutputBuffer(slot,false)}
                        if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)done=true
                    }
                }
                writer?.finish();return writer?.frames ?: 0
            }
        } finally {runCatching {decoder?.stop()};runCatching {decoder?.release()};extractor.release()}
    }
    class Resampler(private val inputRate:Int,private val output:java.io.OutputStream) {
        private var inputFrames=0L;var frames=0L;private set
        private var next=0.0;private var left=0f;private var right=0f
        private val bytes=ByteBuffer.allocate(32768).order(ByteOrder.LITTLE_ENDIAN)
        fun add(l:Float,r:Float) {
            if(inputFrames==0L){emit(l,r);next=inputRate.toDouble()/StemDsp.RATE}
            else while(next<=inputFrames){val t=(next-(inputFrames-1)).toFloat().coerceIn(0f,1f);emit(left+(l-left)*t,right+(r-right)*t);next+=inputRate.toDouble()/StemDsp.RATE}
            left=l;right=r;inputFrames++
        }
        private fun emit(l:Float,r:Float) {
            require(frames<StemDsp.RATE*480L){"Pemisahan maksimal 8 menit per lagu."}
            if(bytes.remaining()<4)flush()
            bytes.putShort((l.coerceIn(-1f,1f)*32767).roundToInt().toShort());bytes.putShort((r.coerceIn(-1f,1f)*32767).roundToInt().toShort());frames++
        }
        private fun flush(){output.write(bytes.array(),0,bytes.position());bytes.clear()}
        fun finish(){val count=(inputFrames*StemDsp.RATE/inputRate).coerceAtMost(StemDsp.RATE*480L);while(frames<count)emit(left,right);flush()}
    }
}
