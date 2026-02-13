package expo.modules.connector.models

import android.os.Bundle

enum class BleState { IDLE, SCANNING, CONNECTING, CONNECTED, ERROR }
enum class TcpState { IDLE, PINGING, TRANSFERRING, ERROR }
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
    val ble: Status<BleState> = Status(BleState.IDLE),
    val tcp: Status<TcpState> = Status(TcpState.IDLE),
    val encryption: Status<EncryptionState> = Status(EncryptionState.IDLE),
    val items: List<Bundle> = emptyList()
) {
    fun toBundle(): Bundle = Bundle().apply {
        putBundle("ble", ble.toBundle())
        putBundle("tcp", tcp.toBundle())
        putBundle("encryption", encryption.toBundle())
    }
}
