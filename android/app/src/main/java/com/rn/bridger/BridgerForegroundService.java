package com.rn.bridger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class BridgerForegroundService extends Service implements
    BleSingleton.BleDataListener,
    BleSingleton.BleConnectionListener {

    private static final String TAG = "BridgerForegroundService";
    private static final int SERVICE_NOTIFICATION_ID = 12345;
    private static final String CHANNEL_ID = "BridgerForegroundServiceChannel";

    private NotificationManager notificationManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        BleSingleton singleton = BleSingleton.getInstance(getApplicationContext());
        singleton.addDataListener(this);
        singleton.addConnectionListener(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Bridger Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create the initial notification
        Notification initialNotification = buildNotification(
            "Bridger Service",
            "Listening for BLE events."
        );
        startForeground(SERVICE_NOTIFICATION_ID, initialNotification);

        Log.d(TAG, "Service started. Registering listeners.");

        // If already connected when service starts, update notification immediately
        BleSingleton singleton = BleSingleton.getInstance(getApplicationContext());
        if (singleton.isConnected()) onDeviceConnected();

        return START_STICKY;
    }

    /**
     * A helper method to build and update the notification.
     */
    private Notification buildNotification(String title, String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification) // Use a system icon
            .build();
    }

    /**
     * A helper method to post updates to the notification.
     */
    private void updateNotification(String title, String text) {
        Notification notification = buildNotification(title, text);
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification);
    }

    // --- BleDataListener Implementation ---

    @Override
    public void onDataReceived(Bundle data) {
        Log.d(TAG, "Data received in service, starting Headless JS task.");
        Intent serviceIntent = new Intent(getApplicationContext(), BridgerHeadlessTask.class);
        serviceIntent.putExtras(data);
        getApplicationContext().startService(serviceIntent);
    }

    // --- BleConnectionListener Implementation ---

    @Override
    public void onDeviceConnected() {
        Log.d(TAG, "Listener notified: Device Connected.");
        updateNotification("Bridger Connected", "Receiving data from your device.");
    }

    @Override
    public void onDeviceDisconnected() {
        Log.w(TAG, "Listener notified: Device Disconnected. Stopping service.");
        updateNotification("Bridger Disconnected", "Connection lost. Service is stopping.");
        stopSelf();
    }

    @Override
    public void onDeviceFailedToConnect(String deviceAddress, String reason) {
        Log.e(TAG, "Listener notified: Failed to connect. Stopping service. Reason: " + reason);
        updateNotification("Bridger Connection Failed", "Could not connect. Service is stopping.");
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service is being destroyed. Cleaning up.");
        // It's crucial to clean up listeners to prevent memory leaks
        BleSingleton singleton = BleSingleton.getInstance(getApplicationContext());

        singleton.removeDataListener(this);
        singleton.removeConnectionListener(this);

        singleton.disconnect();
        stopForeground(true);
    }
}
