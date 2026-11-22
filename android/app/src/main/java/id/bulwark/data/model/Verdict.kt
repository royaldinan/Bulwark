package id.bulwark.data.model

import java.util.Locale

/** Verdict values shared with the Mediation API wire format. */
enum class Verdict(val wire: String) {
    MALICIOUS("malicious"),
    SAFE("safe"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String): Verdict =
            entries.firstOrNull { it.wire == value.lowercase(Locale.ROOT) } ?: UNKNOWN
    }
}

/** Where a verdict came from. Matches PRD `source` values plus the local cache. */
enum class VerdictSource(val wire: String) {
    REGISTRY("registry"),
    SAFE_BROWSING("safebrowsing"),
    CACHE("cache"),
    NONE("none");

    companion object {
        fun fromWire(value: String): VerdictSource =
            entries.firstOrNull { it.wire == value.lowercase(Locale.ROOT) } ?: NONE
    }
}

/** Link decision recorded in `link_event`. */
enum class LinkAction(val wire: String) {
    BLOCKED("blocked"),
    ALLOWED("allowed"),
}

/** Result of looking up a domain in the cache/registry, ready for the tunnel + overlay. */
data class DomainDecision(
    val domain: String,
    val domainHash: String,
    val prefix: String,
    val verdict: Verdict,
    val source: VerdictSource,
    /** true when the verdict was fetched from a remote source this call (cache miss). */
    val fresh: Boolean,
)

/** A verdict returned by a remote source (Mediation API) for a single domain. */
data class RemoteVerdict(
    val verdict: Verdict,
    val source: VerdictSource,
)

/** Report kind shared with the Mediation API `POST /v1/report`. */
enum class ReportKind(val wire: String) {
    DOMAIN("domain"),
    APK("apk"),
}

/** Status of a stored report row (PRD table `report`). */
enum class ReportStatus(val wire: String) {
    PENDING("pending"),
    SENT("sent"),
    FAILED("failed"),
    ALREADY_LISTED("already_listed"),
}

/** The mediation API confirmed that this exact report is already registered. */
class ReportAlreadyListedException : Exception("already_listed")

/**
 * Port used by the tunnel and the report flow. Implemented by `MediationApi` (task 4). It is
 * optional: without it every cache miss resolves to UNKNOWN and reports are stored as failed.
 */
interface MediationGateway {
    suspend fun checkDomain(domain: String, domainHash: String, prefix: String): RemoteVerdict?

    /** Check a full SHA-256 APK hash. Matches `POST /v1/check/apk` `{ sha256 }`. */
    suspend fun checkApk(sha256: String): RemoteVerdict?

    /** Submits a report; returns the real transaction hash, or null on failure. */
    suspend fun report(kind: ReportKind, hash: String): String?
}
