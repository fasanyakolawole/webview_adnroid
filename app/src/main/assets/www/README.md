# Vue.js Dist Files Location

Place your Vue.js dist files in this directory (`app/src/main/assets/www/`).

## Instructions:

1. Copy your Vue.js dist files to this directory:
   - `index.html` (replace the existing placeholder)
   - All CSS files (typically in a `css/` folder)
   - All JS files (typically in a `js/` folder)
   - Any other assets (images, fonts, etc.)

2. Make sure your `index.html` file references the CSS and JS files with relative paths:
   ```html
   <link rel="stylesheet" href="css/app.css">
   <script src="js/app.js"></script>
   ```

3. After placing your files, rebuild the Android app.

## Directory Structure:
```
app/src/main/assets/www/
├── index.html          (your Vue app's index.html)
├── css/                (your CSS files)
│   └── app.css
├── js/                 (your JS files)
│   └── app.js
└── assets/             (other assets like images, fonts, etc.)
```

The WebView will automatically load `index.html` when the app starts.

## JavaScript Interface Functions

You can call native Android functions from your Vue.js app using the `Android` object:

### Toast Messages
```javascript
// Show a short toast message
Android.showToast('Hello from Vue!');

// Show a long toast message
Android.showToastLong('This is a longer message');
```

### Alert Dialogs
```javascript
// Show a simple alert dialog
Android.showAlert('Title', 'This is the message');

// Show an alert with callback when dismissed
window.onAlertDismiss = function() {
    console.log('Alert was dismissed');
    // Your callback code here
};
Android.showAlertWithCallback('Title', 'Message');
```

### Vibration
```javascript
// Vibrate for a specific duration (in milliseconds)
Android.vibrate(200); // Vibrate for 200ms

// Vibrate with a pattern
// pattern: array of durations [wait, vibrate, wait, vibrate, ...]
// repeat: index to repeat from, or -1 to play once
Android.vibratePattern([0, 100, 50, 100], -1); // Double tap pattern
```

### Example Usage in Vue
```javascript
// In your Vue component
methods: {
    handleButtonClick() {
        if (window.Android) {
            Android.vibrate(200); // Vibrate when button is clicked
            Android.showToast('Button clicked!');
        }
    },
    
    showError(message) {
        if (window.Android) {
            Android.vibrate(400); // Long vibration for errors
            Android.showAlert('Error', message);
        }
    },
    
    onButtonPress() {
        // Simple vibration on button press
        if (window.Android) {
            Android.vibrate(100);
        }
    }
}
```

See `js-example.js` for more detailed examples and helper functions.
