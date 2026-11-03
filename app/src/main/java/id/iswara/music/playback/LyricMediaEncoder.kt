package id.iswara.music.playback

import android.media.*
import java.io.*
import java.nio.ByteBuffer
import kotlin.math.*

object LyricMediaEncoder {
    private data class Packet(val video:Boolean,val bytes:ByteArray,val time:Long,val flags:Int)
    fun encode(pcm:File,output:File,startMs:Long,durationMs:Long,painter:LyricVideoPainter?,checkpoint:()->Unit,progress:(Int)->Unit) {
        val available=pcm.length()/4
        val start=(startMs*44100/1000).coerceIn(0,available)
        val samples=minOf(durationMs*44100/1000,available-start)
        require(samples>=4410){"Bagian audio terlalu pendek."}
        val duration=samples*1_000_000/44100
        val totalVideo=ceil(duration/1_000_000.0*24).toInt()
        var audio:MediaCodec?=null;var video:MediaCodec?=null;var muxer:MediaMuxer?=null
        var started=false;var complete=false
        try {
            val a=MediaCodec.createEncoderByType("audio/mp4a-latm");audio=a
            val af=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,2).apply {setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);setInteger(MediaFormat.KEY_BIT_RATE,128000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384)}
            a.configure(af,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);a.start()
            var color=0
            if(painter!=null) {
                val choices=MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter {it.isEncoder && it.supportedTypes.any {t->t.equals("video/avc",true)}}
                val chosen=choices.firstOrNull {info->runCatching {info.getCapabilitiesForType("video/avc").colorFormats.any {it in listOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)}}.getOrDefault(false)} ?: error("Encoder video tidak tersedia pada perangkat ini.")
                val formats=chosen.getCapabilitiesForType("video/avc").colorFormats
                color=listOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar).first {it in formats}
                val vf=MediaFormat.createVideoFormat("video/avc",720,1280).apply {setInteger(MediaFormat.KEY_COLOR_FORMAT,color);setInteger(MediaFormat.KEY_BIT_RATE,1_800_000);setInteger(MediaFormat.KEY_FRAME_RATE,24);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2)}
                video=MediaCodec.createByCodecName(chosen.name).apply {configure(vf,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);start()}
            }
            val m=MediaMuxer(output.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);muxer=m
            var audioTrack=-1;var videoTrack=-1;var audioIn=0L;var frame=0;var audioEos=false;var videoEos=painter==null;var audioDone=false;var videoDone=painter==null
            val pending=mutableListOf<Packet>();var pendingBytes=0;var last=android.os.SystemClock.elapsedRealtime()
            fun packet(p:Packet){if(started){val info=MediaCodec.BufferInfo().apply {set(0,p.bytes.size,p.time,p.flags)};m.writeSampleData(if(p.video)videoTrack else audioTrack,ByteBuffer.wrap(p.bytes),info)}else {pendingBytes+=p.bytes.size;check(pendingBytes<10*1024*1024){"Encoder belum siap."};pending+=p}}
            fun maybeStart(){if(!started && audioTrack>=0 && (painter==null || videoTrack>=0)){m.start();started=true;pending.forEach(::packet);pending.clear()}}
            fun drain(codec:MediaCodec,isVideo:Boolean):Boolean {
                var changed=false;val info=MediaCodec.BufferInfo()
                while(true){val slot=codec.dequeueOutputBuffer(info,0)
                    if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(isVideo)videoTrack=m.addTrack(codec.outputFormat)else audioTrack=m.addTrack(codec.outputFormat);maybeStart();changed=true}
                    else if(slot>=0){try {if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0){val b=codec.getOutputBuffer(slot)!!;b.position(info.offset);b.limit(info.offset+info.size);val bytes=ByteArray(info.size);b.get(bytes);packet(Packet(isVideo,bytes,info.presentationTimeUs.coerceAtLeast(0),info.flags))};if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0){if(isVideo)videoDone=true else audioDone=true}}finally {codec.releaseOutputBuffer(slot,false)};changed=true}
                    else break
                };return changed
            }
            val pixels=if(painter!=null)IntArray(720*1280)else IntArray(0)
            val yuv=if(painter!=null)ByteArray(720*1280*3/2)else ByteArray(0)
            RandomAccessFile(pcm,"r").use {input ->
                input.seek(start*4)
                while(!audioDone || !videoDone) {
                    checkpoint();check(android.os.SystemClock.elapsedRealtime()-last<45_000){"Encoder tidak merespons."}
                    var changed=false
                    if(!audioEos){val slot=a.dequeueInputBuffer(0);if(slot>=0){val b=a.getInputBuffer(slot)!!;b.clear();val count=minOf((b.remaining()/4).toLong(),samples-audioIn).toInt();val bytes=ByteArray(count*4);input.readFully(bytes);b.put(bytes)
                        val eos=count==0;a.queueInputBuffer(slot,0,bytes.size,audioIn*1_000_000/44100,if(eos)MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0);audioIn+=count;audioEos=eos;changed=true}}
                    val v=video
                    if(v!=null && !videoEos){val slot=v.dequeueInputBuffer(0);if(slot>=0){
                        if(frame>=totalVideo){v.queueInputBuffer(slot,0,0,duration,MediaCodec.BUFFER_FLAG_END_OF_STREAM);videoEos=true}
                        else {val time=frame*1_000_000L/24;val image=painter!!.draw(startMs+time/1000,time/1000,duration/1000);image.getPixels(pixels,0,720,0,0,720,1280);val size=fill(v,slot,color,pixels,yuv);v.queueInputBuffer(slot,0,size,time,0);frame++;progress((frame*100/totalVideo).coerceAtMost(99))};changed=true}}
                    changed=drain(a,false) || changed
                    if(v!=null)changed=drain(v,true) || changed
                    if(painter==null)progress((audioIn*100/samples).toInt().coerceAtMost(99))
                    if(changed)last=android.os.SystemClock.elapsedRealtime()else Thread.sleep(2)
                }
            }
            check(started){"Media belum berhasil dibuat."};m.stop();complete=true
        } finally {runCatching {audio?.stop()};runCatching {audio?.release()};runCatching {video?.stop()};runCatching {video?.release()};runCatching {muxer?.release()};if(!complete)output.delete()}
    }
    private fun fill(codec:MediaCodec,index:Int,color:Int,pixels:IntArray,data:ByteArray):Int {
        val image=runCatching {codec.getInputImage(index)}.getOrNull()
        val width=720;val height=1280
        if(image!=null) {
            val planes=image.planes
            fun set(plane:Int,x:Int,y:Int,value:Int){val p=planes[plane];p.buffer.put(y*p.rowStride+x*p.pixelStride,value.coerceIn(0,255).toByte())}
            for(y in 0 until height)for(x in 0 until width){val rgb=pixels[y*width+x];val r=rgb shr 16 and 255;val g=rgb shr 8 and 255;val b=rgb and 255;set(0,x,y,((66*r+129*g+25*b+128) shr 8)+16)
                if(x%2==0 && y%2==0){set(1,x/2,y/2,((-38*r-74*g+112*b+128) shr 8)+128);set(2,x/2,y/2,((112*r-94*g-18*b+128) shr 8)+128)}
            }
            return codec.getInputBuffer(index)?.capacity() ?: width*height*3/2
        } else {
            require(color!=MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible){"Format encoder video ini belum didukung."}
            val plane=width*height
            for(y in 0 until height)for(x in 0 until width){val rgb=pixels[y*width+x];val r=rgb shr 16 and 255;val g=rgb shr 8 and 255;val b=rgb and 255;data[y*width+x]=(((66*r+129*g+25*b+128) shr 8)+16).coerceIn(0,255).toByte()
                if(x%2==0 && y%2==0){val u=((-38*r-74*g+112*b+128) shr 8)+128;val v=((112*r-94*g-18*b+128) shr 8)+128;val pos=y/2*(width/2)+x/2
                    if(color==MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar){data[plane+pos]=u.coerceIn(0,255).toByte();data[plane+plane/4+pos]=v.coerceIn(0,255).toByte()}else {data[plane+pos*2]=u.coerceIn(0,255).toByte();data[plane+pos*2+1]=v.coerceIn(0,255).toByte()}
                }
            };codec.getInputBuffer(index)!!.apply {clear();put(data)}
            return data.size
        }
    }
}
