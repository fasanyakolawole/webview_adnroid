package com.example.naijameals;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private ValueCallback<Uri[]> fileUploadCallback;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private static final String PREF_NAME = "pending_notifications";
    private static final String PREF_KEY_NOTIFICATIONS = "notifications_list";
    private BroadcastReceiver notificationReceiver;

    // JavaScript Interface class
    public class WebAppInterface {
        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void showToastLong(String message) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void showAlert(String title, String message) {
            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }

        @JavascriptInterface
        public void showConfirmDialog(String title, String message) {
            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("Confirm", (dialog, which) -> {
                            webView.evaluateJavascript(
                                    "if(window.onConfirmContinue) window.onConfirmContinue();",
                                    null
                            );
                        })
                        .setNegativeButton("Edit", (dialog, which) -> {
                            webView.evaluateJavascript(
                                    "if(window.onConfirmCancel) window.onConfirmCancel();",
                                    null
                            );
                        })
                        .show();
            });
        }

        @JavascriptInterface
        public void showAlertWithCallback(String title, String message) {
            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("OK", (dialog, which) -> {
                            webView.evaluateJavascript("if(window.onAlertDismiss) window.onAlertDismiss();", null);
                        })
                        .show();
            });
        }

        @JavascriptInterface
        public void vibrate(long milliseconds) {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vibratorManager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(milliseconds);
                }
            }
        }

        @JavascriptInterface
        public void vibratePattern(long[] pattern, int repeat) {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vibratorManager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat));
                } else {
                    vibrator.vibrate(pattern, repeat);
                }
            }
        }

        @JavascriptInterface
        public void makePhoneCall(String phoneNumber) {
            runOnUiThread(() -> {
                makeCall(phoneNumber);
            });
        }

        @JavascriptInterface
        public void triggerPageTransition(String direction) {
            runOnUiThread(() -> {
                // Default fade transition
                AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.7f);
                fadeOut.setDuration(150);
                fadeOut.setFillAfter(true);
                webView.startAnimation(fadeOut);
            });
        }

        @JavascriptInterface
        public void getFirebaseToken() {
            runOnUiThread(() -> {
                try {
                    FirebaseMessaging.getInstance().getToken()
                            .addOnCompleteListener(new OnCompleteListener<String>() {
                                @Override
                                public void onComplete(Task<String> task) {
                                    if (!task.isSuccessful()) {
                                        String error = "Failed to get FCM token. Make sure google-services.json is configured correctly.";
                                        if (task.getException() != null) {
                                            error += " Error: " + task.getException().getMessage();
                                        }
                                        webView.evaluateJavascript(
                                                "if(window.onFirebaseTokenError) window.onFirebaseTokenError('" + error.replace("'", "\\'") + "');",
                                                null
                                        );
                                        return;
                                    }

                                    // Get new FCM registration token
                                    String token = task.getResult();
                                    
                                    // Send token to JavaScript
                                    webView.evaluateJavascript(
                                            "if(window.onFirebaseTokenReceived) window.onFirebaseTokenReceived('" + token + "');",
                                            null
                                    );
                                }
                            });
                } catch (Exception e) {
                    String error = "Firebase not initialized. Please configure google-services.json with package name: com.example.naijameals";
                    webView.evaluateJavascript(
                            "if(window.onFirebaseTokenError) window.onFirebaseTokenError('" + error.replace("'", "\\'") + "');",
                            null
                    );
                }
            });
        }
    }
    
    // Make phone call function
    private void makeCall(String phoneNumber) {
        // Remove any non-digit characters except + for international numbers
        String cleanedNumber = phoneNumber.replaceAll("[^+0-9]", "");
        
        if (cleanedNumber.isEmpty()) {
            Toast.makeText(this, "Invalid phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check and request permission for Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Request permission
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.CALL_PHONE},
                        CALL_PHONE_PERMISSION_REQUEST_CODE);
                // Store phone number to call after permission is granted
                pendingPhoneNumber = cleanedNumber;
                return;
            }
        }
        
        // Permission already granted or Android version < 6.0, make the call
        initiateCall(cleanedNumber);
    }
    
    private String pendingPhoneNumber = null;
    
    // Initiate the actual phone call
    private void initiateCall(String phoneNumber) {
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            startActivity(callIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to make call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    // Request notification permission for Android 13+
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    // Handle permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALL_PHONE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, make the call
                if (pendingPhoneNumber != null) {
                    initiateCall(pendingPhoneNumber);
                    pendingPhoneNumber = null;
                }
            } else {
                // Permission denied
                Toast.makeText(this, "Phone call permission is required to make calls", Toast.LENGTH_LONG).show();
                pendingPhoneNumber = null;
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Notification permission granted
                Log.d("MainActivity", "Notification permission granted");
            } else {
                // Notification permission denied
                Toast.makeText(this, "Notification permission is required to receive notifications", Toast.LENGTH_LONG).show();
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide the ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // Request notification permission for Android 13+
        requestNotificationPermission();
        
        // Initialize file picker launcher
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (fileUploadCallback == null) return;
                    
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // Handle multiple files if available (ClipData is used for multiple selections)
                        if (result.getData().getClipData() != null) {
                            int count = result.getData().getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = result.getData().getClipData().getItemAt(i).getUri();
                            }
                        } else if (result.getData().getData() != null) {
                            // Handle single file selection
                            results = new Uri[]{result.getData().getData()};
                        }
                    }
                    
                    fileUploadCallback.onReceiveValue(results);
                    fileUploadCallback = null;
                }
            }
        );
        
        // Create WebView programmatically
        webView = new WebView(this);
        setContentView(webView);

        // Enable JavaScript
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        
        // Enable other useful settings
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDisplayZoomControls(false);


        // Enable file access for loading local files
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        
        // Allow mixed content (HTTP and HTTPS) for debugging
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // Add JavaScript interface
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        
        // Set WebViewClient to handle page navigation
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Load all URLs within the WebView
                return false;
            }
        });
        
        // Set WebChromeClient to handle file uploads
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                // Cancel any previous callback
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;
                
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                
                // Allow multiple file selection
                if (fileChooserParams != null && fileChooserParams.getAcceptTypes() != null && fileChooserParams.getAcceptTypes().length > 0) {
                    intent.setType(fileChooserParams.getAcceptTypes()[0]);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, fileChooserParams.getAcceptTypes());
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                
                Intent chooserIntent = Intent.createChooser(intent, "Choose File");
                try {
                    filePickerLauncher.launch(chooserIntent);
                } catch (Exception e) {
                    fileUploadCallback = null;
                    Toast.makeText(MainActivity.this, "Cannot open file chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                
                return true;
            }
        });

        // Load the index.html from assets
