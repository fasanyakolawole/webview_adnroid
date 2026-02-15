package com.example.naijameals;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "naija_meals_notifications";
    private static final String CHANNEL_NAME = "Naija Meals Notifications";
    private static final String CHANNEL_DESCRIPTION = "Notifications for Naija Meals app";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        // Token is automatically refreshed by Firebase
        // You can send it to your server here if needed
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Handle both notification payload and data payload
        String title = "Naija Meals";
        String body = "";
        
        // Get notification data
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null 
                    ? remoteMessage.getNotification().getTitle() 
                    : "Naija Meals";
            body = remoteMessage.getNotification().getBody() != null 
                   ? remoteMessage.getNotification().getBody() 
                   : "";
            Log.d(TAG, "Notification Title: " + title);
            Log.d(TAG, "Notification Body: " + body);
        }
        
        // Check if message contains a data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            // Use data payload if notification payload is empty
            if (body.isEmpty() && remoteMessage.getData().containsKey("body")) {
                body = remoteMessage.getData().get("body");
            }
            if (title.equals("Naija Meals") && remoteMessage.getData().containsKey("title")) {
                title = remoteMessage.getData().get("title");
            }
        }

        // Show notification
        if (!body.isEmpty()) {
            sendNotification(title, body, remoteMessage.getData());
        }
        
        // Vibrate device
        vibrateDevice();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            
            // Set default notification sound
            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            channel.setSound(defaultSoundUri, null);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void sendNotification(String title, String messageBody, java.util.Map<String, String> data) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // Add data payload to intent
        if (data != null && !data.isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
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
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconResId)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageBody))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        
        // Add large icon if available (shows app icon in expanded notification)
        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon);
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            // Use a unique ID for each notification
            int notificationId = (int) System.currentTimeMillis();
            notificationManager.notify(notificationId, notificationBuilder.build());
        }
    }

    private void vibrateDevice() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
    }
}
