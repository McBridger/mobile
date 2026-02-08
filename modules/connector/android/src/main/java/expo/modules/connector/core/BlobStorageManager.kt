package expo.modules.connector.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import expo.modules.connector.models.BlobMessage
import expo.modules.connector.models.BlobType
import expo.modules.connector.models.ChunkMessage
import expo.modules.connector.services.NotificationService
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * SRP: Manages storage for large payloads (Blobs).
 * Handles temporary assembly in cache and finalization to URI in Clipboard.
 */
class BlobStorageManager(
    private val context: Context,
    private val notificationService: NotificationService
) {
    private val TAG = "BlobStorageManager"
    private val activeBlobs = ConcurrentHashMap<String, ActiveBlob>()
    
    // Directory within cache for sharing files via FileProvider
    private val blobsDir = File(context.cacheDir, "mcbridger_blobs").apply {
        if (!exists()) mkdirs()
    }

    fun prepare(msg: BlobMessage) {
        val tempFile = File(blobsDir, "blob_${msg.id}")
        if (tempFile.exists()) tempFile.delete()

        activeBlobs[msg.id] = ActiveBlob(msg, tempFile)
        Log.d(TAG, "Prepared storage for blob: ${msg.name}")
    }

    fun writeChunk(msg: ChunkMessage) {
        val blob = activeBlobs[msg.id] ?: return

        try {
            // Using Okio for efficient file access
            FileSystem.SYSTEM.openReadWrite(blob.tempFile.absolutePath.toPath()).use { handle ->
                handle.write(msg.offset, msg.data, 0, msg.data.size)
            }

            updateProgress(blob, msg.offset + msg.data.size)

            if (msg.offset + msg.data.size >= blob.message.size) {
                finalizeBlob(msg.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write chunk for ${msg.id}: ${e.message}")
        }
    }

    private fun updateProgress(blob: ActiveBlob, currentSize: Long) {
        val now = System.currentTimeMillis()
        if (now - blob.lastProgressUpdate < 500) return

        val progress = if (blob.message.size > 0) ((currentSize * 100) / blob.message.size).toInt() else 0
        notificationService.showProgress(blob.message.id, blob.message.name, progress)
        blob.lastProgressUpdate = now
    }

    fun finalizeBlob(id: String) {
        val blob = activeBlobs.remove(id) ?: return
        Log.i(TAG, "Finalizing blob: ${blob.message.name} (Type: ${blob.message.blobType})")

        when (blob.message.blobType) {
            BlobType.TEXT -> finalizeToText(blob)
            BlobType.FILE, BlobType.IMAGE -> finalizeToUri(blob)
        }
    }

    private fun finalizeToText(blob: ActiveBlob) {
        try {
            val text = FileSystem.SYSTEM.read(blob.tempFile.absolutePath.toPath()) { readUtf8() }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            val clipData = ClipData.newPlainText("McBridger Text", text)
            clipboard.setPrimaryClip(clipData)
            
            notificationService.cancel(blob.message.id)
            blob.tempFile.delete()
            Log.i(TAG, "Text blob finalized to clipboard")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize text: ${e.message}")
        }
    }

    private fun finalizeToUri(blob: ActiveBlob) {
        try {
            // Rename blob file to original name for better UX in target apps
            val finalFile = File(blobsDir, blob.message.name)
            if (finalFile.exists()) finalFile.delete()
            blob.tempFile.renameTo(finalFile)

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, finalFile)
            
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            val clipData = ClipData.newRawUri("McBridger File", contentUri)
            // Note: Intent is needed to carry URI permissions correctly in some Android versions
            clipboard.setPrimaryClip(clipData)
            
            notificationService.showFinished(blob.message.id, blob.message.name, true)
            Log.i(TAG, "File blob finalized to URI: $contentUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize URI: ${e.message}")
            notificationService.showFinished(blob.message.id, blob.message.name, false)
        }
    }

    fun cleanup() {
        blobsDir.listFiles()?.forEach { it.delete() }
    }
}

private data class ActiveBlob(
    val message: BlobMessage,
    val tempFile: File,
    var lastProgressUpdate: Long = 0
)
