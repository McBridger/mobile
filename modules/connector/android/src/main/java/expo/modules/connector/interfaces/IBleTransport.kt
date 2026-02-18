package expo.modules.connector.interfaces

import expo.modules.connector.models.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IBleTransport {
    val connectionState: StateFlow<ConnectionState>
    val incomingMessages: Flow<Message>

    fun connect(address: String)
    fun disconnect()

    suspend fun send(message: Message)
    fun stop()

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        POWERED_OFF,
        UNSUPPORTED,
        READY
    }
}