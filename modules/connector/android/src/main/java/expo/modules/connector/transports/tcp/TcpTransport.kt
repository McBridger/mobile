package expo.modules.connector.transports.tcp

import android.util.Log
import expo.modules.connector.core.BlobStorageManager
import expo.modules.connector.interfaces.IEncryptionService
import expo.modules.connector.interfaces.ITcpTransport
import expo.modules.connector.models.BlobMessage
import expo.modules.connector.models.FileMetadata
import expo.modules.connector.models.Message
import okio.buffer
import okio.sink
import okio.source
import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SRP: Implementation of ITcpTransport using raw one-off streaming sockets.
 */
class TcpTransport(
    private val encryptionService: IEncryptionService,
    private val blobStorageManager: BlobStorageManager,
    private val port: Int = 49152,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ITcpTransport {

    private val _connectionState = MutableStateFlow(ITcpTransport.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ITcpTransport.ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages

    private val manager = TcpServerManager(
        port = port,
        onIncomingStream = ::handleIncomingStream,
        scope = scope
    )

    init {
        manager.start()
        _connectionState.value = ITcpTransport.ConnectionState.READY
    }

    private suspend fun handleIncomingStream(socket: Socket) {
        val source = socket.source().buffer()
        try {
            // 1. Read Header (One-off for the whole stream)
            // Header format: [36 bytes UUID string] + [8 bytes Long Size]
            val blobId = source.readUtf8(36).trim()
            val totalSize = source.readLong()
            
            Log.i(TAG, "Starting binary stream for Blob: $blobId (Size: $totalSize)")

            // 2. Stream Data to Manager
            val buffer = ByteArray(64 * 1024)
            var bytesRead = 0L
            
            while (bytesRead < totalSize && !source.exhausted()) {
                val toRead = minOf(buffer.size.toLong(), totalSize - bytesRead).toInt()
                val read = source.read(buffer, 0, toRead)
                if (read == -1) break
                
                blobStorageManager.writeChunk(blobId, bytesRead, buffer.copyOf(read))
                bytesRead += read
            }
            
            Log.i(TAG, "Stream finished. Received $bytesRead of $totalSize bytes.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Incoming stream error: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
    override suspend fun send(message: Message): Boolean {
        Log.w(TAG, "send: Legacy send called, ignoring in one-off TCP model")
        return false
    }

    /**
     * Sends large content via a dedicated TCP connection.
     */
    override suspend fun sendBlob(
        message: BlobMessage, 
        inputStream: java.io.InputStream, 
        host: String, 
        port: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val socket = manager.connect(host, port) ?: return@withContext false
        
        return@withContext try {
            val sink = socket.sink().buffer()
            
            // 1. Send Header: [36 bytes UUID] + [8 bytes Long Size]
            val paddedId = message.id.padEnd(36)
            sink.writeUtf8(paddedId)
            sink.writeLong(message.size)
            sink.flush()

            Log.i(TAG, "Header sent for ${message.name} to $host:$port, starting payload...")

            // 2. Pump bytes via Okio
            val source = inputStream.source().buffer()
            val bytesSent = sink.writeAll(source)
            sink.flush()

            Log.i(TAG, "Payload sent: $bytesSent bytes for ${message.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendBlob failed: ${e.message}")
            false
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