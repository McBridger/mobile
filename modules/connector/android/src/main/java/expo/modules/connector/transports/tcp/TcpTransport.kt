package expo.modules.connector.transports.tcp

import android.util.Log
import expo.modules.connector.crypto.decryptMessage
import expo.modules.connector.crypto.encryptMessage
import expo.modules.connector.interfaces.IEncryptionService
import expo.modules.connector.interfaces.ITcpTransport
import expo.modules.connector.models.FileMetadata
import expo.modules.connector.models.Message
import expo.modules.connector.utils.NetworkUtils
import io.ktor.websocket.*
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SRP: Implementation of ITcpTransport.
 * Orchestrates file sharing and continuous messaging using TcpServerManager and TcpFileProvider.
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

    private var activeSession: DefaultWebSocketServerSession? = null

    private val manager = TcpServerManager(
        port = port,
        fileProvider = fileProvider,
        onWebSocketMessage = ::handleStreamData,
        onSessionEvent = ::handleSessionEvent
    )

    init {
        manager.start()
        _connectionState.value = ITcpTransport.ConnectionState.READY

        // Background cleanup for expired files
        scope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L) // Every 5 minutes
                fileProvider.cleanup()
            }
        }
    }

    private fun handleSessionEvent(session: DefaultWebSocketServerSession?) {
        activeSession = session
        _connectionState.value = if (session != null) {
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
        val fileId = fileProvider.registerFile(metadata)
        val ip = NetworkUtils.getLocalIpAddress() ?: "127.0.0.1"
        return "http://$ip:$port/files/$fileId/${metadata.name}"
    }

    override suspend fun send(message: Message): Boolean {
        val session = activeSession ?: run {
            Log.w(TAG, "send: No active WebSocket session")
            return false
        }
        return try {
            val encrypted = encryptionService.encryptMessage(message) ?: run {
                Log.e(TAG, "send: Encryption failed")
                return false
            }
            Log.d(TAG, "send: Sending encrypted message (${encrypted.size} bytes)")
            session.send(Frame.Binary(true, encrypted))
            true
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
