package expo.modules.connector.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import expo.modules.connector.models.BlobMessage
import expo.modules.connector.models.BlobType
import expo.modules.connector.models.ChunkMessage
import expo.modules.connector.services.NotificationService
import okio.FileHandle
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * SRP: Manages storage for large payloads (Blobs).
 * Handles temporary assembly in cache, auto-saving to Downloads/McBridger, and Clipboard.
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
        if (activeBlobs.containsKey(msg.id)) {
            Log.d(TAG, "Blob ${msg.id} is already being prepared, skipping re-init.")
            return
        }

        val tempFile = File(blobsDir, "blob_${msg.id}")
        if (tempFile.exists()) tempFile.delete()

        try {
            val handle = FileSystem.SYSTEM.openReadWrite(tempFile.absolutePath.toPath())
            activeBlobs[msg.id] = ActiveBlob(msg, tempFile, handle)
            Log.d(TAG, "Prepared storage for blob: ${msg.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file handle: ${e.message}")
        }
    }

    fun writeChunk(msg: ChunkMessage) {
        val blob = activeBlobs[msg.id] ?: return

        try {
            // Write directly using the open handle - MUCH faster
            blob.fileHandle.write(msg.offset, msg.data, 0, msg.data.size)
            
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

        val durationMs = now - blob.startTime
        val speedBps = if (durationMs > 0) (currentSize * 1000) / durationMs else 0L
        
        val progress = if (blob.message.size > 0) ((currentSize * 100) / blob.message.size).toInt() else 0
        
        notificationService.showProgress(
            blobId = blob.message.id,
            name = blob.message.name,
            progress = progress,
            currentSize = currentSize,
            totalSize = blob.message.size,
            speedBps = speedBps
        )
        blob.lastProgressUpdate = now
    }

    fun finalizeBlob(id: String) {
        val blob = activeBlobs.remove(id) ?: return
        Log.i(TAG, "Finalizing blob: ${blob.message.name} (Type: ${blob.message.blobType})")

        // CRITICAL: Close the handle before renaming the file!
        try { blob.fileHandle.close() } catch (_: Exception) {}

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
            // 1. Save to internal cache for Clipboard access (via FileProvider)
            val finalName = "${blob.message.id}_${blob.message.name}"
            val cachedFile = File(blobsDir, finalName)
            if (cachedFile.exists()) cachedFile.delete()
            blob.tempFile.renameTo(cachedFile)

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, cachedFile)
            
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newUri(context.contentResolver, "McBridger Media", contentUri)
            clipboard.setPrimaryClip(clipData)
            
            // 2. Auto-save to public Downloads/McBridger folder
            saveToPublicDownloads(blob.message.name, cachedFile)

            notificationService.showFinished(blob.message.id, blob.message.name, true)
            Log.i(TAG, "Media blob finalized, saved to Downloads and copied to clipboard")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize URI: ${e.message}")
            notificationService.showFinished(blob.message.id, blob.message.name, false)
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
                Log.d(TAG, "Successfully mirrored file to public Downloads/McBridger")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to public Downloads: ${e.message}")
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

private data class ActiveBlob(
    val message: BlobMessage,
    val tempFile: File,
    val fileHandle: FileHandle,
    var lastProgressUpdate: Long = 0,
    val startTime: Long = System.currentTimeMillis()
)
