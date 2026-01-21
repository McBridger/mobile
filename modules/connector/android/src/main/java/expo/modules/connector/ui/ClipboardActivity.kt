package expo.modules.connector.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import expo.modules.connector.core.Broker
import expo.modules.connector.models.FileMetadata
import org.koin.android.ext.android.inject
import java.util.Optional

class ClipboardActivity : Activity() {
    private val TAG = "ClipboardActivity"
    private var isClipboardProcessed = false
    private val broker: Broker by inject()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (isClipboardProcessed) return

        isClipboardProcessed = true
        Log.d(TAG, "Activity has focus. Reading clipboard...")
        sendClipboardData()
        Log.d(TAG, "Processing complete. Finishing activity.")
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity created.")
    }

    private fun sendClipboardData() {
        // 1. Priority: URI from Intent (for sharing/testing)
        intent.data?.let { uri ->
            Log.d(TAG, "URI detected in Intent data: $uri")
            processFileUri(uri)
            return
        }

        val clip = getClipboardManager().primaryClip
        if (clip == null || clip.itemCount == 0) {
            Log.w(TAG, "Clipboard is empty or null.")
            showToast("Nothing to share.")
            return
        }

        val item = clip.getItemAt(0)

        // 2. Secondary Priority: File URI from Clipboard
        if (item.uri != null) {
            Log.d(TAG, "File URI detected in clipboard: ${item.uri}")
            processFileUri(item.uri)
            return
        }

        // 3. Fallback: Text
        val text = item.text?.toString()
        if (!text.isNullOrEmpty()) {
            broker.clipboardUpdate(text)
            showToast("Clipboard text sent.")
            return
        }
    }

    private fun processFileUri(uri: android.net.Uri) {
        // Critical for real combat: try to persist access to the content URI
        if (uri.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d(TAG, "Persistable permission granted for: $uri")
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistable permission (normal for temporary shares): ${e.message}")
            }
        }

        val metadata = FileMetadata.fromUri(contentResolver, uri)
        if (metadata != null) {
            broker.fileUpdate(metadata)
            showToast("Sharing file: ${metadata.name}")
        } else {
            showToast("Failed to resolve file.")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getClipboardManager(): ClipboardManager {
        return getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
}