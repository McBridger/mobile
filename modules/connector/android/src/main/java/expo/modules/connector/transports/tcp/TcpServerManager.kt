package expo.modules.connector.transports.tcp

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

/**
 * SRP: Manages raw TCP sockets and the envelope protocol (length prefix + payload).
 */
class TcpServerManager(
    private val port: Int,
    private val onMessage: suspend (ByteArray, String) -> Unit,
    private val onSessionEvent: (Socket?) -> Unit,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val activeSockets = ConcurrentHashMap<String, Socket>()

    fun start() {
        Log.i(TAG, "Starting TCP server on port $port")
        job = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val address = socket.inetAddress.hostAddress ?: "unknown"
        Log.d(TAG, "New connection from $address")
        
        // For simplicity, we track the latest connection as the active one
        onSessionEvent(socket)
        activeSockets[address] = socket

        try {
            val input = DataInputStream(socket.getInputStream())
            while (coroutineContext.isActive) {
                val length = input.readInt()
                if (length <= 0 || length > 100 * 1024 * 1024) {
                    Log.w(TAG, "Invalid message length: $length")
                    break
                }
                
                val body = ByteArray(length)
                input.readFully(body)
                onMessage(body, address)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client disconnected: $address (${e.message})")
        } finally {
            activeSockets.remove(address)
            onSessionEvent(null)
            try { socket.close() } catch (e: Exception) {}
        }
    }

    suspend fun send(socket: Socket, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = DataOutputStream(socket.getOutputStream())
            output.writeInt(data.size)
            output.write(data)
            output.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
            false
        }
    }

    fun stop() {
        job?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        activeSockets.values.forEach { try { it.close() } catch (e: Exception) {} }
        activeSockets.clear()
        serverSocket = null
    }

    companion object {
        private const val TAG = "TcpServerManager"
    }
}

