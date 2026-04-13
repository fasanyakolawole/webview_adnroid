package com.naijameals.mfas.driver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/**
 * Shared notification channel for new-order alerts (standard sound and importance).
 * <p>
 * <b>When the app is swiped away / process killed:</b> some devices delay or block starting your
 * app for <b>data-only</b> messages. For reliable delivery in that state, use a <b>hybrid</b> FCM
 * message: include both {@code notification} (so Google Play Services can show a tray notification)
 * and {@code data}, and set {@code android.notification.channel_id} to {@link #CHANNEL_ID} so it
 * uses this channel. Use {@code android.priority} {@code HIGH} if you need reliable delivery.
 * <p>
 * <b>When the app is only minimised:</b> {@code data}-only + {@code HIGH} priority is usually enough
 * for {@link MyFirebaseMessagingService#onMessageReceived} to run and post the notification.
 */
public final class OrderNotificationChannel {

    /** New id so users upgrading from the old “call-style” channel get normal behaviour. */
    public static final String CHANNEL_ID = "naija_meals_orders";
    private static final String CHANNEL_NAME = "Order Update";
    private static final String CHANNEL_DESCRIPTION =
            "Alerts when new orders arrive.";

    private OrderNotificationChannel() {}

    public static void ensureCreated(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(CHANNEL_DESCRIPTION);
        channel.enableLights(true);
        channel.enableVibration(true);
        channel.setShowBadge(true);
        channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(soundUri, audioAttributes);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}
