package com.naijameals.mfas.driver;

import android.content.Context;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Persists the latest FCM registration token whenever Firebase issues or refreshes it.
 * {@link FirebaseMessaging#getToken()} always returns the current token; we mirror it here on
 * refresh ({@link MyFirebaseMessagingService#onNewToken}), on app start, and after successful reads.
 */
public final class FcmTokenStore {

    private static final String TAG = "FcmTokenStore";
    private static final String PREF = "naijameals_fcm";
    private static final String KEY_TOKEN = "registration_token";

    private FcmTokenStore() {}

    public static void save(Context context, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TOKEN, token)
                .apply();
    }

    /** Last known token from disk; may lag until {@link #syncFromFirebase} or {@link FirebaseMessaging#getToken()} runs. */
    public static String getCachedToken(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, null);
    }

    /**
     * Fetches the current token from Firebase and persists it (handles reinstall, data clear, and refresh).
     */
    public static void syncFromFirebase(Context context) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                Log.w(TAG, "syncFromFirebase failed", task.getException());
                return;
            }
            save(context, task.getResult());
            Log.d(TAG, "Synced FCM token from Firebase");
        });
    }
}
