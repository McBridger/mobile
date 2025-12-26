package expo.modules.connector.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import expo.modules.connector.R
import expo.modules.connector.core.Broker
import expo.modules.connector.transports.ble.BleTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "BridgerService"
        private var SERVICE_NOTIFICATION_ID = 12345
        private var CHANNEL_ID = "BridgerForegroundServiceChannel"
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind: Service binding.")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service created.")
        
        // Ensure unique ID per package if multiple apps use this lib (unlikely but safe)
        SERVICE_NOTIFICATION_ID += applicationContext.packageName.hashCode()
        CHANNEL_ID += applicationContext.packageName
        
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 1. Initialize Broker (Idempotent)
        Log.d(TAG, "onCreate: Initializing Broker.")
        Broker.init(applicationContext)

        // 3. Listen to state changes for Notification updates
        scope.launch {
            Log.d(TAG, "onCreate: Starting to collect Broker state changes for notification updates.")
            Broker.state.collect { state ->
                Log.d(TAG, "onCreate: Broker state changed to $state, updating notification.")
                updateNotificationForState(state)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Service received start command.")

        Log.d(TAG, "onStartCommand: Starting foreground with notification ID: $SERVICE_NOTIFICATION_ID")
        startForeground(SERVICE_NOTIFICATION_ID, buildNotification("Bridger Active", "Initializing..."))

        Log.i(TAG, "onStartCommand: Service is running in foreground.")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service destroyed, cancelling scope.")
        scope.cancel()
        // We do NOT disconnect Broker here, because UI might still be alive.
        // Service death != App death.
    }

    private fun updateNotificationForState(state: Broker.State) {
        Log.d(TAG, "updateNotificationForState: Updating notification for state: $state")
        val (title, text) = when (state) {
            Broker.State.CONNECTED -> "Bridger Connected" to "Ready to sync."
            Broker.State.CONNECTING -> "Bridger Connecting..." to "Looking for bridge."
            Broker.State.IDLE -> "Bridger Active" to "Waiting for setup."
            Broker.State.DISCONNECTED -> "Bridger Disconnected" to "Connection lost."
            Broker.State.ERROR -> "Bridger Error" to "Check Bluetooth or permissions."
            Broker.State.ENCRYPTING -> "Bridger Setup" to "Generating secure keys..."
            Broker.State.KEYS_READY, 
            Broker.State.TRANSPORT_INITIALIZING -> "Bridger Setup" to "Initializing Bluetooth..."
            Broker.State.READY -> "Bridger Ready" to "Waiting for Mac to appear."
            Broker.State.DISCOVERING -> "Bridger Scanning" to "Searching for your Mac..."
        }
        Log.d(TAG, "updateNotificationForState: Notification title: \"$title\", text: \"$text\"")
        notificationManager.notify(SERVICE_NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "createNotificationChannel: Creating notification channel for ID: $CHANNEL_ID")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bridger Background Service",
                NotificationManager.IMPORTANCE_LOW // Low importance to not annoy user
            )
            notificationManager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "createNotificationChannel: Notification channel created for Android O+.")
        } else {
            Log.d(TAG, "createNotificationChannel: Not creating channel, Android version < O.")
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        Log.d(TAG, "buildNotification: Building notification with title: \"$title\", text: \"$text\"")
        // TODO: Add PendingIntent to open App on click
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.notification_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}