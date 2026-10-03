# DoneTick Android Application

A Kotlin Android application that serves as a thin wrapper for DoneTick server instances, providing a native mobile interface through WebView integration. Unlike the official DoneTick app, it works over plain HTTP — no SSL/TLS certificate required.

## Features

- **Initial Setup Screen**: Configure DoneTick server URL on first launch, with validation and connectivity testing
- **WebView Integration**: Full DoneTick server interface through WebView with JavaScript support
- **Chores Management**: Dedicated chores list view with native Android notification scheduling
- **API Interception**: Automatic capture of chores data from DoneTick server API calls
- **Session Keep-Alive**: Warns 5 days before your login expires and refreshes the token when you open the app, so chore reminders don't silently stop (see [Login / Session Expiration](#login--session-expiration))
- **Secure Storage**: Server URL stored using Android Keystore-backed encryption
- **Settings Management**: Change server URL or reset configuration with confirmation dialogs
- **No SSL/TLS Required**: Connects to self-hosted servers over plain HTTP, unlike the official DoneTick app

See [Architecture](#architecture) and [Technology Stack](#technology-stack) below for implementation details.

## Architecture

- **MVVM Pattern**: ViewModels manage UI state and business logic
- **Two-View Architecture**: Separate activities for WebView and ChoresList for clean navigation
- **Dependency Injection**: Hilt for dependency management and testability
- **Repository Pattern**: Clean separation of data access with secure preferences
- **StateFlow**: Reactive UI updates and state management
- **Clean Architecture**: Separation of concerns across data, domain, and UI layers
- **Use Cases**: Domain-specific business logic encapsulation

### Navigation Architecture

The app uses a two-activity architecture:

1. **WebViewActivity**: Main activity hosting the DoneTick server interface
   - Handles WebView configuration and JavaScript injection
   - Captures API data for chores and notifications
   - Provides button-based navigation to ChoresListActivity

2. **ChoresListActivity**: Dedicated activity for chores management
   - Displays chores with notification status
   - Receives chores data via Intent extras
   - Standard back navigation to WebViewActivity

## Technology Stack

- **Kotlin**: Primary development language with coroutines
- **Jetpack Compose**: Modern declarative UI toolkit
- **Material Design 3**: UI design system with dynamic colors
- **Hilt**: Dependency injection framework
- **Android Keystore**: Secure, encrypted local storage for sensitive data
- **WebView**: DoneTick server interface with enhanced configuration
- **StateFlow**: Reactive state management
- **JUnit & Mockito**: Unit testing framework

## Project Structure

```
app/
├── src/main/java/org/chaosorderx/donetick/
│   ├── data/
│   │   ├── model/          # Data models (ServerConfig)
│   │   ├── preferences/    # Secure preferences management
│   │   └── repository/     # Repository implementations
│   ├── domain/
│   │   └── usecase/        # Business logic use cases
│   ├── ui/
│   │   ├── components/     # Reusable UI components
│   │   ├── setup/          # Setup screen implementation
│   │   ├── webview/        # WebView and ChoresList screen implementations
│   │   ├── settings/       # Settings screen implementation
│   │   └── theme/          # Material Design 3 theming
│   ├── notification/       # Chore reminders and session-expiry warning
│   ├── di/                 # Dependency injection modules
│   └── utils/              # Utility classes (ErrorHandler, NetworkUtils)
├── src/main/res/           # Resources (layouts, strings, themes, icons)
├── src/test/java/          # Unit tests
└── build.gradle.kts        # App-level build configuration
```

## Getting Started

### Prerequisites
- Android Studio (latest stable release recommended)
- Android SDK API 24+ (Android 7.0)
- Kotlin 2.2.0+

### Setup Instructions
1. Clone the repository
2. Open the project in Android Studio
3. Sync the project with Gradle files
4. Build and run the application on a device or emulator
5. On first launch, enter your DoneTick server URL (e.g., `https://your-donetick-server.com`)

### Building
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

## Usage

1. **First Launch**: Enter your DoneTick server URL in the setup screen
2. **URL Validation**: The app validates the URL format and tests connectivity
3. **WebView Interface**: Access the full DoneTick web interface through the integrated WebView
4. **Chores Management**: Tap the chores button to view upcoming chores and notifications
5. **Settings**: Access settings through the menu to change server URL or disconnect
6. **Navigation**: Use standard Android navigation between WebView and ChoresList activities

## Configuration

The app stores the server configuration securely using Android Keystore-backed encryption. The configuration includes:
- Server URL (encrypted)
- Configuration status
- Last validation timestamp

### Login / Session Expiration

#### What

The app keeps your DoneTick login from lapsing silently:

- **Warning notification**: 5 days before your access token expires, the app posts a "Donetick login expiring soon" notification.
- **Refresh on open**: opening the app inside that 5-day window calls the server's `POST /api/v1/auth/refresh`. The new token is stored, the expiry moves out, and a fresh warning is scheduled for 5 days before the *new* expiry. Keep opening the app and the login never lapses.
- **If refresh isn't possible** (see below), the notification says "Log in again before <date>" instead of "Open the app", since opening the app can't help.

#### Why

Chore reminders are local alarms built from data the app syncs with the DoneTick token. Once the token expires the sync gets a 401, no new alarms are scheduled, and reminders stop with no sign anything is wrong.

The web client can't fix this itself. `/auth/refresh` needs the `refresh_token` in the request body, and the login response does include one, but the web client (non-Capacitor) discards it, so its own refresh always fails with a 400 and the session just ends. The app's injected JS hook keeps the `refresh_token` from `/auth/` responses in `localStorage` (and removes it on logout) so the app can refresh on your behalf. Refresh tokens rotate: every refresh returns a new one, which is stored in place of the old one.

#### Older DoneTick servers

Refresh depends on the server issuing a refresh token at login and exposing `/auth/refresh`. The app degrades safely when it doesn't:

| Situation | Behavior |
| --- | --- |
| Server issues no `refresh_token` at login (older DoneTick) | No refresh is ever attempted. The warning says **Log in again** and still fires 5 days before expiry. Logging in again renews the token and re-arms the warning. |
| `/auth/refresh` returns 404 / 405 / 501 | Treated as unsupported: same as above, no further refresh attempts. |
| Refresh rejected or fails (400/401, network error, timeout) | Retried the next time the app opens. The warning shows meanwhile. |
| Already logged in before updating the app | No refresh token was captured, so log in once more. After that, refresh works. |

Servers too old to use the `token` / `token_expiry` `localStorage` keys are not covered: the app can't read a token there, so native sync (and this feature) stays idle.

#### Limitations

- The warning is a scheduled alarm, and Android clears alarms on reboot. After a restart it's set again the next time you open the app.
- The alarm is inexact (`setAndAllowWhileIdle`), so it can arrive a little late. This needs no exact-alarm permission.
- Notification permission (Android 13+) is required for the warning to show.

#### Server settings

The access-token lifetime is set by your DoneTick **server**, not the app. If you'd rather log in less often, raise these on the server (`config.yaml` or `DT_`-prefixed env vars):

```yaml
jwt:
  session_time: 168h   # DT_JWT_SESSION_TIME — access token lifetime before re-auth is required
  max_refresh: 1440h   # DT_JWT_MAX_REFRESH — max time a session can silently refresh
```

Restart the server after changing them.

## Error Handling

The app provides comprehensive error handling for:
- Network connectivity issues
- Invalid URL formats
- Server reachability
- SSL/TLS connection problems
- Timeout scenarios

## Testing

The project includes unit tests for:
- Data models and validation logic
- Use cases and business logic
- Error handling scenarios
- URL validation functionality

## Requirements

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 35 (Android 15)
- **Internet Permission**: Required for server communication
- **Network State Permission**: For connectivity checks
- **Valid DoneTick Server**: The app requires a running DoneTick server instance (HTTP or HTTPS — no certificate needed)

## Contributing

1. Follow the existing code style and architecture patterns
2. Add unit tests for new functionality
3. Update documentation as needed
4. Test on multiple Android versions and screen sizes

## License

This project is licensed under the MIT License - see the LICENSE file for details.
