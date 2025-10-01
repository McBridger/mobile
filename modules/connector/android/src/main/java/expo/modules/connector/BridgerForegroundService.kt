package expo.modules.connector

import expo.modules.connector.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.nio.charset.Charset

class BridgerForegroundService : Service(),
    BleSingleton.BleDataListener,
    BleSingleton.BleConnectionListener {

    private lateinit var notificationManager: NotificationManager
    private val HISTORY_FILE_NAME = "bridger_history.txt"

    companion object {
        private const val TAG = "BridgerForegroundService"
        private const val SERVICE_NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "BridgerForegroundServiceChannel"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val singleton = BleSingleton.getInstance(applicationContext)
        singleton.addDataListener(this)
        singleton.addConnectionListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = buildNotification(
            "Bridger Service",
            "Listening for BLE events."
        )
        startForeground(SERVICE_NOTIFICATION_ID, initialNotification)

        Log.d(TAG, "Service started. Registering listeners.")

        val singleton = BleSingleton.getInstance(applicationContext)
        if (singleton.isConnected) onDeviceConnected()

        return START_STICKY
    }

    override fun onDataReceived(data: Bundle) {
        val value = data.getString("value")
        if (value == null) {
            Log.w(TAG, "Received data is null, ignoring.")
            return
        }

        Log.d(TAG, "Data received in service: $value")

        // 1. Write to clipboard
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bridger Data", value)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "Copied to clipboard successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard", e)
        }

        // 2. Write to history file
        try {
            val file = File(applicationContext.filesDir, HISTORY_FILE_NAME)
            // Append timestamp and value, separated by a newline
            val lineToAppend = "${System.currentTimeMillis()},$value\n"
            file.appendText(lineToAppend, Charset.defaultCharset())
            Log.d(TAG, "Appended to history file successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to history file", e)
        }
    }

    override fun onDeviceConnected() {
        Log.d(TAG, "Listener notified: Device Connected.")
        updateNotification("Bridger Connected", "Receiving data from your device.")
    }

    override fun onDeviceDisconnected() {
        Log.w(TAG, "Listener notified: Device Disconnected. Stopping service.")
        updateNotification("Bridger Disconnected", "Connection lost. Service is stopping.")
        stopSelf()
    }

    override fun onDeviceFailedToConnect(deviceAddress: String?, deviceName: String?, reason: String?) {
        Log.e(TAG, "Listener notified: Failed to connect. Stopping service. Reason: $reason")
        val displayMessage = deviceName ?: deviceAddress
        updateNotification("Bridger Connection Failed", "Could not connect to $displayMessage. Service is stopping.")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service is being destroyed. Cleaning up.")
        val singleton = BleSingleton.getInstance(applicationContext)
        singleton.disconnect() 
        singleton.removeDataListener(this)
        singleton.removeConnectionListener(this)
        stopForeground(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bridger Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val notificationIcon = R.drawable.ic_notification

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(notificationIcon)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
    }
}