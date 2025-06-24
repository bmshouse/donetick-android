# Chore Notification System

This document explains the Android notification system implemented for DoneTick chores.

## Overview

The notification system automatically schedules Android notifications for chores that have `notification: true` in their JSON data. Notifications are scheduled based on the `nextDueDate` field and are automatically updated whenever new chores data is received from the WebView.

## Features

### ✅ Automatic Scheduling
- Notifications are automatically scheduled when chores data is loaded
- Only chores with `notification: true` and `isActive: true` get notifications
- Notifications are scheduled for the exact `nextDueDate` time

### ✅ Smart Management
- **Add**: New chores with notifications enabled get scheduled
- **Update**: Changed due dates reschedule existing notifications
- **Remove**: Inactive chores or chores with `notification: false` cancel their notifications
- **Reschedule**: All notifications are refreshed when new data is received

### ✅ Permission Handling
- Automatically requests notification permission on Android 13+
- Gracefully handles permission denial
- Re-schedules notifications when permission is granted

### ✅ Immediate Notifications
- Shows immediate notifications for overdue chores
- Handles chores that are already past their due date

## Implementation Details

### Core Components

1. **ChoreNotificationManager** - Main service for managing notifications
2. **ChoreNotificationReceiver** - BroadcastReceiver for showing scheduled notifications
3. **NotificationPermissionHelper** - Utility for handling permissions
4. **Updated ChoreItem** - Enhanced data model with notification fields

### Data Structure

The system expects chores with this structure:
```json
{
  "id": 5,
  "name": "Brush Teeth",
  "nextDueDate": "2025-06-24T03:00:00Z",
  "notification": true,
  "notificationMetadata": {
    "dueDate": true
  },
  "isActive": true
}
```

### Notification Flow

1. **Data Capture**: WebView captures chores JSON from API (only from list endpoints, not individual actions)
2. **Parsing**: JSON is parsed into ChoreItem objects
3. **Filtering**: Only chores with `notification: true` and `isActive: true` are processed
4. **Scheduling**: AlarmManager schedules exact alarms for due dates
5. **Individual Actions**: When chores are marked done via `/api/v1/chores/:id/do`, only that specific notification is cancelled
6. **Display**: BroadcastReceiver shows notifications when triggered

### Permission Requirements

Added to AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## Usage

### For Users
1. Open the DoneTick app
2. Grant notification permission when prompted
3. Let the WebView load chores data
4. Notifications will automatically be scheduled for chores with notifications enabled
5. Receive notifications at the exact due time

### For Developers
The system is fully automatic once implemented. Key integration points:

```kotlin
// In WebViewViewModel - automatically called when chores data is received
fun handleChoresData(jsonData: String) {
    val choresList = parseChoresJson(jsonData)
    scheduleChoreNotifications(choresList) // Notifications scheduled here
}

// In WebViewViewModel - called when individual chore is marked done
fun handleChoreMarkedDone(choreId: Int) {
    choreNotificationManager.cancelChoreNotification(choreId) // Only cancel this chore's notification
}
```

## UI Enhancements

### Chores List Screen
- Shows notification bell icon for chores with notifications enabled
- Displays formatted due dates
- Shows frequency information
- Indicates notification status

### WebView Screen
- Floating action button appears when chores are available
- Bottom sheet shows native chores list
- Real-time updates when new data is captured

## Technical Details

### Notification Channel
- **ID**: `chore_notifications`
- **Name**: "Chore Reminders"
- **Importance**: Default
- **Features**: Vibration, badge, sound

### Alarm Scheduling
- Uses `AlarmManager.setExactAndAllowWhileIdle()` for precise timing
- Handles device sleep/doze mode
- Unique PendingIntent for each chore (based on chore ID)

### Error Handling
- Graceful handling of permission denial
- Logging for debugging notification issues
- Fallback for malformed date strings
- Safe parsing of JSON data

## Recent Fixes

### Notification Preservation Fix

**Problem**: When a chore was marked as done via `/api/v1/chores/:id/do`, all notifications would be cleared instead of just removing the completed chore's notification.

**Root Cause**: The WebView JavaScript interceptor was capturing all `/api/v1/chores` API calls, including individual chore actions like `/do`. This caused the notification system to treat the response as a full chores list update, leading to:
1. Cancellation of all existing notifications
2. Rescheduling only for chores in the response (often empty or just the completed chore)
3. Loss of all other pending notifications

**Solution**:
- Modified JavaScript interceptor to differentiate between chores list calls and individual actions
- Added separate handling for `/api/v1/chores/:id/do` calls that only cancels the specific chore's notification
- Preserved existing notifications for all other chores

**Implementation**:
```kotlin
// New method to handle individual chore completion
fun handleChoreMarkedDone(choreId: Int) {
    choreNotificationManager.cancelChoreNotification(choreId)
}
```

**Result**: Notifications now persist correctly when chores are marked as done, with only the completed chore's notification being removed.

## Future Enhancements

### Planned Features
- [ ] Custom notification timing (e.g., 30 minutes before due)
- [ ] Recurring notifications for overdue chores
- [ ] Rich notifications with action buttons (Mark Complete, Snooze)
- [ ] Notification grouping for multiple chores
- [ ] Custom notification sounds per chore priority

### Advanced Options
- [ ] Notification preferences in settings
- [ ] Quiet hours configuration
- [ ] Priority-based notification styling
- [ ] Integration with system Do Not Disturb

## Testing

### Test Scenarios
1. **New Installation**: Verify permission request flow
2. **Permission Denied**: Ensure graceful handling
3. **Permission Granted Later**: Verify notifications are scheduled
4. **Overdue Chores**: Check immediate notification display
5. **Data Updates**: Confirm notifications are rescheduled
6. **App Restart**: Verify notifications persist

### Debug Information
- All notification operations are logged with tag `ChoreNotificationManager`
- Check Android's notification settings to verify channel creation
- Use `adb shell dumpsys alarm` to inspect scheduled alarms

## Troubleshooting

### Common Issues
1. **No Notifications**: Check notification permission and channel settings
2. **Wrong Timing**: Verify device timezone and date parsing
3. **Duplicate Notifications**: Ensure proper cancellation of old alarms
4. **Battery Optimization**: May need to whitelist app for exact alarms

### Debug Commands
```bash
# Check scheduled alarms
adb shell dumpsys alarm | grep com.donetick.app

# Check notification channels
adb shell cmd notification list_channels com.donetick.app

# Test notification permission
adb shell cmd notification post -S bigtext -t "Test" com.donetick.app 1
```
