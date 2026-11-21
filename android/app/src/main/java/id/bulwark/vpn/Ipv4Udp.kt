package id.bulwark.vpn

/**
 * Just enough IPv4 + UDP to read DNS packets off the TUN and write the answers back.
 * Only IPv4 is handled; IPv6 is intentionally out of scope for the DNS-only demo tunnel.
 */
internal object Ipv4Udp {

    private const val IPV4_VERSION = 4
    private const val IPV4_MIN_HEADER = 20
    private const val UDP_HEADER = 8
    private const val PROTOCOL_UDP = 17
    private const val DEFAULT_TTL = 64
    private const val FLAG_DONT_FRAGMENT = 0x4000

    data class Packet(
        val sourceIp: ByteArray,
        val destIp: ByteArray,
        val sourcePort: Int,
        val destPort: Int,
        val dscp: Int,
        val identification: Int,
        val payload: ByteArray,
    )

    /** Parse an IPv4/UDP datagram from [buffer]. Returns null for anything else. */
    fun parse(buffer: ByteArray, length: Int): Packet? {
        if (length < IPV4_MIN_HEADER + UDP_HEADER) return null

        val version = (buffer[0].toInt() ushr 4) and 0x0F
        if (version != IPV4_VERSION) return null

        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (ihl < IPV4_MIN_HEADER || length < ihl + UDP_HEADER) return null

        val totalLength = u16(buffer, 2)
        if (totalLength < ihl + UDP_HEADER || totalLength > length) return null

        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null

        val udpLength = u16(buffer, ihl + 4)
        if (udpLength < UDP_HEADER) return null

        val payloadStart = ihl + UDP_HEADER
        val payloadEnd = minOf(totalLength, ihl + udpLength)
        if (payloadEnd < payloadStart) return null

        return Packet(
            sourceIp = buffer.copyOfRange(12, 16),
            destIp = buffer.copyOfRange(16, 20),
            sourcePort = u16(buffer, ihl),
            destPort = u16(buffer, ihl + 2),
            dscp = buffer[1].toInt() and 0xFF,
            identification = u16(buffer, 4),
            payload = buffer.copyOfRange(payloadStart, payloadEnd),
        )
    }

    /**
     * Build the reversed IPv4/UDP datagram carrying [payload] back to the original sender:
     * source becomes the original destination (the virtual DNS address) and vice versa.
     */
    fun buildResponse(request: Packet, payload: ByteArray): ByteArray {
        val total = IPV4_MIN_HEADER + UDP_HEADER + payload.size
        val out = ByteArray(total)
        val udpLength = UDP_HEADER + payload.size

        // IPv4 header.
        out[0] = 0x45 // version 4, IHL 5
        out[1] = request.dscp.toByte()
        putU16(out, 2, total)
        putU16(out, 4, request.identification)
        putU16(out, 6, FLAG_DONT_FRAGMENT)
        out[8] = DEFAULT_TTL.toByte()
        out[9] = PROTOCOL_UDP.toByte()
        // checksum at 10..11 filled below
        System.arraycopy(request.destIp, 0, out, 12, 4) // source = virtual dns
        System.arraycopy(request.sourceIp, 0, out, 16, 4) // dest = client
        putU16(out, 10, 0)
        putU16(out, 10, checksum(out, 0, IPV4_MIN_HEADER))

        // UDP header.
        putU16(out, IPV4_MIN_HEADER, request.destPort) // source port = 53
        putU16(out, IPV4_MIN_HEADER + 2, request.sourcePort) // dest port = client
        putU16(out, IPV4_MIN_HEADER + 4, udpLength)
        System.arraycopy(payload, 0, out, IPV4_MIN_HEADER + UDP_HEADER, payload.size)
        putU16(out, IPV4_MIN_HEADER + 6, 0)
        putU16(out, IPV4_MIN_HEADER + 6, udpChecksum(out, udpLength))

        return out
    }

    private fun udpChecksum(packet: ByteArray, udpLength: Int): Int {
        var sum = 0
        // Pseudo-header: source ip, dest ip, zero, protocol, udp length.
        for (i in 12 until 20 step 2) sum += u16(packet, i)
        sum += PROTOCOL_UDP
        sum += udpLength
        // UDP header + payload with checksum field zeroed.
        var i = IPV4_MIN_HEADER
        val end = IPV4_MIN_HEADER + udpLength
        while (i + 1 < end) {
            sum += u16(packet, i)
            i += 2
        }
        if (i < end) sum += (packet[i].toInt() and 0xFF) shl 8
        val folded = fold(sum)
        // A UDP checksum of 0 means "no checksum"; emit 0xFFFF in that (rare) case.
        return if (folded == 0) 0xFFFF else folded
    }

    private fun checksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += u16(buffer, i)
            i += 2
        }
        if (i < end) sum += (buffer[i].toInt() and 0xFF) shl 8
        return fold(sum)
    }

    private fun fold(sum0: Int): Int {
        var sum = sum0
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }

    private fun u16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun putU16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }
}
