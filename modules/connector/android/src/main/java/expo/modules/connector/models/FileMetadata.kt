package expo.modules.connector.models

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

data class FileMetadata(
    val uri: Uri,
    val name: String,
    val size: Long
) {
    companion object {
        private const val TAG = "FileMetadata"

        fun fromUri(contentResolver: ContentResolver, uri: Uri): FileMetadata? {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        
                        val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                        val sizeBytes = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                        
                        return FileMetadata(
                            uri = uri,
                            name = name ?: uri.lastPathSegment ?: "unknown_file",
                            size = sizeBytes
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve metadata for $uri: ${e.message}")
            }

            // Fallback for file:// URIs or failed queries
            Log.d(TAG, "Using fallback metadata for $uri")
            val size = if (uri.scheme == "file") {
                runCatching { java.io.File(uri.path.orEmpty()).length() }.getOrDefault(0L)
            } else 0L

            return FileMetadata(
                uri = uri,
                name = uri.lastPathSegment ?: "file_${System.currentTimeMillis()}",
                size = size
            )
        }
    }
}
