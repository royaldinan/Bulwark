package id.bulwark.overlay

import java.lang.ref.WeakReference

/**
 * Same-process bridge between [id.bulwark.vpn.BulwarkVpnService] and [BlockWarningActivity].
 *
 * The warning is a real Activity (not a raw overlay window) because a focusable
 * TYPE_APPLICATION_OVERLAY window keeps window focus even after the user presses HOME on this
 * device, so "user left the app" could never be detected. An Activity gets onStop()/Back normally.
 */
object WarningBridge {

    var onShown: (() -> Unit)? = null
    var onStaySafe: (() -> Unit)? = null
    var onProceed: (() -> Unit)? = null
    var onClosed: ((reason: String) -> Unit)? = null

    private var activity: WeakReference<BlockWarningActivity>? = null

    fun attach(target: BlockWarningActivity) {
        activity = WeakReference(target)
    }

    fun detach(target: BlockWarningActivity) {
        if (activity?.get() === target) activity = null
    }

    fun isAttached(): Boolean = activity?.get() != null

    /** Ask the visible warning to close itself with [reason] (e.g. service_stop). */
    fun finish(reason: String) {
        activity?.get()?.close(reason)
    }

    fun reset() {
        onShown = null
        onStaySafe = null
        onProceed = null
        onClosed = null
    }
}
