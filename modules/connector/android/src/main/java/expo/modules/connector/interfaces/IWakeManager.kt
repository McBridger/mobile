package expo.modules.connector.interfaces

interface IWakeManager {
    fun acquire(durationMs: Long = 10000L)
    fun release()
    suspend fun <T> withLock(durationMs: Long = 10000L, block: suspend () -> T): T
}
