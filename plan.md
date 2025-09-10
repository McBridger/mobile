
### **Part 1: Create the Native Singleton (The "Brain")**

The Singleton will hold the BLE logic and state. It ensures that both your foreground app (via the Native Module) and your background service are talking to the exact same object.

**File: `android/app/src/main/java/com/your-package/BleSingleton.java`**

```java
package com.rn.bridger; // Or your actual package

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import // ... your other BLE imports

public class BleSingleton {
    private static final String TAG = "BleSingleton";
    private static final String PREFS_NAME = "BLE_APP_PREFS";
    private static final String LAST_MESSAGE_KEY = "LAST_RECEIVED_MESSAGE";

    private static BleSingleton instance;
    private final BridgerBleManager bleManager; // Your existing BleManager from your module
    private final Context context;

    // Callback to notify listeners (our Service) of new data
    public interface BleDataListener {
        void onDataReceived(String data);
    }
    private BleDataListener dataListener;

    private BleSingleton(Context context) {
        this.context = context.getApplicationContext(); // Use application context to avoid leaks
        this.bleManager = new BridgerBleManager(this.context);
        
        // Set up the notification callback within your BleManager
        // This is a conceptual example; adapt it to your BleManager's implementation.
        // The goal is that when your BleManager receives data, it calls this lambda.
        this.bleManager.setNotificationCallback(notifyCharacteristic)
            .with((device, data) -> {
                String value = data.getStringValue(0);
                if (value != null) {
                    Log.d(TAG, "Data received in Singleton: " + value);
                    // 1. Persist the data immediately
                    saveLastReceivedMessage(value);
                    // 2. Notify any background listener (our service)
                    if (dataListener != null) {
                        dataListener.onDataReceived(value);
                    }
                }
            });
    }

    public static synchronized BleSingleton getInstance(Context context) {
        if (instance == null) {
            instance = new BleSingleton(context);
        }
        return instance;
    }

    // --- Public methods to be called by Service and RN Module ---

    public void connect(String address) {
        // Your connection logic here, using the bleManager
    }

    public void disconnect() {
        // Your disconnection logic
    }


    public boolean isConnected() {
        return bleManager.isConnected();
    }

    public void setDataListener(BleDataListener listener) {
        this.dataListener = listener;
    }

    // --- SharedPreferences Logic ---

    public void saveLastReceivedMessage(String message) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(LAST_MESSAGE_KEY, message);
        editor.apply();
        Log.d(TAG, "Saved to SharedPreferences: " + message);
    }

    public String getLastReceivedMessage() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(LAST_MESSAGE_KEY, null); // Return null if nothing is saved
    }
}
```

### **Part 2: Create the Foreground Service (The "Host")**

This service will run persistently. Its main jobs are to keep the Singleton alive and to launch the Headless JS task when it receives data.

**File: `android/app/src/main/java/com/your-package/BridgerForegroundService.java`**

```java
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
import androidx.core.app.NotificationCompat;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

public class BridgerForegroundService extends Service {
    private static final int SERVICE_NOTIFICATION_ID = 12345;
    private static final String CHANNEL_ID = "BridgerForegroundServiceChannel";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Bridger Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bridger Connected")
            .setContentText("Listening for BLE events in the background.")
            // .setSmallIcon(R.drawable.ic_notification) // Add a notification icon
            .build();
        startForeground(SERVICE_NOTIFICATION_ID, notification);

        // Get the Singleton and set the listener
        BleSingleton.getInstance(getApplicationContext()).setDataListener(data -> {
            // This is the callback from the Singleton!
            Intent serviceIntent = new Intent(getApplicationContext(), BridgerHeadlessTask.class);
            Bundle bundle = new Bundle();
            bundle.putString("bleData", data);
            serviceIntent.putExtras(bundle);
            getApplicationContext().startService(serviceIntent);
        });

        return START_STICKY; // If the service is killed, it will be automatically restarted.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up: stop foreground, remove notification, disconnect BLE
        stopForeground(true);
        BleSingleton.getInstance(getApplicationContext()).disconnect();
    }
}
```

### **Part 3: Create the Headless JS Task (The Native "Bridge")**

This is a simple, required piece of boilerplate that receives the intent from the Foreground Service and starts the JavaScript task.

**File: `android/app/src/main/java/com/your-package/BridgerHeadlessTask.java`**

```java
package com.rn.bridger;

import android.content.Intent;
import android.os.Bundle;
import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;
import javax.annotation.Nullable;

public class BridgerHeadlessTask extends HeadlessJsTaskService {
    @Override
    protected @Nullable HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            return new HeadlessJsTaskConfig(
                "BridgerHeadlessTask", // This name must match the JS registration
                Arguments.fromBundle(extras),
                5000, // Timeout for the task in milliseconds
                true  // Allow task to run in foreground
            );
        }
        return null;
    }
}
```

### **Part 4: Create the Transparent Activity (The "Worker")**

