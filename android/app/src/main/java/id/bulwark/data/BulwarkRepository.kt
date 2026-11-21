package id.bulwark.data

import id.bulwark.data.entity.DomainCacheEntity
import id.bulwark.data.entity.LinkEventEntity
import id.bulwark.data.entity.ReportEntity
import id.bulwark.data.model.DomainDecision
import id.bulwark.data.model.LinkAction
import id.bulwark.data.model.MediationGateway
import id.bulwark.data.model.ReportKind
import id.bulwark.data.model.ReportStatus
import id.bulwark.data.model.Verdict
import id.bulwark.data.model.VerdictSource

/**
 * Single entry point for verdicts and counters.
 *
 * The tunnel asks [checkDomain]; cache hits stay local, misses go to the optional remote source
 * (Mediation API, wired in task 4). Counters are read straight from `link_event` so every number on
 * screen comes from real user activity (AGENTS hard rule 1).
 */
class BulwarkRepository(
    private val database: AppDatabase,
    private val gateway: MediationGateway? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val domainCacheDao = database.domainCacheDao()
    private val linkEventDao = database.linkEventDao()
    private val reportDao = database.reportDao()

    suspend fun checkDomain(domain: String): DomainDecision {
        val normalized = DomainHasher.normalize(domain)
        val hash = DomainHasher.sha256Hex(normalized)
        val prefix = DomainHasher.prefix8(hash)
        val now = clock()

        val cached = domainCacheDao.find(hash)
        if (cached != null && cached.expiresAt > now) {
            return DomainDecision(
                domain = normalized,
                domainHash = hash,
                prefix = prefix,
                verdict = Verdict.fromWire(cached.verdict),
                source = VerdictSource.CACHE,
                fresh = false,
            )
        }

        val remoteVerdict = gateway?.checkDomain(normalized, hash, prefix)
        if (remoteVerdict != null) {
            domainCacheDao.upsert(
                DomainCacheEntity(
                    domainHash = hash,
                    domain = normalized,
                    verdict = remoteVerdict.verdict.wire,
                    source = remoteVerdict.source.wire,
                    checkedAt = now,
                    expiresAt = now + ttlMillis(remoteVerdict.verdict),
                ),
            )
            return DomainDecision(
                domain = normalized,
                domainHash = hash,
                prefix = prefix,
                verdict = remoteVerdict.verdict,
                source = remoteVerdict.source,
                fresh = true,
            )
        }

        // No verdict source available: treat as unknown, but do not cache a guess.
        return DomainDecision(
            domain = normalized,
            domainHash = hash,
            prefix = prefix,
            verdict = Verdict.UNKNOWN,
            source = VerdictSource.NONE,
            fresh = true,
        )
    }

    suspend fun recordLinkEvent(decision: DomainDecision, action: LinkAction) {
        linkEventDao.insert(
            LinkEventEntity(
                domainHash = decision.domainHash,
                domain = decision.domain,
                action = action.wire,
                source = decision.source.wire,
                createdAt = clock(),
            ),
        )
    }

    suspend fun countLinks(action: LinkAction): Int = linkEventDao.countByAction(action.wire)

    /**
     * Report a domain to the public registry through the Mediation API. The row is written first as
     * `pending`, then updated with the real tx hash (`sent`) or marked `failed`. Never fabricates a
     * hash (AGENTS hard rule 1).
     */
    suspend fun reportDomain(domain: String): ReportEntity =
        submitReport(ReportKind.DOMAIN, DomainHasher.domainHash(domain))

    /** Report an APK by its full SHA-256 hex. */
    suspend fun reportApk(sha256Hex: String): ReportEntity =
        submitReport(ReportKind.APK, sha256Hex.trim().lowercase(java.util.Locale.ROOT))

    suspend fun reports(): List<ReportEntity> = reportDao.all()

    private suspend fun submitReport(kind: ReportKind, hash: String): ReportEntity {
        val id = reportDao.insert(
            ReportEntity(
                kind = kind.wire,
                hash = hash,
                txHash = null,
                status = ReportStatus.PENDING.wire,
                createdAt = clock(),
            ),
        )
        val txHash = try {
            gateway?.report(kind, hash)
        } catch (e: Exception) {
            null
        }
        val status = if (txHash != null) ReportStatus.SENT else ReportStatus.FAILED
        reportDao.updateResult(id, txHash, status.wire)
        return reportDao.findById(id) ?: error("report row $id disappeared")
    }

    private fun ttlMillis(verdict: Verdict): Long = when (verdict) {
        // Malicious may be cached long; safe/unknown must expire within 30 s for cross-device sync.
        Verdict.MALICIOUS -> MALICIOUS_TTL_MILLIS
        Verdict.SAFE, Verdict.UNKNOWN -> SHORT_TTL_MILLIS
    }

    companion object {
        const val SHORT_TTL_MILLIS = 30_000L
        const val MALICIOUS_TTL_MILLIS = 24 * 60 * 60 * 1000L
    }
}
