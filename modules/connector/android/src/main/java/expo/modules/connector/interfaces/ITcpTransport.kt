package expo.modules.connector.interfaces

import expo.modules.connector.models.BlobMessage
import expo.modules.connector.models.FileMetadata
import expo.modules.connector.models.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ITcpTransport {
    val connectionState: StateFlow<ConnectionState>
    val incomingMessages: Flow<Message>

    suspend fun send(message: Message)
    suspend fun sendBlob(message: BlobMessage, inputStream: java.io.InputStream, host: String, port: Int)
    fun stop()

    enum class ConnectionState {
        DISCONNECTED,
        READY,
        CONNECTED
    }
}
