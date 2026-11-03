package id.iswara.music.model

import java.io.ByteArrayOutputStream
import java.io.InputStream

object BackupReader {
    fun read(stream: InputStream, limit: Int = 10_000_000): String {
        require(limit > 0)
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
            if (count == -1) break
            if (count == 0) {
                val byte = stream.read()
                if (byte == -1) break
                output.write(byte)
            } else output.write(buffer, 0, count)
            require(output.size() <= limit) { "Backup terlalu besar" }
        }
        return output.toString("UTF-8")
    }
}
