package expo.modules.connector.transports.tcp

import expo.modules.connector.interfaces.IFileStreamProvider
import expo.modules.connector.models.FileMetadata
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * SRP: Manages registration and retrieval of shared files.
 */
class TcpFileProvider(private val streamProvider: IFileStreamProvider) {
    private val activeFiles = ConcurrentHashMap<String, FileMetadata>()

    fun registerFile(metadata: FileMetadata): String {
        val id = UUID.randomUUID().toString()
        activeFiles[id] = metadata
        return id
    }

    fun getFile(id: String): FileMetadata? = activeFiles[id]

    fun openStream(metadata: FileMetadata) = streamProvider.openStream(metadata.uri.toString())
}
