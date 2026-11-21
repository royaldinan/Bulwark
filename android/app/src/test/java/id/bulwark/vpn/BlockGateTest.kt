package id.bulwark.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate rules from the demo spec:
 * (a) 10 queries of the same domain within 1 s -> one overlay/event;
 * (b) after the warning is closed and 15 s pass, a new query shows again;
 * (c) suppressed queries must NOT extend the fixed cooldown;
 * (d) an overlay that is still on screen is never duplicated;
 * (e) "Lanjutkan" allowlist expiry blocks again (covered in DnsQueryProcessorTest).
 */
class BlockGateTest {

    private val hash = "a379a6f6eeafb9a5"

    @Test
    fun tenQueriesInOneSecondYieldOneShow() {
        val gate = BlockGate()
        var shown = 0
        repeat(10) { i ->
            val decision = gate.decide(hash, now = 1_000L + i * 100L)
            if (decision.show) shown++
        }
        assertEquals(1, shown)
    }

    @Test
    fun newVisitAfterFixedCooldownShowsAgain() {
        val gate = BlockGate()
        assertTrue(gate.decide(hash, now = 0L).show)
        gate.onWarningClosed(hash, now = 2_000L) // user closed the warning

        val stillCooling = gate.decide(hash, now = 2_000L + 14_999L)
        assertFalse(stillCooling.show)
        assertEquals(BlockGate.Reason.COOLDOWN, stillCooling.reason)
        assertEquals(1L, stillCooling.remainingMillis)

        assertTrue(gate.decide(hash, now = 2_000L + 15_000L).show)
    }

    @Test
    fun suppressedQueriesDoNotExtendCooldown() {
        val gate = BlockGate()
        assertTrue(gate.decide(hash, now = 0L).show)
        gate.onWarningClosed(hash, now = 1_000L)

        // Chrome retries every ~5 s; each one is suppressed and must not move the close time.
        listOf(5_000L, 10_000L, 12_000L, 15_000L).forEach { at ->
            val decision = gate.decide(hash, now = at)
            assertFalse("query at $at must stay suppressed", decision.show)
            assertEquals(BlockGate.Reason.COOLDOWN, decision.reason)
        }

        // 15 s after the close (1_000 + 15_000) a new visit must show, not later.
        assertTrue(gate.decide(hash, now = 16_001L).show)
    }

    @Test
    fun visibleWarningIsNeverDuplicated() {
        val gate = BlockGate()
        assertTrue(gate.decide(hash, now = 0L).show)

        val duplicate = gate.decide(hash, now = 30_000L)
        assertFalse(duplicate.show)
        assertEquals(BlockGate.Reason.ACTIVE, duplicate.reason)
    }

    @Test
    fun otherDomainIsSuppressedWhileWarningVisible() {
        val gate = BlockGate()
        assertTrue(gate.decide(hash, now = 0L).show)

        val other = gate.decide("ffffffffffffffff", now = 1_000L)
        assertFalse(other.show)
        assertEquals(BlockGate.Reason.ACTIVE, other.reason)
    }

    @Test
    fun serviceStopReleasesVisibleFlagWithoutCooldown() {
        val gate = BlockGate()
        assertTrue(gate.decide(hash, now = 0L).show)
        gate.onServiceStopped()

        assertTrue(gate.decide(hash, now = 1_000L).show)
    }

    @Test
    fun failedOverlayDoesNotStartCooldown() {
        val gate = BlockGate()
        assertTrue(gate.decide(hash, now = 0L).show)
        gate.cancelWarning(hash) // addView failed / permission missing

        assertTrue("a failed overlay must not start the 15 s window", gate.decide(hash, now = 1L).show)
    }
}