//        webView.loadUrl("file:///android_asset/www/index.html");
        webView.loadUrl("file:///android_asset/www/index.html");
        
        // Debug: Connect to local development server
        // Note: On Android emulator, use "http://10.0.2.2:8081" instead of "localhost"
        // On physical device, use your computer's IP address (e.g., "http://192.168.1.100:8081")
//        webView.loadUrl("http://10.0.2.2:8081");
        
        // Check for pending notifications and show as alerts
        // Delay this slightly to ensure activity is fully initialized
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (!isFinishing() && !isDestroyedCompat()) {
                    checkAndShowPendingNotifications();
                    // Register broadcast receiver for notifications received while app is running
                    registerNotificationReceiver();
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error initializing notifications: " + e.getMessage());
            }
        }, 100);
    }
    
    // Helper method to check if activity is destroyed (compatible with all Android versions)
    private boolean isDestroyedCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return isDestroyed();
        }
        return false;
    }
    
    // Register broadcast receiver to show notifications immediately when app is running
    private void registerNotificationReceiver() {
        try {
            notificationReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if ("com.example.naijameals.NOTIFICATION_RECEIVED".equals(intent.getAction())) {
                            String title = intent.getStringExtra("title");
                            String body = intent.getStringExtra("body");
                            if (title != null && body != null) {
                                showNotificationAlert(title, body);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error in notification receiver: " + e.getMessage());
                    }
                }
            };
            
            IntentFilter filter = new IntentFilter("com.example.naijameals.NOTIFICATION_RECEIVED");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(notificationReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(notificationReceiver, filter);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error registering notification receiver: " + e.getMessage());
            // Continue without receiver - notifications will still show on app open
        }
    }
    
    // Check for stored notifications and show them as alert dialogs
    private void checkAndShowPendingNotifications() {
        // Delay slightly to ensure WebView is ready
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String notificationsJson = prefs.getString(PREF_KEY_NOTIFICATIONS, "[]");
                
                if (notificationsJson == null || notificationsJson.trim().isEmpty()) {
                    notificationsJson = "[]";
                }
                
                JSONArray notificationsArray = new JSONArray(notificationsJson);
                
                if (notificationsArray.length() > 0) {
                    // Show notifications one by one
                    showNotificationsSequentially(notificationsArray, 0);
                    
                    // Clear stored notifications
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(PREF_KEY_NOTIFICATIONS, "[]");
                    editor.apply();
                }
            } catch (JSONException e) {
                Log.e("MainActivity", "Error reading notifications: " + e.getMessage());
                // Clear invalid data
                try {
                    SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(PREF_KEY_NOTIFICATIONS, "[]");
                    editor.apply();
                } catch (Exception ex) {
                    Log.e("MainActivity", "Error clearing invalid notifications: " + ex.getMessage());
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Unexpected error checking notifications: " + e.getMessage());
            }
        }, 500); // Small delay to ensure UI is ready
    }
    
    // Show notifications one by one as alert dialogs
    private void showNotificationsSequentially(JSONArray notificationsArray, int index) {
        if (index >= notificationsArray.length()) {
            return; // All notifications shown
        }
        
        try {
            JSONObject notification = notificationsArray.getJSONObject(index);
            String title = notification.getString("title");
            String body = notification.getString("body");
            
            showNotificationAlert(title, body, () -> {
                // After user closes this notification, show the next one
                showNotificationsSequentially(notificationsArray, index + 1);
            });
        } catch (JSONException e) {
            Log.e("MainActivity", "Error showing notification: " + e.getMessage());
            // Try next notification
            if (index + 1 < notificationsArray.length()) {
                showNotificationsSequentially(notificationsArray, index + 1);
            }
        }
    }
    
    // Show a notification as an alert dialog (must be dismissed)
    private void showNotificationAlert(String title, String body) {
        showNotificationAlert(title, body, null);
    }
    
    // Show a notification as an alert dialog with optional callback
    private void showNotificationAlert(String title, String body, Runnable onDismiss) {
        try {
            if (isFinishing() || isDestroyedCompat()) {
                return; // Don't show dialog if activity is finishing or destroyed
            }
            
            runOnUiThread(() -> {
                try {
                    if (isFinishing() || isDestroyedCompat()) {
                        return; // Double check in case state changed
                    }
                    
                    new AlertDialog.Builder(this)
                            .setTitle(title != null ? title : "Notification")
                            .setMessage(body != null ? body : "")
                            .setCancelable(false) // User must close it
                            .setPositiveButton("OK", (dialog, which) -> {
                                dialog.dismiss();
                                if (onDismiss != null) {
                                    onDismiss.run();
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .show();
                } catch (Exception e) {
                    Log.e("MainActivity", "Error showing notification alert: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e("MainActivity", "Error in showNotificationAlert: " + e.getMessage());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister broadcast receiver
        if (notificationReceiver != null) {
            try {
                unregisterReceiver(notificationReceiver);
            } catch (Exception e) {
                Log.e("MainActivity", "Error unregistering receiver: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onBackPressed() {
        // Handle back button - go back in WebView history if possible
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
