package expo.modules.connector.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import expo.modules.connector.receivers.DownloadReceiver

class FileTransferService(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "mcbridger_files"
        const val NOTIFICATION_ID = 2077
        const val ACTION_ACCEPT = "expo.modules.connector.ACCEPT_FILE"
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "File Transfers"
            val descriptionText = "Incoming file offers from your Mac"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showOffer(filename: String, url: String) {
        // 1. Intent for the "Download" button (BroadcastReceiver)
        val downloadIntent = Intent(context, DownloadReceiver::class.java).apply {
            action = ACTION_ACCEPT
            putExtra("url", url)
            putExtra("filename", filename)
        }
        
        val downloadPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context, 
            url.hashCode(), 
            downloadIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Intent for the main notification tap (Open App)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent: PendingIntent? = launchIntent?.let {
            PendingIntent.getActivity(
                context, 
                0, 
                it, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("New file available")
            .setContentText("Tap to open app, or use button to receive")
            .setStyle(NotificationCompat.BigTextStyle().bigText("File: $filename\n\nCloud sync ready. Would you like to download this file now?"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_save, "Download", downloadPendingIntent)
            .setTimeoutAfter(300000) // 5 minutes

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun dismissOffer() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
