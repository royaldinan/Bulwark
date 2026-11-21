package id.bulwark.files

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object HashUtil {

    const val PREFIX_BYTES: Int = 8
    private const val BUFFER_SIZE: Int = 64 * 1024
    private const val HEX = "0123456789abcdef"

    fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    fun sha256Hex(input: ByteArray): String = sha256(input).toHex()

    fun sha256Hex(input: String): String = sha256Hex(input.toByteArray(Charsets.UTF_8))

    fun sha256Stream(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var read = stream.read(buffer)
        while (read != -1) {
            if (read > 0) digest.update(buffer, 0, read)
            read = stream.read(buffer)
        }
        return digest.digest().toHex()
    }

    fun sha256File(file: File): String = file.inputStream().use { sha256Stream(it) }

    fun prefix(hashHex: String, bytes: Int = PREFIX_BYTES): String {
        val hexLength = (bytes * 2).coerceIn(0, hashHex.length)
        return hashHex.substring(0, hexLength).lowercase()
    }

    fun normalizeDomain(domain: String): String =
        domain.trim().lowercase().removeSuffix(".")

    fun sha256OfDomain(domain: String): String = sha256Hex(normalizeDomain(domain))

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        for (i in indices) {
            val value = this[i].toInt() and 0xFF
            out[i * 2] = HEX[value ushr 4]
            out[i * 2 + 1] = HEX[value and 0x0F]
        }
        return String(out)
    }
}
