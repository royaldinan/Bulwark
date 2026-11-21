package id.bulwark.vpn

import id.bulwark.data.model.DomainDecision
import id.bulwark.data.model.Verdict
import id.bulwark.data.model.VerdictSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the block flow (pure JVM; the overlay/event sinks are injected).
 * Room/WindowManager are not available here, so "event saved"/"warning shown" are observed via the
 * [DnsQueryProcessor.onBlock] flags that the service uses to drive them.
 */
class DnsQueryProcessorTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // Real DNS query for example.com A (transaction id 0x1a2b).
    private val query = bytes(
        0x1a, 0x2b, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x03, 0x63, 0x6f, 0x6d,
        0x00, 0x00, 0x01, 0x00, 0x01,
    )

    private fun malicious(hash: String = "a379a6f6eeafb9a5") = DomainDecision(
        domain = "example.com",
        domainHash = hash,
        prefix = hash,
        verdict = Verdict.MALICIOUS,
        source = VerdictSource.REGISTRY,
        fresh = false,
    )

    @Test
    fun maliciousVerdictYieldsNxdomainAndRecordsEvent() {
        val calls = mutableListOf<String>()
        var forwarded = false
        val processor = DnsQueryProcessor(
            resolveVerdict = { malicious() },
            forward = {
                forwarded = true
                null
            },
            onBlock = { decision -> calls += decision.domainHash },
            now = { 1_000L },
            log = {},
        )

        val response = processor.process(query)

        assertNotNull(response)
        assertEquals(3, DnsParser.responseCode(response!!) ?: -1) // NXDOMAIN
        assertFalse("malicious domains must not be forwarded", forwarded)
        assertEquals(1, calls.size)
        assertEquals(malicious().domainHash, calls[0])
    }

    @Test
    fun repeatedQueriesWithinOneSecondYieldSingleWarningAndEvent() {
        var now = 1_000L
        val calls = mutableListOf<String>()
        val processor = DnsQueryProcessor(
            resolveVerdict = { malicious() },
            forward = { null },
            allowList = DomainAllowList(),
            blockGate = BlockGate(),
            onBlock = { decision -> calls += decision.domainHash },
            now = { now },
            log = {},
        )

        repeat(10) {
            val response = processor.process(query)
            assertEquals(3, DnsParser.responseCode(response!!) ?: -1)
            now += 100L // all ten queries happen within one second
        }

        assertEquals(1, calls.size) // ten queries, one gate decision: one overlay + one event
    }

    @Test
    fun allowlistedDomainIsForwarded() {
        val answer = bytes(0xDE, 0xAD, 0xBE, 0xEF)
        var forwarded = false
        val calls = mutableListOf<String>()
        val now = 5_000L
        val allowList = DomainAllowList(ttlMillis = 300_000L)
        val processor = DnsQueryProcessor(
            resolveVerdict = { malicious() },
            forward = {
                forwarded = true
                answer
            },
            allowList = allowList,
            blockGate = BlockGate(),
            onBlock = { decision -> calls += decision.domainHash },
            now = { now },
            log = {},
        )
        allowList.allow(malicious().domainHash, now)

        val response = processor.process(query)

        assertTrue("allowlisted domain must be forwarded", forwarded)
        assertArrayEquals(answer, response!!)
        assertTrue("no block side effects while allowlisted", calls.isEmpty())
    }

    @Test
    fun expiredAllowlistBlocksAgain() {
        var now = 10_000L
        val calls = mutableListOf<String>()
        val allowList = DomainAllowList(ttlMillis = 300_000L)
        val processor = DnsQueryProcessor(
            resolveVerdict = { malicious() },
            forward = { null },
            allowList = allowList,
            blockGate = BlockGate(),
            onBlock = { decision -> calls += decision.domainHash },
            now = { now },
            log = {},
        )
        allowList.allow(malicious().domainHash, now)
        now += 300_001L // 5 minutes + 1 ms

        val response = processor.process(query)

        assertNotNull(response)
        assertEquals(3, DnsParser.responseCode(response!!) ?: -1) // blocked again
        assertEquals(1, calls.size)
        assertEquals(malicious().domainHash, calls[0])
    }

    @Test
    fun forwardFailureYieldsServfailInsteadOfDropping() {
        val processor = DnsQueryProcessor(
            resolveVerdict = { null }, // unknown / verdict timeout
            forward = { null }, // forwarding failed after retries
            log = {},
        )

        val response = processor.process(query)

        assertNotNull(response)
        assertEquals(2, DnsParser.responseCode(response!!) ?: -1) // SERVFAIL, never silent
    }
}
