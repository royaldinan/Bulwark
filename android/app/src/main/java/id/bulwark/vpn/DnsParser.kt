package id.bulwark.vpn

/**
 * Minimal DNS message parser: enough to read the queried name (QNAME) out of a query, and to build
 * an NXDOMAIN answer for blocked domains. No dependency, no full RFC implementation — only what the
 * DNS-only tunnel needs. Verified against real packet bytes in `DnsParserTest`.
 */
object DnsParser {

    private const val HEADER_LENGTH = 12
    private const val FLAG_QR = 0x8000
    private const val FLAG_AA = 0x0400
    private const val FLAG_RD = 0x0100
    private const val FLAG_RA = 0x0080
    private const val RCODE_NXDOMAIN = 0x0003
    private const val RCODE_SERVFAIL = 0x0002
    private const val RCODE_MASK = 0x000F
    private const val OPCODE_MASK = 0x7800

    data class Query(
        val id: Int,
        val flags: Int,
        val domain: String,
        val qType: Int,
        val qClass: Int,
        /** Length in bytes of the question section (QNAME + QTYPE + QCLASS). */
        val questionLength: Int,
    )

    /**
     * Parse a DNS query. Returns null when the packet is not a well-formed query (too short, a
     * response, no question, or a malformed name).
     */
    fun parseQuery(packet: ByteArray): Query? {
        if (packet.size < HEADER_LENGTH) return null

        val id = u16(packet, 0)
        val flags = u16(packet, 2)
        if (flags and FLAG_QR != 0) return null // not a query

        val questionCount = u16(packet, 4)
        if (questionCount < 1) return null

        val name = readName(packet, HEADER_LENGTH) ?: return null
        val questionStart = HEADER_LENGTH
        val questionEnd = name.end
        if (questionEnd + 4 > packet.size) return null

        val qType = u16(packet, questionEnd)
        val qClass = u16(packet, questionEnd + 2)

        return Query(
            id = id,
            flags = flags,
            domain = name.value,
            qType = qType,
            qClass = qClass,
            questionLength = questionEnd + 4 - questionStart,
        )
    }

    /** true when [packet] is a DNS query we can handle. */
    fun isQuery(packet: ByteArray): Boolean = parseQuery(packet) != null

    /**
     * Build an NXDOMAIN response for [query] with the same transaction id and question section.
     * Used to make a flagged domain fail to resolve inside the tunnel.
     */
    fun buildNxdomainResponse(query: ByteArray): ByteArray? = buildErrorResponse(query, RCODE_NXDOMAIN)

    /** Build a SERVFAIL response so a failed lookup is never silently dropped. */
    fun buildServfailResponse(query: ByteArray): ByteArray? = buildErrorResponse(query, RCODE_SERVFAIL)

    private fun buildErrorResponse(query: ByteArray, rcode: Int): ByteArray? {
        val parsed = parseQuery(query) ?: return null
        val response = ByteArray(HEADER_LENGTH + parsed.questionLength)

        // Transaction id: copy from the query.
        response[0] = query[0]
        response[1] = query[1]

        val opcode = parsed.flags and OPCODE_MASK
        val recursionDesired = parsed.flags and FLAG_RD
        val flags = FLAG_QR or opcode or FLAG_AA or recursionDesired or FLAG_RA or rcode
        response[2] = ((flags ushr 8) and 0xFF).toByte()
        response[3] = (flags and 0xFF).toByte()

        // QDCOUNT = 1, all others zero.
        response[5] = 1

        System.arraycopy(query, HEADER_LENGTH, response, HEADER_LENGTH, parsed.questionLength)
        return response
    }

    /** Read the RCODE of a DNS response (low nibble of the flags); null when too short. */
    fun responseCode(packet: ByteArray): Int? {
        if (packet.size < HEADER_LENGTH) return null
        return u16(packet, 2) and RCODE_MASK
    }

    private data class Name(val value: String, val end: Int)

    /**
     * Reads a length-prefixed DNS name starting at [offset]. Follows compression pointers so it also
     * works on response packets. [end] is the offset in the original buffer just past the name.
     */
    private fun readName(packet: ByteArray, offset: Int): Name? {
        val labels = StringBuilder()
        var pos = offset
        var end = -1
        var hops = 0

        while (true) {
            if (pos >= packet.size) return null
            val length = packet[pos].toInt() and 0xFF

            when {
                length == 0 -> {
                    if (end == -1) end = pos + 1
                    break
                }

                length and 0xC0 == 0xC0 -> { // compression pointer
                    if (pos + 1 >= packet.size) return null
                    if (end == -1) end = pos + 2
                    val pointer = ((length and 0x3F) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                    if (pointer >= packet.size || ++hops > 16) return null
                    pos = pointer
                }

                length and 0xC0 != 0 -> return null // reserved label type

                else -> {
                    if (labels.isNotEmpty()) labels.append('.')
                    val labelEnd = pos + 1 + length
                    if (labelEnd > packet.size) return null
                    for (i in pos + 1 until labelEnd) {
                        labels.append((packet[i].toInt() and 0xFF).toChar())
                    }
                    pos = labelEnd
                }
            }
        }

        return Name(labels.toString(), end)
    }

    private fun u16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
}