**1. Define the Style (res/values/styles.xml):**
```xml
<resources>
    <!-- ... your other styles ... -->
    <style name="Theme.Transparent" parent="Theme.AppCompat.Light.NoActionBar">
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:backgroundDimEnabled">false</item>
    </style>
</resources>
```

**2. Create the Activity File: `android/app/src/main/java/com/your-package/ClipboardWriterActivity.java`**
```java
package com.rn.bridger;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

public class ClipboardWriterActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("ClipboardWriter", "Activity Created");

        String textToCopy = getIntent().getStringExtra("textToCopy");
        if (textToCopy != null) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Bridger Data", textToCopy);
            clipboard.setPrimaryClip(clip);
            Log.d("ClipboardWriter", "Copied to clipboard: " + textToCopy);
        }
        
        finish(); // Immediately close the activity
    }
}
```

### **Part 5: Configure `AndroidManifest.xml`**

This is a critical step to declare all your new native components.

**File: `android/app/src/main/AndroidManifest.xml`**

```xml
<manifest ...>
    <!-- You need this permission for a Foreground Service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <!-- Ensure you have your Bluetooth permissions as well -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <application ...>
        <!-- ... your other activities ... -->

        <!-- DECLARE THE FOREGROUND SERVICE -->
        <service
            android:name=".BridgerForegroundService"
            android:enabled="true"
            android:exported="false" />

        <!-- DECLARE THE HEADLESS JS TASK -->
        <service
            android:name=".BridgerHeadlessTask"
            android:enabled="true"
            android:exported="false" />
        
        <!-- DECLARE THE TRANSPARENT ACTIVITY -->
        <activity
            android:name=".ClipboardWriterActivity"
            android:theme="@style/Theme.Transparent"
            android:exported="false"
            android:taskAffinity=""
            android:excludeFromRecents="true" />

    </application>
</manifest>
```

### **Part 6: Integrate with Your React Native Code**

**1. Modify Your Existing Native Module (`BleConnectorModule.java`)**

Add methods to control the service, get the last message, and launch the transparent activity.

```java
// Inside BleConnectorModule.java

// ... existing code ...

@ReactMethod
public void startBridgerService() {
    Intent serviceIntent = new Intent(getReactApplicationContext(), BridgerForegroundService.class);
    getReactApplicationContext().startService(serviceIntent);
}

@ReactMethod
public void stopBridgerService() {
    Intent serviceIntent = new Intent(getReactApplicationContext(), BridgerForegroundService.class);
    getReactApplicationContext().stopService(serviceIntent);
}

@ReactMethod
public void getLastReceivedMessage(Promise promise) {
    String message = BleSingleton.getInstance(getReactApplicationContext()).getLastReceivedMessage();
    promise.resolve(message);
}

@ReactMethod
public void launchClipboardWriter(String text) {
    Context context = getReactApplicationContext();
    Intent intent = new Intent(context, ClipboardWriterActivity.class);
    intent.putExtra("textToCopy", text);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Required when starting activity from non-activity context
    context.startActivity(intent);
}
```

**2. Update Your JavaScript Code**

**File: `index.js` (or your app's entry point)**

```javascript
import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';
import YourNativeModule from './YourNativeModule'; // Import your native module

// 1. Define and register the Headless JS Task
const BridgerHeadlessTask = async (taskData) => {
  console.log('Headless Task received data:', taskData.bleData);
  if (taskData.bleData) {
    // Launch the transparent activity to handle the clipboard
    YourNativeModule.launchClipboardWriter(taskData.bleData);
  }
};

AppRegistry.registerHeadlessTask('BridgerHeadlessTask', () => BridgerHeadlessTask);

// 2. Register your main component
AppRegistry.registerComponent(appName, () => App);
```

**File: `DevicePage.js` (or wherever you connect)**

```javascript
// ... imports, including your native module
import BleConnector from './YourNativeModule';

// ...

const handleConnect = async () => {
  try {
    await BleConnector.connect(device.id);
    // After a successful connection, start the service!
    BleConnector.startBridgerService();
    console.log('Connection successful, starting foreground service.');
  } catch (error) {
    console.error('Connection failed', error);
  }
};

// Also, remember to stop the service on disconnect or when the app is fully closed
const handleDisconnect = async () => {
    await BleConnector.disconnect();
    BleConnector.stopBridgerService();
};
```

**File: `App.js` (or a main component for UI sync)**

```javascript
import React, { useEffect, useState } from 'react';
import BleConnector from './YourNativeModule';
// ... other imports

const App = () => {
  const [lastMessage, setLastMessage] = useState('');

  useEffect(() => {
    // When the app starts, get the last known message from SharedPreferences
    const syncData = async () => {
      const message = await BleConnector.getLastReceivedMessage();
      if (message) {
        setLastMessage(message);
        console.log('Synced last message from native:', message);
      }
    };
    syncData();
  }, []);

  // ... rest of your App UI
};
```

You now have a complete, robust, and optimized plan. Just replace the placeholder names and integrate your specific BLE logic into the Singleton, and you will have a professional-grade background processing feature.