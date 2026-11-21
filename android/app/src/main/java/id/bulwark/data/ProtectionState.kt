package id.bulwark.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Live protection state set by `BulwarkVpnService` when the tunnel starts/stops. Beranda reads this
 * so "Perlindungan aktif" reflects reality instead of a stored guess.
 */
object ProtectionState {

    val active = MutableStateFlow(false)

    fun set(value: Boolean) {
        active.value = value
    }
}
