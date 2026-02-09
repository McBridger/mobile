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
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        
    }

    fun showProgress(
        blobId: String, 
        name: String, 
        progress: Int,
        currentSize: Long,
        totalSize: Long,
        speedBps: Long
    ) {
        val notificationId = getNotificationId(blobId)
        
        val speedStr = formatSize(speedBps) + "/s"
        val remainingBytes = totalSize - currentSize
        val etaSec = if (speedBps > 0) remainingBytes / speedBps else -1L
        val etaStr = if (etaSec >= 0) " - ${formatTime(etaSec)} left" else ""

        val detailsText = "${formatSize(currentSize)} / ${formatSize(totalSize)} ($speedStr)$etaStr"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Receiving ($progress%): $name")
            .setContentText(detailsText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailsText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Higher than LOW to keep it visible
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)

        try { nm.notify(notificationId, builder.build()) } 
        catch (_: SecurityException) {}
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatTime(seconds: Long): String {
        return if (seconds >= 3600) {
            String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60)
        } else if (seconds >= 60) {
            String.format("%dm %ds", seconds / 60, seconds % 60)
        } else {
            String.format("%ds", seconds)
        }
    }

    fun showFinished(blobId: String, name: String, success: Boolean) {
        val notificationId = getNotificationId(blobId)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
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
