package expo.modules.connector.services

import android.R.drawable.ic_menu_upload
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // Ensure unique ID per package if multiple apps use this lib (unlikely but safe)
        SERVICE_NOTIFICATION_ID += applicationContext.packageName.hashCode()
        CHANNEL_ID += applicationContext.packageName
        
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 1. Initialize Broker (Idempotent)
        Broker.init(applicationContext)

        // 3. Listen to state changes for Notification updates
        scope.launch {
            Broker.state.collect { state ->
                updateNotificationForState(state)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.HONEYCOMB)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForeground(SERVICE_NOTIFICATION_ID, buildNotification("Bridger Service", "Service Started"))
        Broker.registerBle(
            BleTransport(
                applicationContext,
                intent.extras!!.getString("SERVICE_UUID") ?: "",
                intent.extras!!.getString("CHARACTERISTIC_UUID") ?: ""
            )
        )
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        // We do NOT disconnect Broker here, because UI might still be alive.
        // Service death != App death.
    }

    private fun updateNotificationForState(state: Broker.State) {
        val (title, text) = when (state) {
            Broker.State.CONNECTED -> "Bridger Connected" to "Ready to sync."
            Broker.State.CONNECTING -> "Bridger Connecting..." to "Looking for bridge."
            else -> "Bridger Active" to "Waiting for connection."
        }
        notificationManager.notify(SERVICE_NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bridger Background Service",
                NotificationManager.IMPORTANCE_LOW // Low importance to not annoy user
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        // TODO: Add PendingIntent to open App on click
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(ic_menu_upload) // Replace with your icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}