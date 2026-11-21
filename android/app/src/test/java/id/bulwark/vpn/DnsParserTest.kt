package id.bulwark.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DnsParser checked against real DNS packet bytes (see vectors below). */
class DnsParserTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // Real DNS query for example.com A, transaction id 0x1a2b, RD set (29 bytes).
    private val exampleQuery = bytes(
        0x1a, 0x2b, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x03, 0x63, 0x6f, 0x6d,
        0x00, 0x00, 0x01, 0x00, 0x01,
    )

    // Real DNS query for www.scam-domain.com AAAA (37 bytes).
    private val wwwQuery = bytes(
        0x2b, 0x3c, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x03, 0x77, 0x77, 0x77, 0x0b, 0x73, 0x63, 0x61, 0x6d, 0x2d, 0x64, 0x6f,
        0x6d, 0x61, 0x69, 0x6e, 0x03, 0x63, 0x6f, 0x6d, 0x00, 0x00, 0x1c, 0x00,
        0x01,
    )

    // Real DNS response for example.com (QR=1) with one A answer (45 bytes).
    private val exampleResponse = bytes(
        0x1a, 0x2b, 0x81, 0x80, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
        0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x03, 0x63, 0x6f, 0x6d,
        0x00, 0x00, 0x01, 0x00, 0x01, 0xc0, 0x0c, 0x00, 0x01, 0x00, 0x01, 0x00,
        0x00, 0x01, 0x2c, 0x00, 0x04, 0x5d, 0xb8, 0xd8, 0x22,
    )

    @Test
    fun parsesSimpleQuery() {
        val query = DnsParser.parseQuery(exampleQuery)!!
        assertEquals(0x1a2b, query.id)
        assertEquals("example.com", query.domain)
        assertEquals(1, query.qType) // A
        assertEquals(1, query.qClass) // IN
        assertEquals(17, query.questionLength) // 13-byte name + 2 + 2
        assertTrue(DnsParser.isQuery(exampleQuery))
    }

    @Test
    fun parsesMultiLabelQuery() {
        val query = DnsParser.parseQuery(wwwQuery)!!
        assertEquals("www.scam-domain.com", query.domain)
        assertEquals(28, query.qType) // AAAA
        assertEquals(0x2b3c, query.id)
    }

    @Test
    fun rejectsResponsePacket() {
        // QR bit is set: this is a response, not a query.
        assertNull(DnsParser.parseQuery(exampleResponse))
        assertFalse(DnsParser.isQuery(exampleResponse))
        // ...but its RCODE is readable (NOERROR).
        assertEquals(0, DnsParser.responseCode(exampleResponse))
    }

    @Test
    fun rejectsMalformedPackets() {
        assertNull(DnsParser.parseQuery(ByteArray(0)))
        assertNull(DnsParser.parseQuery(exampleQuery.copyOf(10))) // header truncated
        assertNull(DnsParser.parseQuery(exampleQuery.copyOf(20))) // question truncated
        // QDCOUNT = 0 has no question to parse.
        val noQuestion = exampleQuery.copyOf()
        noQuestion[4] = 0
        noQuestion[5] = 0
        assertNull(DnsParser.parseQuery(noQuestion))
    }

    @Test
    fun buildsNxdomainForBlockedDomain() {
        val response = DnsParser.buildNxdomainResponse(exampleQuery)!!

        assertEquals(exampleQuery.size, response.size)
        assertEquals(exampleQuery[0], response[0]) // transaction id preserved
        assertEquals(exampleQuery[1], response[1])
        assertTrue("QR bit set", (response[2].toInt() and 0x80) != 0)
        assertEquals(1, (response[5].toInt() and 0xFF)) // QDCOUNT = 1
        assertEquals(3, DnsParser.responseCode(response)) // NXDOMAIN
        // Question section copied verbatim.
        assertTrue(
            exampleQuery.copyOfRange(12, exampleQuery.size)
                .contentEquals(response.copyOfRange(12, response.size)),
        )
    }

    @Test
    fun nxdomainReturnsNullForNonQuery() {
        assertNull(DnsParser.buildNxdomainResponse(exampleResponse))
    }
}
