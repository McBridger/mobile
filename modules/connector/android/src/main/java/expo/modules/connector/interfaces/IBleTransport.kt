package expo.modules.connector.interfaces

import expo.modules.connector.models.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IBleTransport {
    val state: StateFlow<State>
    val incomingMessages: Flow<Message>

    fun connect(address: String)
    fun disconnect()

    suspend fun send(message: Message)
    fun stop()

    enum class State {
        IDLE,
        SCANNING,
        CONNECTING,
        CONNECTED,
        POWERED_OFF,
        UNSUPPORTED,
        ERROR
    }
}