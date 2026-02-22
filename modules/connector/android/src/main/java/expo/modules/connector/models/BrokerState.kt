package expo.modules.connector.models

import android.os.Bundle
import expo.modules.connector.interfaces.IBleTransport
import expo.modules.connector.interfaces.ITcpTransport

enum class EncryptionState { IDLE, ENCRYPTING, KEYS_READY, ERROR }

data class Status<T : Enum<*>>(val current: T, val error: String? = null) {
    fun toBundle(): Bundle = Bundle().apply {
        putString("current", current.name)
        putString("error", error)
    }
}

/**
 * Single Source of Truth for the Broker's state.
 * Structured hierarchically for better JS consumption.
 */
data class BrokerState(
    val ble: Status<IBleTransport.State> = Status(IBleTransport.State.IDLE),
    val tcp: Status<ITcpTransport.State> = Status(ITcpTransport.State.IDLE),
    val encryption: Status<EncryptionState> = Status(EncryptionState.IDLE),
    val items: List<Bundle> = emptyList()
) {
    fun toBundle(): Bundle = Bundle().apply {
        putBundle("ble", ble.toBundle())
        putBundle("tcp", tcp.toBundle())
        putBundle("encryption", encryption.toBundle())
    }
}
