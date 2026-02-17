package expo.modules.connector.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import expo.modules.connector.models.BlobMessage
import expo.modules.connector.models.BridgerType
import okio.FileHandle
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File

/**
 * Stateless storage toolkit. 
 * Creates sessions that must be managed by the caller (Broker).
 */
class BlobStorageManager(
    private val context: Context
) {
    private val TAG = "BlobStorageManager"

    private val blobsDir = File(context.cacheDir, "mcbridger_blobs").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Represents an active file assembly session.
     * Must be closed by the owner!
     */
    inner class BlobSession(
        val id: String,
        val name: String,
        val type: BridgerType,
        val totalSize: Long,
        private val tempFile: File,
        private val fileHandle: FileHandle
    ) {
        fun write(offset: Long, data: ByteArray) {
            fileHandle.write(offset, data, 0, data.size)
        }

        fun finalize(): String? {
            try { fileHandle.close() } catch (_: Exception) {}
            
            return try {
                val finalName = "${id}_${name}"
                val cachedFile = File(blobsDir, finalName)
                if (cachedFile.exists()) cachedFile.delete()
                tempFile.renameTo(cachedFile)

                saveToPublicDownloads(name, cachedFile)
                
                val authority = "${context.packageName}.fileprovider"
                FileProvider.getUriForFile(context, authority, cachedFile).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Finalization failed for $name: ${e.message}")
                null
            }
        }

        fun close() {
            try { fileHandle.close() } catch (_: Exception) {}
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun openSession(msg: BlobMessage): BlobSession? {
        val tempFile = File(context.cacheDir, "${msg.id}.tmp")
        if (tempFile.exists()) tempFile.delete()

        return try {
            val handle = FileSystem.SYSTEM.openReadWrite(tempFile.absolutePath.toPath())
            BlobSession(msg.id, msg.name, msg.dataType, msg.size, tempFile, handle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open session: ${e.message}")
            null
        }
    }

    private fun saveToPublicDownloads(fileName: String, sourceFile: File) {
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, resolver.getType(getUriForFile(sourceFile)) ?: "*/*")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/McBridger")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mirror to Downloads failed: ${e.message}")
        }
    }

    fun getUriForFile(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun cleanup() {
        blobsDir.listFiles()?.forEach { it.delete() }
    }
}
