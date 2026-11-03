package id.iswara.music.data

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun offlineFileName(owner: String, id: String) = MessageDigest.getInstance("SHA-256").digest("$owner:$id".toByteArray()).joinToString("") { "%02x".format(it) } + ".audio"
internal fun decryptOffline(encrypted: ByteArray, key: SecretKey): ByteArray {
    require(encrypted.size >= 28) { "Unduhan rusak. Unduh ulang lagu ini." }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(0, 12))) }
    return cipher.doFinal(encrypted, 12, encrypted.size - 12)
}
