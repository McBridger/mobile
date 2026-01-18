package expo.modules.connector.receivers

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import expo.modules.connector.services.FileTransferService
import androidx.core.net.toUri
import android.app.NotificationManager

class DownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FileTransferService.ACTION_ACCEPT) {
            val url = intent.getStringExtra("url")
            val filename = intent.getStringExtra("filename") ?: "unknown_file"

            if (url != null) {
                startDownload(context, url, filename)
            } else {
                Log.e("DownloadReceiver", "URL missing in download intent")
            }
            
            // Explicitly dismiss notification since action clicks don't trigger autoCancel
            this.getNotificationManager(context).cancel(FileTransferService.NOTIFICATION_ID)
        }
    }



    private fun startDownload(context: Context, url: String, filename: String) {
        try {
            val request = DownloadManager.Request(url.toUri())
                .setTitle(filename)
                .setDescription("Downloading via McBridger")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "McBridger/$filename")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            getDownloadManager(context).enqueue(request)
            
            Toast.makeText(context, "Started downloading $filename...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("DownloadReceiver", "Failed to start download: ${e.message}")
            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun getDownloadManager(context: Context): DownloadManager {
        return context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
}
