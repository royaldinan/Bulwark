package id.bulwark.vpn

/**
 * In-memory, per-session allowlist for "Lanjutkan dengan risiko sendiri". Entries expire after
 * [ttlMillis] (5 minutes) and are lost when the service restarts — the registry is never touched.
 */
class DomainAllowList(private val ttlMillis: Long = DEFAULT_TTL_MILLIS) {

    private val expiresAt = HashMap<String, Long>()

    @Synchronized
    fun allow(domainHash: String, now: Long) {
        expiresAt[domainHash] = now + ttlMillis
    }

    @Synchronized
    fun isAllowed(domainHash: String, now: Long): Boolean {
        val expiry = expiresAt[domainHash] ?: return false
        if (now >= expiry) {
            expiresAt.remove(domainHash)
            return false
        }
        return true
    }

    @Synchronized
    fun clear() {
        expiresAt.clear()
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 300_000L
    }
}
