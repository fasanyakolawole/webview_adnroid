package com.naijameals.mfas.driver;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Every 30s while the app is in use: read stored driver token, get GPS, PUT {@code /api/driver/location}.
 * Started when {@link MainActivity} opens. Stopped only when the activity is finishing (removed from
 * Recents or exited). Switching to another app (e.g. Maps) via Home or Recents does not finish the
 * activity, so the service keeps running; rotation / transient destroy without finishing also keeps it.
 */
public class DriverLocationNativeForegroundService extends Service {

    private static final String TAG = "DriverLocationNative";

    public static final String ACTION_START = "com.naijameals.mfas.driver.action.START_DRIVER_LOCATION_NATIVE";
    public static final String ACTION_STOP = "com.naijameals.mfas.driver.action.STOP_DRIVER_LOCATION_NATIVE";

    /** Same id as prior heartbeat so order-dismiss logic can exclude it by channel. */
    public static final int NOTIFICATION_ID = 20003;

    private static final String CHANNEL_ID = "driver_location_native";
    /** Wall-clock cadence: next {@link #runLocationCycle} starts ~30s after this cycle started (not after it ends). */
    private static final long INTERVAL_MS = 30_000L;
    private static final String LOCATION_URL = "https://api.naijameals.com/api/driver/location";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable cycleRunnable = this::runLocationCycle;
    /** Elapsed realtime when {@link #runLocationCycle} began; used to keep PUTs on a ~30s cadence. */
    private long cycleStartElapsedMs;

    private final ExecutorService httpExecutor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build();

    private FusedLocationProviderClient fusedLocationClient;

    public static void start(Context context) {
        Intent i = new Intent(context, DriverLocationNativeForegroundService.class);
        i.setAction(ACTION_START);
        ContextCompat.startForegroundService(context, i);
    }

    public static void stop(Context context) {
        Intent i = new Intent(context, DriverLocationNativeForegroundService.class);
        i.setAction(ACTION_STOP);
        context.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            handler.removeCallbacks(cycleRunnable);
            httpExecutor.shutdown();
            stopForegroundCleanup();
            stopSelf();
            return START_NOT_STICKY;
        }

        int fgType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), fgType);

        handler.removeCallbacks(cycleRunnable);
        handler.post(cycleRunnable);
        return START_STICKY;
    }

    private void runLocationCycle() {
        cycleStartElapsedMs = SystemClock.elapsedRealtime();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NaijaMeals:DriverLoc");
        wl.setReferenceCounted(false);
        wl.acquire(60_000L);

        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission");
            finishCycle(wl);
            return;
        }

        String token = DriverTokenStore.getToken(this);
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "No driver token — open the app and sign in once to sync token.");
            finishCycle(wl);
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnCompleteListener(task -> {
                    Location loc = task.isSuccessful() ? task.getResult() : null;
                    if (loc != null) {
                        httpExecutor.execute(() -> sendPut(token, loc, wl));
                        return;
                    }
                    fusedLocationClient.getLastLocation().addOnCompleteListener(t2 -> {
                        Location cached = t2.isSuccessful() ? t2.getResult() : null;
                        if (cached != null) {
                            httpExecutor.execute(() -> sendPut(token, cached, wl));
                        } else {
                            Log.w(TAG, "No location fix this cycle");
                            finishCycle(wl);
                        }
                    });
                });
    }

    private void sendPut(String token, Location location, PowerManager.WakeLock wl) {
        try {
            JSONObject body = new JSONObject();
            body.put("latitude", location.getLatitude());
            body.put("longitude", location.getLongitude());
            String json = body.toString();

            Request request = new Request.Builder()
                    .url(LOCATION_URL)
                    .put(RequestBody.create(JSON, json))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                int code = response.code();
                String text = "";
                ResponseBody rb = response.body();
                if (rb != null) {
                    try {
                        text = rb.string();
                    } catch (IOException ignored) {
                    }
                }
                if (code >= 200 && code < 300) {
                    Log.i(TAG, "Location PUT OK " + code + " " + truncate(text, 200));
                } else {
                    Log.e(TAG, "Location PUT failed " + code + " " + truncate(text, 500));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Location PUT network error", e);
        } catch (Exception e) {
            Log.e(TAG, "Location PUT error", e);
        } finally {
            finishCycle(wl);
        }
    }

    private void finishCycle(PowerManager.WakeLock wl) {
        try {
            if (wl != null && wl.isHeld()) {
                wl.release();
            }
        } catch (RuntimeException ignored) {
        }
        long elapsed = SystemClock.elapsedRealtime() - cycleStartElapsedMs;
        long nextDelayMs = Math.max(0L, INTERVAL_MS - elapsed);
        handler.postDelayed(cycleRunnable, nextDelayMs);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s != null ? s : "";
        }
        return s.substring(0, max) + "...";
    }

    private void stopForegroundCleanup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(cycleRunnable);
        httpExecutor.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.driver_location_native_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(getString(R.string.driver_location_native_channel_desc));
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.driver_location_native_notification_title))
                .setContentText(getString(R.string.driver_location_native_notification_text))
                .setSmallIcon(getApplicationInfo().icon != 0 ? getApplicationInfo().icon : R.drawable.ic_notification)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }
}
