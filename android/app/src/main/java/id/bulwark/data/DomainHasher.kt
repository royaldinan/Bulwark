package id.bulwark.data

import java.security.MessageDigest
import java.util.Locale

/**
 * SHA-256 hashing for domains plus the 8-byte prefix used for network lookups.
 *
 * Rules (PRD "Keputusan teknis" 4 & 5): SHA-256 (never keccak256), 8-byte prefix, and the domain is
 * normalized first (lowercase, no trailing dot). The client and the Mediation API must use the exact
 * same normalization.
 */
object DomainHasher {

    const val PREFIX_BYTES = 8
    const val PREFIX_HEX_LENGTH = PREFIX_BYTES * 2

    fun normalize(domain: String): String =
        domain.trim().lowercase(Locale.ROOT).trimEnd('.')

    fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    fun sha256Hex(value: String): String = toHex(sha256(value))

    /** Full lowercase SHA-256 hex of the normalized domain. */
    fun domainHash(domain: String): String = sha256Hex(normalize(domain))

    /** First 8 bytes (16 hex chars) of a full hash. */
    fun prefix8(domainHashHex: String): String = domainHashHex.take(PREFIX_HEX_LENGTH)

    /** 8-byte prefix for a domain, computed in one step. */
    fun prefixOf(domain: String): String = prefix8(domainHash(domain))

    private const val HEX = "0123456789abcdef"

    private fun toHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }
}
