package id.bulwark.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ipv4Udp checked against a real IPv4/UDP datagram carrying a DNS query. */
class Ipv4UdpTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // Real IPv4/UDP packet: 10.111.222.1:40000 -> 10.111.222.2:53, payload = example.com A query.
    private val datagram = bytes(
        0x45, 0x00, 0x00, 0x39, 0x12, 0x34, 0x40, 0x00, 0x40, 0x11, 0x57, 0x9e,
        0x0a, 0x6f, 0xde, 0x01, 0x0a, 0x6f, 0xde, 0x02, 0x9c, 0x40, 0x00, 0x35,
        0x00, 0x25, 0xa8, 0xb3, 0x1a, 0x2b, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65,
        0x03, 0x63, 0x6f, 0x6d, 0x00, 0x00, 0x01, 0x00, 0x01,
    )

    @Test
    fun parsesRealDatagram() {
        val packet = Ipv4Udp.parse(datagram, datagram.size)!!
        assertArrayEquals(bytes(0x0a, 0x6f, 0xde, 0x01), packet.sourceIp)
        assertArrayEquals(bytes(0x0a, 0x6f, 0xde, 0x02), packet.destIp)
        assertEquals(40000, packet.sourcePort)
        assertEquals(53, packet.destPort)
        assertTrue(DnsParser.isQuery(packet.payload))
        assertEquals("example.com", DnsParser.parseQuery(packet.payload)!!.domain)
    }

    @Test
    fun rejectsNonUdpAndTruncated() {
        val tcp = datagram.copyOf()
        tcp[9] = 6 // protocol TCP
        assertNull(Ipv4Udp.parse(tcp, tcp.size))
        assertNull(Ipv4Udp.parse(datagram, 10)) // truncated
        assertNull(Ipv4Udp.parse(ByteArray(4), 4))
    }

    @Test
    fun buildsResponseWithSwappedEndpointsAndValidChecksums() {
        val request = Ipv4Udp.parse(datagram, datagram.size)!!
        val answer = DnsParser.buildNxdomainResponse(request.payload)!!
        val responseBytes = Ipv4Udp.buildResponse(request, answer)

        val response = Ipv4Udp.parse(responseBytes, responseBytes.size)!!
        assertArrayEquals(request.destIp, response.sourceIp) // source = virtual dns
        assertArrayEquals(request.sourceIp, response.destIp) // dest = client
        assertEquals(53, response.sourcePort)
        assertEquals(40000, response.destPort)
        assertArrayEquals(answer, response.payload)

        assertTrue("valid IPv4 header checksum", ipv4ChecksumValid(responseBytes))
        assertTrue("valid UDP checksum", udpChecksumValid(responseBytes, responseBytes.size))
    }

    // Standard ones-complement check: a valid IPv4 header folds to 0xFFFF.
    private fun ipv4ChecksumValid(packet: ByteArray): Boolean = fold(packet, 0, 20) == 0xFFFF

    private fun udpChecksumValid(packet: ByteArray, total: Int): Boolean {
        var sum = 0
        for (i in 12 until 20 step 2) sum += u16(packet, i)
        sum += 17
        val udpLength = total - 20
        sum += udpLength
        var i = 20
        while (i + 1 < total) {
            sum += u16(packet, i)
            i += 2
        }
        if (i < total) sum += (packet[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum and 0xFFFF) == 0xFFFF
    }

    private fun fold(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += u16(data, i)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum and 0xFFFF
    }

    private fun u16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
}
