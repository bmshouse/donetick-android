# WebView API Data Capture Implementation

This document explains how the DoneTick Android app captures JSON data from API calls made by the website loaded in the WebView.

## Overview

The implementation allows the Android app to intercept and capture JSON responses from the `/api/v1/chores` (or `/api/v1/chores`) API endpoint that the donetick website calls. This captured data can then be used to add native Android functionality while still using the WebView for the main interface.

## How It Works

### 1. JavaScript Interface Bridge

The app creates a JavaScript interface that allows the WebView's JavaScript to communicate with the Android app:

```kotlin
// In WebViewActivity.kt
webView.addJavascriptInterface(ApiDataCapture(), "AndroidApiCapture")

inner class ApiDataCapture {
    @android.webkit.JavascriptInterface
    fun onChoresDataReceived(jsonData: String) {
        runOnUiThread {
            viewModel.handleChoresData(jsonData)
        }
    }

    @android.webkit.JavascriptInterface
    fun onChoreMarkedDone(choreId: Int) {
        runOnUiThread {
            viewModel.handleChoreMarkedDone(choreId)
        }
    }
}
```

### 2. JavaScript Injection

When the WebView page finishes loading, the app injects JavaScript code that:
- Intercepts `fetch()` API calls
- Intercepts `XMLHttpRequest` calls
- Checks if the URL contains `/api/v1/chores`
- Differentiates between chores list calls and individual chore actions
- Captures the JSON response for list calls and sends it to the Android app
- Handles individual chore actions (like marking done) separately

```javascript
// Injected JavaScript (simplified)
window.fetch = function(...args) {
    const url = args[0];

    return originalFetch.apply(this, args)
        .then(response => {
            // Only capture the exact chores list API call (not history, labels, etc.)
            if (url.match(/\/api\/v1\/chores\/?(\?.*)?$/)) {
                const clonedResponse = response.clone();
                clonedResponse.json().then(data => {
                    AndroidApiCapture.onChoresDataReceived(JSON.stringify(data));
                });
            }
            // Handle chore "do" actions separately
            else if (url.includes('/do') && url.match(/\/api\/v1\/chores\/\d+\/do/)) {
                const choreIdMatch = url.match(/\/api\/v1\/chores\/(\d+)\/do/);
                if (choreIdMatch && choreIdMatch[1]) {
                    AndroidApiCapture.onChoreMarkedDone(parseInt(choreIdMatch[1]));
                }
            }
            return response;
        });
};
```

### 3. Data Processing

The Android app processes the captured JSON data:

```kotlin
// In WebViewViewModel.kt
fun handleChoresData(jsonData: String) {
    val choresList = parseChoresJson(jsonData)
    _uiState.value = _uiState.value.copy(
        choresData = jsonData,
        choresList = choresList
    )
}
```

### 4. UI Integration

The captured data is displayed in the UI:
- A floating action button appears when chores data is available
- **Swipe-based navigation** between WebView and chores list
- **HorizontalPager** provides smooth transitions between screens
- **Page indicators** show current screen position
- The chores are displayed in a native Android UI with Material Design 3

## Data Structure

The app expects the chores API to return a JSON array with objects containing:

```json
[
  {
    "id": 1,
    "name": "Take out trash",
    "assigned_to": "John",
    "due_date": "2024-01-15",
    "is_completed": false,
    "frequency": "weekly",
    "description": "Take out the kitchen trash"
  }
]
```

## Notification Management

### Smart API Filtering

The implementation now intelligently filters API calls to prevent notification issues:

- **Chores List Calls**: URLs like `/api/v1/chores` (without action suffixes) trigger full chores data processing and notification rescheduling
- **Individual Actions**: URLs like `/api/v1/chores/:id/do`, `/api/v1/chores/:id/skip`, `/api/v1/chores/:id/update` are handled separately
- **Chore Done Action**: When `/api/v1/chores/:id/do` is called, only the specific chore's notification is cancelled, preserving other notifications

## Benefits

1. **Hybrid Approach**: Keep using WebView for the main interface while adding native features
2. **Real-time Data**: Capture data as it's loaded by the website
3. **Native UI**: Display captured data using native Android components with smooth swipe navigation
4. **Intuitive UX**: Familiar swipe gestures for seamless navigation between web and native views
5. **Extensible**: Easy to add more API endpoints or data types

## Usage

1. Open the DoneTick app
2. The WebView loads the donetick server website
3. When the website makes API calls to fetch chores, the data is automatically captured
4. A floating action button with a list icon appears when chores data is available
5. **Swipe left** to view the chores in a native Android interface
6. **Swipe right** or **tap the back arrow** to return to the WebView
7. Visual page indicators at the bottom show which screen you're on

## Future Enhancements

- Add support for more API endpoints (users, tasks, etc.)
- Implement offline caching of captured data
