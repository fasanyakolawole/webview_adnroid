package com.naijameals.mfas.driver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/**
 * Shared notification channel for urgent “New Order” alerts (ringtone + max importance).
 * <p>
 * <b>When the app is swiped away / process killed:</b> some devices delay or block starting your
 * app for <b>data-only</b> messages. For reliable delivery in that state, use a <b>hybrid</b> FCM
 * message: include both {@code notification} (so Google Play Services can show a tray notification)
 * and {@code data}, and set {@code android.notification.channel_id} to {@link #CHANNEL_ID} so it
 * uses this urgent channel. Use {@code android.priority} {@code HIGH}.
 * <p>
 * <b>When the app is only minimised:</b> {@code data}-only + {@code HIGH} priority is usually enough
 * for {@link MyFirebaseMessagingService#onMessageReceived} to run and post the full-screen style alert.
 */
public final class OrderNotificationChannel {

    public static final String CHANNEL_ID = "naija_meals_orders_urgent";
    private static final String CHANNEL_NAME = "New orders (urgent)";
    private static final String CHANNEL_DESCRIPTION =
            "Incoming new orders — full-screen and ringtone like a call";

    private OrderNotificationChannel() {}

    public static void ensureCreated(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MAX
        );
        channel.setDescription(CHANNEL_DESCRIPTION);
        channel.enableLights(true);
        channel.enableVibration(true);
        channel.setShowBadge(true);
        channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        long[] ringPattern = {0, 600, 400, 600, 400, 600, 400, 1200};
        channel.setVibrationPattern(ringPattern);

        Uri ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (ringUri == null) {
            ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(ringUri, audioAttributes);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}
