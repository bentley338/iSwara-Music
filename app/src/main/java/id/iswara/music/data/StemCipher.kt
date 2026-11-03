package id.iswara.music.data

import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Independently authenticated blocks permit seeking without loading a whole WAV into memory. */
object StemCipher {
    const val BLOCK=65536
    private const val HEADER=28
    private val magic="ISWSTEM1".toByteArray(Charsets.US_ASCII)
    fun encrypt(input:File,output:File,key:SecretKey,identity:String,checkpoint:()->Unit={}) {
        val nonce=ByteArray(12).also {SecureRandom().nextBytes(it)}
        val header=ByteBuffer.allocate(HEADER).put(magic).putLong(input.length()).put(nonce).array()
        input.inputStream().buffered().use {source->output.outputStream().buffered().use {sink->
            sink.write(header);val buffer=ByteArray(BLOCK);var index=0
            while(true) {
                checkpoint();var size=0
                while(size<BLOCK){val n=source.read(buffer,size,BLOCK-size);if(n<0)break;size+=n}
                if(size==0)break
                sink.write(cipher(Cipher.ENCRYPT_MODE,key,nonce,index,header,identity).doFinal(buffer,0,size));index++
            }
        }}
    }
    private fun cipher(mode:Int,key:SecretKey,nonce:ByteArray,index:Int,header:ByteArray,identity:String):Cipher {
        val iv=nonce.copyOf();for(i in 0..3)iv[8+i]=(iv[8+i].toInt() xor (index ushr (24-i*8))).toByte()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode,key,GCMParameterSpec(128,iv));updateAAD(header);updateAAD(identity.toByteArray(Charsets.UTF_8));updateAAD(ByteBuffer.allocate(4).putInt(index).array())
        }
    }
    class Reader(file:File,private val key:SecretKey,private val identity:String):Closeable {
        private val input=RandomAccessFile(file,"r")
        private val header=ByteArray(HEADER)
        private val nonce:ByteArray
        val length:Long
        private var block=-1
        private var plain=ByteArray(0)
        init {
            try {
                input.readFully(header);require(header.copyOfRange(0,8).contentEquals(magic)){"Berkas stem tidak valid."}
                length=ByteBuffer.wrap(header,8,8).long;require(length in 44..(200L*1024*1024)){"Ukuran stem tidak valid."}
                val count=(length+BLOCK-1)/BLOCK;require(input.length()==HEADER+length+count*16){"Berkas stem tidak lengkap."}
                nonce=header.copyOfRange(16,28)
            } catch(e:Exception){input.close();throw e}
        }
        fun read(position:Long,target:ByteArray,offset:Int,size:Int):Int {
            require(position>=0 && offset>=0 && size>=0 && offset+size<=target.size)
            if(size==0)return 0
            if(position>=length)return -1
            val index=(position/BLOCK).toInt()
            if(index!=block) {
                val count=minOf(BLOCK.toLong(),length-index.toLong()*BLOCK).toInt()
                val bytes=ByteArray(count+16);input.seek(HEADER+index.toLong()*(BLOCK+16));input.readFully(bytes)
                plain=cipher(Cipher.DECRYPT_MODE,key,nonce,index,header,identity).doFinal(bytes);block=index
            }
            val start=(position%BLOCK).toInt();val count=minOf(size,plain.size-start)
            plain.copyInto(target,offset,start,start+count);return count
        }
        override fun close(){plain.fill(0);input.close()}
    }
}
