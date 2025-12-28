package expo.modules.connector.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import expo.modules.connector.core.Broker
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
        val dataToSend = getClipboardText()
        if (dataToSend == null) {
            Log.w(TAG, "Clipboard is empty or contains non-text data.")
            showToast("Clipboard is empty.")
            return
        }

        broker.clipboardUpdate(dataToSend)
        Log.i(TAG, "Clipboard data sent successfully: $dataToSend")
        showToast("Clipboard data sent.")
    }

    private fun getClipboardText(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}