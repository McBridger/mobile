package expo.modules.connector.interfaces

import expo.modules.connector.models.FileMetadata
import expo.modules.connector.models.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ITcpTransport {
    val connectionState: StateFlow<ConnectionState>
    val incomingMessages: Flow<Message>

    fun registerFile(metadata: FileMetadata): String
    suspend fun send(message: Message): Boolean
    fun stop()

    enum class ConnectionState {
        DISCONNECTED,
        READY,
        CONNECTED
    }
}
