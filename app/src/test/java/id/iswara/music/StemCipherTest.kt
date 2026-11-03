package id.iswara.music

import id.iswara.music.data.StemCipher
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import javax.crypto.spec.SecretKeySpec

class StemCipherTest {
    @Test fun authenticatedBlocksSupportSeekAndRejectDifferentOwnerCorruptionAndReordering() {
        val folder=Files.createTempDirectory("iswara-stem-test").toFile()
        try {
            val bytes=ByteArray(StemCipher.BLOCK*3+150){((it*31)%251).toByte()}
            val input=java.io.File(folder,"input").apply {writeBytes(bytes)};val encrypted=java.io.File(folder,"audio.enc")
            val key=SecretKeySpec(ByteArray(32){it.toByte()},"AES")
            StemCipher.encrypt(input,encrypted,key,"alice:track:vocal:generation")
            StemCipher.Reader(encrypted,key,"alice:track:vocal:generation").use {reader ->
                assertEquals(bytes.size.toLong(),reader.length)
                for(position in listOf(0,65530,65536,99999,bytes.lastIndex)) {
                    val result=ByteArray(500);val count=reader.read(position.toLong(),result,0,result.size)
                    assertArrayEquals(bytes.copyOfRange(position,position+count),result.copyOf(count))
                }
            }
            fun rejected(identity:String){try {StemCipher.Reader(encrypted,key,identity).use {it.read(0,ByteArray(64),0,64)};fail("Unauthenticated stem accepted")}catch(_:javax.crypto.AEADBadTagException){}}
            rejected("bob:track:vocal:generation")
            val original=encrypted.readBytes();val tampered=original.copyOf();tampered[44]=(tampered[44].toInt() xor 1).toByte();encrypted.writeBytes(tampered);rejected("alice:track:vocal:generation")
            val swapped=original.copyOf();val width=StemCipher.BLOCK+16;original.copyInto(swapped,28+width,28,28+width);original.copyInto(swapped,28,28+width,28+width*2);encrypted.writeBytes(swapped);rejected("alice:track:vocal:generation")
        } finally {folder.listFiles()?.forEach {it.delete()};folder.delete()}
    }
}
