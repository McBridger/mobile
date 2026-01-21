package expo.modules.connector.transports.tcp

import expo.modules.connector.interfaces.IFileStreamProvider
import expo.modules.connector.models.FileMetadata
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * SRP: Manages registration and retrieval of shared files.
 */
class TcpFileProvider(private val streamProvider: IFileStreamProvider) {
    private data class RegisteredFile(
        val metadata: FileMetadata,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val activeFiles = ConcurrentHashMap<String, RegisteredFile>()
    private val FILE_TTL_MS = 10 * 60 * 1000L // 10 minutes

    fun registerFile(metadata: FileMetadata): String {
        val id = java.util.UUID.randomUUID().toString()
        activeFiles[id] = RegisteredFile(metadata)
        return id
    }

    fun getFile(id: String): FileMetadata? {
        val entry = activeFiles[id] ?: return null
        if (System.currentTimeMillis() - entry.createdAt > FILE_TTL_MS) {
            activeFiles.remove(id)
            return null
        }
        return entry.metadata
    }

    fun openStream(metadata: FileMetadata) = streamProvider.openStream(metadata.uri.toString())

    fun cleanup() {
        val now = System.currentTimeMillis()
        activeFiles.entries.removeIf { now - it.value.createdAt > FILE_TTL_MS }
    }
}
