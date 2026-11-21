package id.bulwark.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainHasherTest {

    @Test
    fun normalizesLowercaseAndTrailingDot() {
        assertEquals("example.com", DomainHasher.normalize("Example.COM."))
        assertEquals("scam-domain.com", DomainHasher.normalize("  Scam-Domain.com "))
        assertEquals("example.com", DomainHasher.normalize("example.com.."))
    }

    @Test
    fun hashesWithSha256() {
        // sha256("example.com")
        assertEquals(
            "a379a6f6eeafb9a55e378c118034e2751e682fab9f2d30ab13d2125586ce1947",
            DomainHasher.domainHash("example.com"),
        )
        // sha256("scam-domain.com") - normalization must happen before hashing.
        assertEquals(
            "917ff20997cefb450b790d298cee13bf0cbdd8b650e2ce0f5d1d7fb4837ba9ea",
            DomainHasher.domainHash("Scam-Domain.com."),
        )
    }

    @Test
    fun prefixIsFirstEightBytes() {
        val hash = DomainHasher.domainHash("example.com")
        assertEquals(16, DomainHasher.prefix8(hash).length)
        assertEquals("a379a6f6eeafb9a5", DomainHasher.prefixOf("example.com"))
        assertEquals(DomainHasher.prefix8(hash), DomainHasher.prefixOf("example.com"))
    }
}
