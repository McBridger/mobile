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
        val clip = getClipboardManager().primaryClip ?: return

        if (clip.itemCount == 0) {
            Log.w(TAG, "Clipboard is empty or contains unsupported data.")
            showToast("Clipboard is empty.")
            return
        }

        val item = clip.getItemAt(0)

        // 1. Priority: File URI
        if (item.uri != null) {
            Log.d(TAG, "File URI detected in clipboard: ${item.uri}")
            val metadata = FileMetadata.fromUri(contentResolver, item.uri)
            if (metadata != null) {
                broker.fileUpdate(metadata)
                showToast("Sharing file: ${metadata.name}")
                return
            }
        }

        // 2. Fallback: Text
        val text = item.text?.toString()
        if (!text.isNullOrEmpty()) {
            broker.clipboardUpdate(text)
            showToast("Clipboard text sent.")
            return
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getClipboardManager(): ClipboardManager {
        return getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
}