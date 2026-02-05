package expo.modules.connector.transports.tcp

import android.util.Log
import expo.modules.connector.crypto.decryptMessage
import expo.modules.connector.crypto.encryptMessage
import expo.modules.connector.interfaces.IEncryptionService
import expo.modules.connector.interfaces.ITcpTransport
import expo.modules.connector.models.FileMetadata
import expo.modules.connector.models.Message
import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SRP: Implementation of ITcpTransport using raw sockets and envelope protocol.
 */
class TcpTransport(
    private val encryptionService: IEncryptionService,
    private val fileProvider: TcpFileProvider,
    private val port: Int = 49152,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ITcpTransport {

    private val _connectionState = MutableStateFlow(ITcpTransport.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ITcpTransport.ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Message>()
    override val incomingMessages = _incomingMessages

    private var activeSocket: Socket? = null

    private val manager = TcpServerManager(
        port = port,
        onMessage = ::handleStreamData,
        onSessionEvent = ::handleSessionEvent,
        scope = scope
    )

    init {
        manager.start()
        _connectionState.value = ITcpTransport.ConnectionState.READY
    }

    private fun handleSessionEvent(socket: Socket?) {
        activeSocket = socket
        _connectionState.value = if (socket != null) {
            ITcpTransport.ConnectionState.CONNECTED
        } else {
            ITcpTransport.ConnectionState.READY
        }
    }

    private suspend fun handleStreamData(data: ByteArray, address: String) {
        try {
            val message = encryptionService.decryptMessage(data, address) ?: return
            _incomingMessages.emit(message)
        } catch (e: Exception) {
            Log.e(TAG, "Process failed: ${e.message}")
        }
    }

    override fun registerFile(metadata: FileMetadata): String {
        // Now returns just an ID or internal URI since we don't use HTTP
        val fileId = fileProvider.registerFile(metadata)
        return "bridge://$fileId"
    }

    override suspend fun send(message: Message): Boolean {
        val socket = activeSocket ?: run {
            Log.w(TAG, "send: No active socket")
            return false
        }
        return try {
            val encrypted = encryptionService.encryptMessage(message) ?: run {
                Log.e(TAG, "send: Encryption failed")
                return false
            }
            manager.send(socket, encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "send: Failed: ${e.message}")
            false
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

