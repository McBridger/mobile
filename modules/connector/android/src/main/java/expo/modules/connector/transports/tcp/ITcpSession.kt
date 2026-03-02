package expo.modules.connector.transports.tcp

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ITcpSession {
    val id: String
    val host: String
    val port: Int

    val state: StateFlow<SessionState>
    val incomingMessages: SharedFlow<ByteArray>

    suspend fun send(data: ByteArray)
    suspend fun forcePing()
    fun setPingEnabled(enabled: Boolean)
    fun disconnect()
}
