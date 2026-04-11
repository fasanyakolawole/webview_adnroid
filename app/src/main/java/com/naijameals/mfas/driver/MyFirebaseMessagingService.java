package com.naijameals.mfas.driver;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
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
        if (token != null && !token.isEmpty()) {
            FcmTokenStore.save(this, token);
            ClientFcmTokenRegistrar.registerWhenPossible(this);
        }
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

            Intent broadcastIntent = new Intent("com.naijameals.mfas.driver.NOTIFICATION_RECEIVED");
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

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        int smallIconId = appContext.getApplicationInfo().icon;
        if (smallIconId == 0) {
            smallIconId = R.drawable.ic_notification;
        }

        Bitmap largeIcon = loadLauncherIconBitmap(appContext);
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(appContext, OrderNotificationChannel.CHANNEL_ID)
                .setSmallIcon(smallIconId)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setContentIntent(pendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageBody))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setWhen(System.currentTimeMillis());
        
        // Add large icon if available (shows app icon in expanded notification)
        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon);
        }
        if (soundUri != null) {
            notificationBuilder.setSound(soundUri);
        }

        NotificationManagerCompat.from(appContext).notify(notificationId, notificationBuilder.build());
    }

    /**
     * Same icon the user sees on the home screen (handles adaptive icons; avoids decodeResource on XML mipmaps).
     */
    private static Bitmap loadLauncherIconBitmap(Context context) {
        try {
            Drawable d = context.getPackageManager().getApplicationIcon(context.getPackageName());
            return drawableToBitmap(d, context);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not load app icon", e);
            return null;
        }
    }

    private static Bitmap drawableToBitmap(Drawable drawable, Context context) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bm = ((BitmapDrawable) drawable).getBitmap();
            if (bm != null && !bm.isRecycled()) {
                return bm;
            }
        }
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        float density = context.getResources().getDisplayMetrics().density;
        if (w <= 0 || h <= 0) {
            int px = Math.round(64 * density);
            w = h = px;
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
