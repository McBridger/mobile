package expo.modules.connector.core

import android.content.Context
import android.net.Uri
import android.util.Log
import expo.modules.connector.interfaces.IFileStreamProvider
import java.io.File
import java.io.InputStream

class AndroidFileStreamProvider(private val context: Context) : IFileStreamProvider {
    private val TAG = "FileStreamProvider"

    override fun openStream(uriString: String): InputStream? {
        return try {
            val uri = Uri.parse(uriString)
            
            // 1. Handle file:// and raw paths directly (critical for E2E tests)
            if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path ?: uriString.removePrefix("file://")
                Log.d(TAG, "Opening direct file stream for path: $path")
                return File(path).inputStream()
            }

            // 2. Default to ContentResolver for content:// URIs
            Log.d(TAG, "Opening ContentResolver stream for URI: $uri")
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open stream for $uriString: ${e.message}")
            null
        }
    }
}
