package id.iswara.music.playback

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object StemDsp {
    const val RATE=44100
    const val FFT=4096
    const val HOP=1024
    const val FRAMES=128
    const val BINS=1024
    const val SAMPLES=(FRAMES-1)*HOP+FFT
    const val CORE=96*HOP
    const val CONTEXT=16*HOP
    const val OVERLAP=4096
    private val window=FloatArray(FFT){(.5-.5*cos(2*PI*it/FFT)).toFloat()}
    fun fft(real:FloatArray,imag:FloatArray,inverse:Boolean=false) {
        val size=real.size;require(size==imag.size && size>0 && size and (size-1)==0)
        var j=0
        for(i in 1 until size){var bit=size shr 1;while(j and bit!=0){j=j xor bit;bit=bit shr 1};j=j xor bit;if(i<j){val r=real[i];real[i]=real[j];real[j]=r;val im=imag[i];imag[i]=imag[j];imag[j]=im}}
        var len=2
        while(len<=size){val angle=(if(inverse)2 else -2)*PI/len;val wr=cos(angle).toFloat();val wi=sin(angle).toFloat();var base=0
            while(base<size){var ar=1f;var ai=0f
                for(k in 0 until len/2){val a=base+k;val b=a+len/2;val br=real[b]*ar-imag[b]*ai;val bi=real[b]*ai+imag[b]*ar
                    real[b]=real[a]-br;imag[b]=imag[a]-bi;real[a]+=br;imag[a]+=bi
                    val next=ar*wr-ai*wi;ai=ar*wi+ai*wr;ar=next
                };base+=len
            };len=len shl 1
        }
        if(inverse)for(i in real.indices){real[i]/=size;imag[i]/=size}
    }
    fun magnitudes(audio:Array<FloatArray>,checkpoint:()->Unit={}):FloatArray {
        require(audio.size==2 && audio.all {it.size==SAMPLES})
        val result=FloatArray(2*FRAMES*BINS);val real=FloatArray(FFT);val imag=FloatArray(FFT)
        for(ch in 0..1)for(frame in 0 until FRAMES){checkpoint();val start=frame*HOP
            for(i in 0 until FFT){real[i]=audio[ch][start+i]*window[i];imag[i]=0f};fft(real,imag)
            for(bin in 0 until BINS)result[(ch*FRAMES+frame)*BINS+bin]=hypot(real[bin],imag[bin])
        };return result
    }
    fun reconstructVocals(audio:Array<FloatArray>,vocal:FloatArray,accompaniment:FloatArray,checkpoint:()->Unit={}):Array<FloatArray> {
        require(vocal.size==2*FRAMES*BINS && accompaniment.size==vocal.size)
        val result=Array(2){FloatArray(SAMPLES)};val weight=FloatArray(SAMPLES)
        val real=FloatArray(FFT);val imag=FloatArray(FFT)
        for(frame in 0 until FRAMES)for(i in 0 until FFT)weight[frame*HOP+i]+=window[i]*window[i]
        for(ch in 0..1) {
            for(frame in 0 until FRAMES){checkpoint();val start=frame*HOP
                for(i in 0 until FFT){real[i]=audio[ch][start+i]*window[i];imag[i]=0f};fft(real,imag)
                for(bin in 0..FFT/2){val index=(ch*FRAMES+frame)*BINS+bin
                    val mask=if(bin<BINS){val v=vocal[index].toDouble();val a=accompaniment[index].toDouble();((v*v+5e-11)/(v*v+a*a+1e-10)).toFloat().takeIf {it.isFinite()}?.coerceIn(0f,1f) ?: 0f}else 0f
                    real[bin]*=mask;imag[bin]*=mask
                    if(bin>0 && bin<FFT/2){real[FFT-bin]*=mask;imag[FFT-bin]*=mask}
                };fft(real,imag,true)
                for(i in 0 until FFT)result[ch][start+i]+=real[i]*window[i]
            }
            for(i in result[ch].indices)result[ch][i]=if(weight[i]>1e-6)result[ch][i]/weight[i] else 0f
        };return result
    }
    class WavWriter(file:File,val rate:Int=RATE):Closeable {
        private val output=RandomAccessFile(file,"rw");private var frames=0L
        init {output.setLength(0);output.write(ByteArray(44))}
        fun write(left:FloatArray,right:FloatArray,count:Int=left.size){require(count<=left.size && count<=right.size);val bytes=ByteBuffer.allocate(count*4).order(ByteOrder.LITTLE_ENDIAN)
            for(i in 0 until count){bytes.putShort((left[i].coerceIn(-1f,1f)*32767).roundToInt().toShort());bytes.putShort((right[i].coerceIn(-1f,1f)*32767).roundToInt().toShort())};output.write(bytes.array());frames+=count
        }
        override fun close(){val size=frames*4;val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).put("RIFF".toByteArray()).putInt((size+36).toInt()).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate*4).putShort(4).putShort(16).put("data".toByteArray()).putInt(size.toInt());output.seek(0);output.write(h.array());output.close()}
    }
    fun block(input:RandomAccessFile,total:Long,start:Long):Array<FloatArray> {
        val result=Array(2){FloatArray(SAMPLES)}
        val from=start.coerceAtLeast(0);val end=(start+SAMPLES).coerceAtMost(total)
        if(end<=from)return result
        val count=(end-from).toInt();val data=ByteArray(count*4);input.seek(from*4);input.readFully(data)
        val buffer=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);val offset=(from-start).toInt()
        for(i in 0 until count){result[0][offset+i]=buffer.short/32768f;result[1][offset+i]=buffer.short/32768f};return result
    }
}
