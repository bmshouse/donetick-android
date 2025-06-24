# DoneTick Android Application

A Kotlin Android application that serves as a thin wrapper for DoneTick server instances, providing a native mobile interface through WebView integration.

## Features

- **Initial Setup Screen**: Configure DoneTick server URL on first launch with validation
- **URL Validation**: Comprehensive server URL validation and connectivity testing
- **WebView Integration**: Full DoneTick server interface through WebView with JavaScript support
- **Secure Storage**: Server URL stored securely using EncryptedSharedPreferences
- **Settings Management**: Change server URL and manage configuration with confirmation dialogs
- **Material Design 3**: Modern UI following Material Design guidelines with dynamic theming
- **MVVM Architecture**: Clean architecture with ViewModels and StateFlow for reactive updates
- **Error Handling**: Comprehensive error handling with user-friendly messages
- **Network Awareness**: Network connectivity checks and appropriate error messages
- **Back Navigation**: Proper WebView back navigation and activity management

## Architecture

- **MVVM Pattern**: ViewModels manage UI state and business logic
- **Dependency Injection**: Hilt for dependency management and testability
- **Repository Pattern**: Clean separation of data access with secure preferences
- **StateFlow**: Reactive UI updates and state management
- **Clean Architecture**: Separation of concerns across data, domain, and UI layers
- **Use Cases**: Domain-specific business logic encapsulation

## Technology Stack

- **Kotlin**: Primary development language with coroutines
- **Jetpack Compose**: Modern declarative UI toolkit
- **Material Design 3**: UI design system with dynamic colors
- **Hilt**: Dependency injection framework
- **EncryptedSharedPreferences**: Secure local storage for sensitive data
- **WebView**: DoneTick server interface with enhanced configuration
- **StateFlow**: Reactive state management
- **JUnit & Mockito**: Unit testing framework

## Project Structure

```
app/
├── src/main/java/com/donetick/app/
│   ├── data/
│   │   ├── model/          # Data models (ServerConfig)
│   │   ├── preferences/    # Secure preferences management
│   │   └── repository/     # Repository implementations
│   ├── domain/
│   │   └── usecase/        # Business logic use cases
│   ├── ui/
│   │   ├── components/     # Reusable UI components
│   │   ├── setup/          # Setup screen implementation
│   │   ├── webview/        # WebView screen implementation
│   │   ├── settings/       # Settings screen implementation
│   │   └── theme/          # Material Design 3 theming
│   ├── di/                 # Dependency injection modules
│   └── utils/              # Utility classes (ErrorHandler, NetworkUtils)
├── src/main/res/           # Resources (layouts, strings, themes, icons)
├── src/test/java/          # Unit tests
└── build.gradle.kts        # App-level build configuration
```

## Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK API 24+ (Android 7.0)
- Kotlin 1.9.20+

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
4. **Settings**: Access settings through the menu to change server URL or disconnect
5. **Navigation**: Use the back button to navigate within the WebView or return to previous screens

## Configuration

The app stores the server configuration securely using Android's EncryptedSharedPreferences. The configuration includes:
- Server URL (encrypted)
- Configuration status
- Last validation timestamp

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

Run tests with: `./gradlew test`

## Requirements

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Internet Permission**: Required for server communication
- **Network State Permission**: For connectivity checks
- **Valid DoneTick Server**: The app requires a running DoneTick server instance

## Contributing

1. Follow the existing code style and architecture patterns
2. Add unit tests for new functionality
3. Update documentation as needed
4. Test on multiple Android versions and screen sizes

## License

This project is licensed under the MIT License - see the LICENSE file for details.
