package id.bulwark.vpn

/**
 * Single gate for the block side effects: one [decide] call drives exactly one overlay, one
 * `link_event` and +1 on the "Link diblokir" counter. There is no separate event window any more,
 * so an event can never be saved without its warning (or the other way round).
 *
 * Rules per domain:
 * - while an overlay is still on screen no second overlay is shown (one warning at a time);
 * - after an overlay is closed there is a FIXED [cooldownMillis] window counted from the close
 *   time; a suppressed query never moves that timestamp (no sliding window);
 * - otherwise the block is a new visit and must be shown.
 *
 * State is in memory per service instance. Safe to call from the tunnel thread and the main thread.
 */
class BlockGate(
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
) {

    enum class Reason { NEW, ACTIVE, COOLDOWN }

    data class Decision(
        val show: Boolean,
        val reason: Reason,
        val remainingMillis: Long = 0L,
    )

    private val closedAt = HashMap<String, Long>()
    private var visibleDomainHash: String? = null

    @Synchronized
    fun decide(domainHash: String, now: Long): Decision {
        if (visibleDomainHash != null) {
            // An overlay is already on screen; never stack a second one.
            return Decision(show = false, reason = Reason.ACTIVE)
        }
        val closed = closedAt[domainHash]
        if (closed != null) {
            val elapsed = now - closed
            if (elapsed < cooldownMillis) {
                return Decision(
                    show = false,
                    reason = Reason.COOLDOWN,
                    remainingMillis = cooldownMillis - elapsed,
                )
            }
            // Cooldown over: drop the timestamp without moving it (suppressed queries never
            // refreshed it in the first place).
            closedAt.remove(domainHash)
        }
        visibleDomainHash = domainHash
        return Decision(show = true, reason = Reason.NEW)
    }

    /** Overlay actually left the screen: the fixed cooldown starts now. */
    @Synchronized
    fun onWarningClosed(domainHash: String, now: Long) {
        if (visibleDomainHash == domainHash) {
            visibleDomainHash = null
            closedAt[domainHash] = now
        }
    }

    /** Clear the "visible" flag without starting a cooldown (e.g. overlay could not be shown). */
    @Synchronized
    fun cancelWarning(domainHash: String) {
        if (visibleDomainHash == domainHash) visibleDomainHash = null
    }

    @Synchronized
    fun onServiceStopped() {
        visibleDomainHash = null
    }

    companion object {
        /** Fixed window after the warning was closed; queries in between must not extend it. */
        const val DEFAULT_COOLDOWN_MILLIS = 15_000L
    }
}
