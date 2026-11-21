package id.bulwark.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportValidatorTest {

    @Test
    fun acceptsValidDomainsAndNormalizes() {
        assertEquals("example.com", ReportValidator.validDomain("Example.com."))
        assertEquals("sub.example.co.id", ReportValidator.validDomain(" sub.example.co.id "))
        assertEquals("a-b.example.com", ReportValidator.validDomain("a-b.example.com"))
    }

    @Test
    fun rejectsEmptyAndMalformedDomains() {
        assertNull(ReportValidator.validDomain(""))
        assertNull(ReportValidator.validDomain("   "))
        assertNull(ReportValidator.validDomain("no-dot"))
        assertNull(ReportValidator.validDomain("has space.com"))
        assertNull(ReportValidator.validDomain(".example.com"))
    }

    @Test
    fun acceptsOnlyFullSha256Hex() {
        val hash = "a".repeat(64)
        assertEquals(hash, ReportValidator.validSha256(hash))
        assertEquals(hash, ReportValidator.validSha256("A".repeat(64)))
        assertNull(ReportValidator.validSha256("abc"))
        assertNull(ReportValidator.validSha256("g".repeat(64)))
    }
}
