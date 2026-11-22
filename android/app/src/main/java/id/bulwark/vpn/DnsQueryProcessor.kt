package id.bulwark.vpn

import id.bulwark.data.model.DomainDecision
import id.bulwark.data.model.Verdict

/**
 * Pure DNS decision core (unit-testable without Android/Room).
 *
 * Verdict handling is fail-closed: an unavailable verdict gets one bounded retry, then SERVFAIL.
 * A real MALICIOUS verdict is blocked unless an in-memory allowlist ("Lanjutkan dengan risiko
 * sendiri") permits forwarding for up to 5 minutes. [BlockGate] is the single gate for overlay +
 * event + counter: [onBlock] fires only when the gate says the warning must be shown, and the
 * service records the event only after the overlay is actually on screen.
 */
class DnsQueryProcessor(
    private val resolveVerdict: (domain: String) -> DomainDecision?,
    private val forward: (query: ByteArray) -> ByteArray?,
    private val allowList: DomainAllowList = DomainAllowList(),
    private val blockGate: BlockGate = BlockGate(),
    private val onBlock: (decision: DomainDecision) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (message: String) -> Unit = {},
) {

    fun process(queryPayload: ByteArray): ByteArray? {
        val query = DnsParser.parseQuery(queryPayload) ?: return null
        var decision: DomainDecision? = null
        for (attempt in 1..MAX_VERDICT_ATTEMPTS) {
            decision = resolveVerdict(query.domain)
            if (decision != null) break
            log("verdict unavailable domain=${query.domain} attempt=$attempt")
        }

        if (decision == null) {
            log("verdict unavailable after retry domain=${query.domain} -> SERVFAIL")
            return DnsParser.buildServfailResponse(queryPayload)
        }

        if (decision.verdict == Verdict.MALICIOUS) {
            if (allowList.isAllowed(decision.domainHash, now())) {
                log("allowlisted domain=${decision.domain} -> forward")
            } else {
                val nxdomain = DnsParser.buildNxdomainResponse(queryPayload)
                if (nxdomain != null) {
                    val gate = blockGate.decide(decision.domainHash, now())
                    log(
                        "gate domain=${decision.domain} show=${gate.show} " +
                            "reason=${gate.reason.name.lowercase()} remainingMs=${gate.remainingMillis}",
                    )
                    log("verdict=MALICIOUS domain=${decision.domain} source=${decision.source} -> NXDOMAIN")
                    if (gate.show) onBlock(decision)
                    return nxdomain
                }
            }
        }

        val answer = forward(queryPayload)
        if (answer != null) {
            log("forward ok domain=${query.domain}")
            return answer
        }

        log("forward failed domain=${query.domain} -> SERVFAIL")
        return DnsParser.buildServfailResponse(queryPayload)
    }

    private companion object {
        const val MAX_VERDICT_ATTEMPTS = 2
    }
}
