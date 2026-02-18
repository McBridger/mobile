package expo.modules.connector.transports.tcp

import android.util.Log
import expo.modules.connector.interfaces.IEncryptionService
import expo.modules.connector.interfaces.ITcpTransport
import expo.modules.connector.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Socket

/**
 * SRP: Implementation of ITcpTransport using Unified Binary Framing.
 */
class TcpTransport(
    private val encryptionService: IEncryptionService,
    private val port: Int = 49152,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ITcpTransport {

    private val _connectionState = MutableStateFlow(ITcpTransport.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ITcpTransport.ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages

    private val manager = TcpServerManager(
        port = port,
        onMessage = ::handleIncomingFrame,
        scope = scope
    )

    init {
        manager.start()
        _connectionState.value = ITcpTransport.ConnectionState.READY
    }

    private suspend fun handleIncomingFrame(payload: ByteArray, address: String) {
        try {
            // In a future step, payload will be decrypted here
            val message = Message.fromBytes(payload) ?: return
            _incomingMessages.emit(message.withAddress(address))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process frame: ${e.message}")
        }
    }

    override suspend fun send(message: Message) {
        throw UnsupportedOperationException("General send not implemented in on-demand TCP model")
    }

    override suspend fun sendBlob(
        message: BlobMessage, 
        inputStream: java.io.InputStream, 
        host: String, 
        port: Int
    ) = withContext(Dispatchers.IO) {
        val socket = manager.connect(host, port) ?: throw java.io.IOException("Could not connect to $host:$port")
        
        try {
            // 1. Send Blob Announcement Frame
            manager.sendFrame(socket, message.toBytes())
            
            // 2. Send Data in Chunks
            val buffer = ByteArray(64 * 1024)
            var offset = 0L
            while (isActive) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                
                val chunk = ChunkMessage(offset, buffer.copyOf(read), message.id)
                manager.sendFrame(socket, chunk.toBytes())
                
                offset += read
            }
            
            Log.i(TAG, "Blob send finished: $offset bytes for ${message.name}")
        } catch (e: Exception) {
            Log.e(TAG, "sendBlob failed: ${e.message}")
            throw java.io.IOException("TCP Stream error: ${e.message}", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    override fun stop() {
        manager.stop()
        scope.cancel()
    }

    companion object {
        private const val TAG = "TcpTransport"
    }
}