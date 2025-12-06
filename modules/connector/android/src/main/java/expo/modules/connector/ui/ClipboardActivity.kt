package expo.modules.connector.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import expo.modules.connector.core.Broker
import java.util.Optional

class ClipboardActivity : Activity() {
    private val TAG = "ClipboardActivity"
    private var isClipboardProcessed = false

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
        Broker.init(applicationContext) 
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) return

        Log.d(TAG, "Activity paused. Finishing now.")
        finish()
    }

    private fun sendClipboardData() {
        val dataToSend = getClipboardText()
        if (dataToSend == null) {
            Log.w(TAG, "Clipboard is empty or contains non-text data.")
            showToast("Clipboard is empty.")
            return
        }

        Broker.clipboardUpdate(dataToSend)
        Log.i(TAG, "Clipboard data sent successfully: $dataToSend")
        showToast("Clipboard data sent.")
    }

    private fun getClipboardText(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        return Optional.ofNullable(clipboard)
            .filter { it.hasPrimaryClip() }
            .map { it.primaryClip }
            .filter { it?.itemCount ?: 0 > 0 }
            .map { it?.getItemAt(0)?.text }
            .map { it?.toString() }
            .filter { !it.isNullOrEmpty() }
            .orElse(null)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}