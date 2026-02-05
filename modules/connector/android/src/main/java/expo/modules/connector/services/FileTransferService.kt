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

    fun showOffer(filename: String, fileId: String) {
        // Intent for the main notification tap (Open App)
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
            .setContentTitle("Incoming file: $filename")
            .setContentText("Transfer in progress...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true) // Keep it while transferring

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun showFinished(filename: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("File received")
            .setContentText(filename)
            .setSubText("Saved to Downloads/McBridger")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: SecurityException) {}
    }

    fun dismissOffer() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
