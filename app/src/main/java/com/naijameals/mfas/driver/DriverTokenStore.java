package com.naijameals.mfas.driver;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the driver JWT from WebView {@code localStorage @driver_token} so native code can
 * call the API when the WebView is not running.
 */
public final class DriverTokenStore {

    private static final String PREF = "driver_token_store";
    private static final String KEY_TOKEN = "token";

    private DriverTokenStore() {}

    public static void save(Context context, String token) {
        if (context == null) {
            return;
        }
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        p.edit().putString(KEY_TOKEN, token != null ? token : "").apply();
    }

    public static String getToken(Context context) {
        if (context == null) {
            return null;
        }
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String t = p.getString(KEY_TOKEN, "");
        return t != null && !t.isEmpty() ? t : null;
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_TOKEN).apply();
    }
}
