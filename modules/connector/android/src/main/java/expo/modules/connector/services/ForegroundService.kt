package expo.modules.connector.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import expo.modules.connector.R
import expo.modules.connector.core.Broker
import expo.modules.connector.interfaces.IBleTransport
import expo.modules.connector.interfaces.ITcpTransport
import expo.modules.connector.models.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import expo.modules.connector.di.initKoin

class ForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var notificationManager: NotificationManager
    private val broker: Broker by inject()

    companion object {
        private const val TAG = "BridgerService"
        private var SERVICE_NOTIFICATION_ID = 12345
        private var CHANNEL_ID = "BridgerForegroundServiceChannel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service created. Initializing Koin if needed.")
        initKoin(this)

        SERVICE_NOTIFICATION_ID += applicationContext.packageName.hashCode()
        CHANNEL_ID += applicationContext.packageName

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        scope.launch {
            Log.d(TAG, "onCreate: Starting to collect state changes for notification updates.")
            broker.state.collect { state ->
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

    private fun updateNotificationForState(state: BrokerState) {
        Log.d(TAG, "updateNotificationForState: Updating notification for state: $state")
        val title: String
        val text: String

        when {
            state.encryption.current == EncryptionState.ERROR -> {
                title = "Bridger Error (Security)"
                text = state.encryption.error ?: "Check mnemonic or salt."
            }
            state.ble.current == IBleTransport.State.ERROR -> {
                title = "Bridger Error (Bluetooth)"
                text = state.ble.error ?: "Check Bluetooth permissions."
            }
            state.tcp.current == ITcpTransport.State.TRANSFERRING -> {
                title = "Bridger Connected (Turbo)"
                text = "Transferring data..."
            }
            state.ble.current == IBleTransport.State.CONNECTED -> {
                title = "Bridger Connected"
                text = "Secure link active."
            }
            state.ble.current == IBleTransport.State.SCANNING -> {
                title = "Bridger Scanning"
                text = "Searching for nearby devices..."
            }
            state.encryption.current == EncryptionState.ENCRYPTING -> {
                title = "Bridger Setup"
                text = "Securing transport channel..."
            }
            else -> {
                title = "Bridger Idle"
                text = "Waiting for setup."
            }
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
                NotificationManager.IMPORTANCE_LOW
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
