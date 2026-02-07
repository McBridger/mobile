package expo.modules.connector.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log

class NotificationService(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    init { createChannel() }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;


        val name = "McBridger Transfers"
        val descriptionText = "Notifications for incoming files and large data"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        
    }

    fun showProgress(blobId: String, name: String, progress: Int) {
        val notificationId = getNotificationId(blobId)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Receiving: $name")
            .setContentText("$progress%")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)

        try { nm.notify(notificationId, builder.build()) } 
        catch (_: SecurityException) {}
    }

    fun showFinished(blobId: String, name: String, success: Boolean) {
        val notificationId = getNotificationId(blobId)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOngoing(false)

        if (success) {
            builder
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Received: $name")
            .setContentText("Saved to Downloads/McBridger")
        } else {
            builder
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Failed: $name")
            .setContentText("Transfer interrupted")
        }

        try { nm.notify(notificationId, builder.build()) } 
        catch (_: SecurityException) {}
    }
    
    fun cancel(blobId: String) {
        nm.cancel(getNotificationId(blobId))
    }

    private fun getNotificationId(blobId: String): Int {
        return 3000 + (blobId.hashCode() and 0xFFFF)
    }

    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "mcbridger_transfers"
    }
}
