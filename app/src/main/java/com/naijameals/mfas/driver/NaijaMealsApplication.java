package com.naijameals.mfas.driver;

import android.app.Application;

/**
 * Creates the urgent order notification channel before any FCM message is shown so the system
 * and Firebase defaults can use {@link OrderNotificationChannel#CHANNEL_ID}.
 */
public class NaijaMealsApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        OrderNotificationChannel.ensureCreated(this);
        FcmTokenStore.syncFromFirebase(this);
    }
}
