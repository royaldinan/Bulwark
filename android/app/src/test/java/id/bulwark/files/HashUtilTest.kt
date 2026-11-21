package id.bulwark.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HashUtilTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun emptyStringMatchesKnownDigest() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            HashUtil.sha256Hex(""),
        )
    }

    @Test
    fun abcMatchesKnownDigest() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashUtil.sha256Hex("abc"),
        )
    }

    @Test
    fun quickBrownFoxMatchesKnownDigest() {
        assertEquals(
            "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
            HashUtil.sha256Hex("The quick brown fox jumps over the lazy dog"),
        )
    }

    @Test
    fun fileHashEqualsContentHash() {
        val content = "Bulwark streaming test payload"
        val file = folder.newFile("sample.apk").apply { writeText(content) }
        assertEquals(HashUtil.sha256Hex(content), HashUtil.sha256File(file))
    }

    @Test
    fun streamingHashIsChunkSizeIndependent() {
        val content = "a".repeat(200_000)
        val file = folder.newFile("big.bin").apply { writeText(content) }
        assertEquals(HashUtil.sha256Hex(content), HashUtil.sha256File(file))
    }

    @Test
    fun prefixUsesEightBytes() {
        val hash = HashUtil.sha256Hex("example.com")
        val prefix = HashUtil.prefix(hash)
        assertEquals(16, prefix.length)
        assertEquals(hash.substring(0, 16), prefix)
        assertTrue(hash.startsWith(prefix))
    }

    @Test
    fun normalizeDomainLowercasesAndTrimsTrailingDot() {
        assertEquals("example.com", HashUtil.normalizeDomain("  Example.COM. "))
    }

    @Test
    fun domainHashNormalizesBeforeHashing() {
        assertEquals(HashUtil.sha256Hex("example.com"), HashUtil.sha256OfDomain("Example.com."))
    }
}
