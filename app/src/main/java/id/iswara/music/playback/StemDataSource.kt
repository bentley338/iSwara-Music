@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package id.iswara.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.*
import id.iswara.music.data.StemCipher
import id.iswara.music.data.StemStore

class StemDataSource(private val store:StemStore):BaseDataSource(false) {
    private var ownerTag=""
    private var reader:StemCipher.Reader?=null;private var uri:Uri?=null;private var position=0L;private var remaining=0L;private var opened=false
    override fun open(dataSpec:DataSpec):Long {
        transferInitializing(dataSpec)
        try {ownerTag=dataSpec.uri.getQueryParameter("owner").orEmpty();val source=store.reader(dataSpec.uri.getQueryParameter("id") ?: error("Stem tidak valid"),dataSpec.uri.host ?: error("Stem tidak valid"),ownerTag);reader=source
            require(dataSpec.position<=source.length){"Posisi stem di luar berkas."}
            uri=dataSpec.uri;position=dataSpec.position;remaining=if(dataSpec.length==C.LENGTH_UNSET.toLong())source.length-position else minOf(dataSpec.length,source.length-position)
            opened=true;transferStarted(dataSpec);return remaining
        } catch(e:Exception){reader?.close();reader=null;throw java.io.IOException("Hasil AI tidak dapat dibaca. Hapus hasil dan proses ulang.",e)}
    }
    override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
        check(ownerTag==store.ownerTag()){"Akun berubah."}
        if(length==0)return 0
        if(remaining==0L)return C.RESULT_END_OF_INPUT
        val count=reader!!.read(position,buffer,offset,minOf(length.toLong(),remaining).toInt());if(count<0)throw java.io.EOFException("Stem tidak lengkap")
        position+=count;remaining-=count;bytesTransferred(count);return count
    }
    override fun getUri()=uri
    override fun close(){try {reader?.close()}finally {reader=null;uri=null;if(opened){opened=false;transferEnded()}}}
}
