package com.example.naijameals;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles incoming FCM when {@code onMessageReceived} runs (foreground, or data-only with high
 * priority when the process can start). If the app is force-stopped or the OEM blocks delivery,
 * notifications may not arrive until the app is opened again. See {@link OrderNotificationChannel}
 * for hybrid server payloads when the process is killed.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    public static final String NEW_ORDER_TITLE = "New Order";
    private static final String PREF_NAME = "pending_notifications";
    private static final String PREF_KEY_NOTIFICATIONS = "notifications_list";

    @Override
    public void onCreate() {
        super.onCreate();
        OrderNotificationChannel.ensureCreated(this);
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        // Token is automatically refreshed by Firebase
        // You can send it to your server here if needed
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        OrderNotificationChannel.ensureCreated(this);
        try {
            Log.d(TAG, "onMessageReceived from=" + remoteMessage.getFrom()
                    + " hasNotification=" + (remoteMessage.getNotification() != null)
                    + " dataKeys=" + remoteMessage.getData().keySet());

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "Notifications disabled; user will not see alerts until enabled in settings");
            }

            String body = "";

            if (remoteMessage.getNotification() != null) {
                body = remoteMessage.getNotification().getBody() != null
                        ? remoteMessage.getNotification().getBody()
                        : "";
                Log.d(TAG, "Notification Body: " + body);
            }

            if (remoteMessage.getData().size() > 0) {
                Log.d(TAG, "Message data payload: " + remoteMessage.getData());
                if (body.isEmpty() && remoteMessage.getData().containsKey("body")) {
                    body = remoteMessage.getData().get("body");
                }
            }

            if (body.isEmpty()) {
                body = "Tap to view order details";
            }

            final String displayTitle = NEW_ORDER_TITLE;

            storeNotificationForAlert(displayTitle, body);
            sendNotification(displayTitle, body, remoteMessage.getData());

            vibrateDevice();

            Intent broadcastIntent = new Intent("com.example.naijameals.NOTIFICATION_RECEIVED");
            broadcastIntent.putExtra("title", displayTitle);
            broadcastIntent.putExtra("body", body);
            sendBroadcast(broadcastIntent);
        } catch (Throwable t) {
            Log.e(TAG, "Error handling FCM message (e.g. cold start); notification may be missing", t);
        }
    }
    
    // Store notification to show as alert dialog when app opens
    private void storeNotificationForAlert(String title, String body) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String notificationsJson = prefs.getString(PREF_KEY_NOTIFICATIONS, "[]");
            
            JSONArray notificationsArray = new JSONArray(notificationsJson);
            JSONObject notification = new JSONObject();
            notification.put("title", title);
            notification.put("body", body);
            notification.put("timestamp", System.currentTimeMillis());
            
            notificationsArray.put(notification);
            
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_KEY_NOTIFICATIONS, notificationsArray.toString());
            editor.apply();
            
            Log.d(TAG, "Stored notification for alert: " + title);
        } catch (JSONException e) {
            Log.e(TAG, "Error storing notification: " + e.getMessage());
        }
    }

    private void sendNotification(String title, String messageBody, java.util.Map<String, String> data) {
        Context appContext = getApplicationContext();
        Intent intent = new Intent(appContext, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (data != null && !data.isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        int notificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId,
                intent,
                flags
        );

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId + 1,
                intent,
                flags
        );

        Uri ringSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (ringSoundUri == null) {
            ringSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        
        // Use app launcher icon for notifications
        // Try ic_launcher_foreground first (your app icon)
        int iconResId = getResources().getIdentifier("ic_launcher_foreground", "drawable", getPackageName());
        if (iconResId == 0) {
            // Try regular launcher icon
            iconResId = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
        }
        if (iconResId == 0) {
            // Try round icon
            iconResId = getResources().getIdentifier("ic_launcher_round", "mipmap", getPackageName());
        }
        if (iconResId == 0) {
            // Fallback to notification icon or default
            iconResId = getResources().getIdentifier("ic_notification", "drawable", getPackageName());
        }
        if (iconResId == 0) {
            iconResId = android.R.drawable.ic_dialog_info; // Final fallback
        }
        
        // Get large icon bitmap for better visibility (use app icon)
        android.graphics.Bitmap largeIcon = null;
        try {
            // Try to get the launcher icon as large icon
            int largeIconResId = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
            if (largeIconResId == 0) {
                largeIconResId = getResources().getIdentifier("ic_launcher_foreground", "drawable", getPackageName());
            }
            if (largeIconResId != 0) {
                largeIcon = android.graphics.BitmapFactory.decodeResource(getResources(), largeIconResId);
            }
        } catch (Exception e) {
            // If decoding fails, largeIcon will remain null
            Log.e(TAG, "Error loading large icon: " + e.getMessage());
        }
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(appContext, OrderNotificationChannel.CHANNEL_ID)
                .setSmallIcon(iconResId)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setSound(ringSoundUri)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageBody))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setWhen(System.currentTimeMillis());
        
        // Add large icon if available (shows app icon in expanded notification)
        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon);
        }

        NotificationManagerCompat.from(appContext).notify(notificationId, notificationBuilder.build());
    }

    private void vibrateDevice() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] ringPattern = {0, 600, 400, 600, 400, 600, 400, 1200};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(ringPattern, -1));
            } else {
                vibrator.vibrate(ringPattern, -1);
            }
        }
    }
}
