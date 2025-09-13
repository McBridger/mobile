package com.rn.bridger;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.facebook.react.bridge.ReactApplicationContext;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Objects;

public class BubbleModule extends NativeBubbleSpec {

    public static final String NAME = "NativeBubble";
    private static final String TAG = "BubbleModule";
    private static final String CHANNEL_ID = "BubbleChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String SHORTCUT_ID = "bubble_shortcut";

    // Action strings for PendingIntents
    public static final String ACTION_BUBBLE_CLICK = "com.rn.bridger.BUBBLE_CLICK";
    public static final String ACTION_BUBBLE_LONG_CLICK = "com.rn.bridger.BUBBLE_LONG_CLICK";

    // 1. Статическая слабая ссылка на экземпляр BubbleActivity
    public static WeakReference<BubbleActivity> bubbleActivityInstance;

    // 2. Статический метод для установки ссылки из Activity
    public static void setBubbleActivityInstance(BubbleActivity activity) {
        bubbleActivityInstance = new WeakReference<>(activity);
    }

    private final NotificationManager notificationManager;
    private final ReactApplicationContext reactContext;

    public BubbleModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.notificationManager = (NotificationManager) reactContext.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void showBubble() {
        Log.d(TAG, "showBubble called");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // ... fallback ...
            return;
        }

        // Создаем Intent, который запускает нашу BubbleActivity
        Intent bubbleIntent = new Intent(reactContext, BubbleActivity.class);
        // Важно, чтобы у каждого бабла был свой уникальный Intent
        bubbleIntent.setAction(Long.toString(System.currentTimeMillis()));

        // Создаем PendingIntent для Activity
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                reactContext,
                0, // requestCode
                bubbleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        Person user = new Person.Builder()
                .setName("Bridger Bubble")
                .setIcon(IconCompat.createWithResource(reactContext, R.drawable.ic_send_clipboard))
                .build();

        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(reactContext, SHORTCUT_ID)
            .setCategories(Collections.singleton("com.rn.bridger.BUBBLE_CATEGORY"))
            .setIntent(new Intent(reactContext, MainActivity.class).setAction(Intent.ACTION_MAIN))
            .setLongLived(true)
            .setShortLabel(Objects.requireNonNull(user.getName()))
            .setIcon(IconCompat.createWithResource(reactContext, R.drawable.ic_send_clipboard))
            .setPerson(user) // <--- РЕКОМЕНДАЦИЯ: Свяжите Shortcut с Person
            .build();
        
        // Рекомендуется использовать MessagingStyle для Android 11+
        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(user)
            .setConversationTitle("Bridger Bubble")
            .addMessage("Нажмите, чтобы открыть", System.currentTimeMillis(), user);


        // Создаем BubbleMetadata
        NotificationCompat.BubbleMetadata bubbleMetadata = new NotificationCompat.BubbleMetadata.Builder(contentPendingIntent, IconCompat.createWithResource(reactContext, R.drawable.ic_send_clipboard))
                .setDesiredHeight(600)
                .build();

        // Строим уведомление
        NotificationCompat.Builder builder = new NotificationCompat.Builder(reactContext, CHANNEL_ID)
            .setContentIntent(contentPendingIntent)
            .setSmallIcon(R.drawable.ic_notification)
            .setBubbleMetadata(bubbleMetadata)
            .setShortcutId(SHORTCUT_ID)
            .addPerson(user)
            .setStyle(messagingStyle) // Добавляем стиль
            .setCategory(NotificationCompat.CATEGORY_MESSAGE); // Важно для "бесед"


        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    public void hideBubble() {
        Log.d(TAG, "hideBubble called from JS");

        // 3. Используем нашу прямую ссылку вместо getCurrentActivity()
        if (bubbleActivityInstance != null && bubbleActivityInstance.get() != null) {
            Log.d(TAG, "Found BubbleActivity instance. Finishing it.");
            bubbleActivityInstance.get().finishAndRemoveTask();
        } else {
            Log.e(TAG, "Could not find an active BubbleActivity instance to close.");
            // Попробуем запасной вариант на всякий случай
            final Activity activity = reactContext.getCurrentActivity();
            if (activity instanceof BubbleActivity) {
                activity.finishAndRemoveTask();
            }
        }

        showBubble();

        // notificationManager.cancel(NOTIFICATION_ID);
    }

    @Override
    public boolean isBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Bubbles exist from API 29
            for (android.service.notification.StatusBarNotification sbn : notificationManager.getActiveNotifications()) {
                if (sbn.getId() == NOTIFICATION_ID) {
                    NotificationCompat.BubbleMetadata bubbleMetadata = NotificationCompat.getBubbleMetadata(sbn.getNotification());
                    if (bubbleMetadata != null) {
                        Log.d(TAG, "isBubble called, returning true");
                        return true;
                    }
                }
            }
        }
        Log.d(TAG, "isBubble called, returning false");
        return false;
    }

// ...

    private void hideBubbleNew() {
        // Создаем Intent, который запускает нашу BubbleActivity
        Intent bubbleIntent = new Intent(reactContext, BubbleActivity.class);
        // Важно, чтобы у каждого бабла был свой уникальный Intent
        bubbleIntent.setAction(Intent.ACTION_MAIN);

        // Создаем PendingIntent для Activity
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                reactContext,
                0, // requestCode
                bubbleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        Person user = new Person.Builder()
                .setName("Bridger Bubble")
                .setIcon(IconCompat.createWithResource(reactContext, R.drawable.ic_send_clipboard))
                .build();

        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(reactContext, SHORTCUT_ID)
            .setCategories(Collections.singleton("com.rn.bridger.BUBBLE_CATEGORY"))
            .setIntent(new Intent(reactContext, MainActivity.class).setAction(Intent.ACTION_MAIN))
            .setLongLived(true)
            .setShortLabel(Objects.requireNonNull(user.getName()))
            .setIcon(IconCompat.createWithResource(reactContext, R.drawable.ic_send_clipboard))
            .setPerson(user) // <--- РЕКОМЕНДАЦИЯ: Свяжите Shortcut с Person
            .build();
        
        // Рекомендуется использовать MessagingStyle для Android 11+
        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(user)
            .setConversationTitle("Bridger Bubble")
            .addMessage("Нажмите, чтобы открыть", System.currentTimeMillis(), user);

        // Строим уведомление
        NotificationCompat.Builder builder = new NotificationCompat.Builder(reactContext, CHANNEL_ID)
            .setContentIntent(contentPendingIntent)
            .setSmallIcon(R.drawable.ic_notification)
            .setShortcutId(SHORTCUT_ID)
            .addPerson(user)
            .setStyle(messagingStyle) // Добавляем стиль
            .setCategory(NotificationCompat.CATEGORY_MESSAGE); // Важно для "бесед"


        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bridger Bubble Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void showFallbackNotification(String title, String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(reactContext, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
