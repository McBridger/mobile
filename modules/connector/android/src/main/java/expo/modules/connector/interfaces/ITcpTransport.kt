package expo.modules.connector.interfaces

import expo.modules.connector.models.BlobMessage
import expo.modules.connector.models.FileMetadata
import expo.modules.connector.models.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ITcpTransport {
    val state: StateFlow<State>
    val incomingMessages: Flow<Message>

    fun connect(host: String, port: Int)
    suspend fun send(message: Message)
    suspend fun sendBlob(message: BlobMessage, inputStream: java.io.InputStream, host: String, port: Int)
    fun stop()

    enum class State {
        IDLE,         // Keys not ready, transport stopped
        READY,        // Server started, waiting for target info
        PINGING,      // Target known, attempting to establish connection
        CONNECTED,    // Control socket established, heartbeats flowing
        TRANSFERRING, // Active data stream in progress
        ERROR         // Fatal failure
    }
}
