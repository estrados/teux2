# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android KitKat (API 19+) compatibility project for testing SSL/HTTPS workarounds with the TeuxDeux API. This is a fresh project where all code can be replaced as needed.

## Key Problem & Goals

**Problem**: Old KitKat devices cannot make modern HTTPS API calls due to SSL/TLS compatibility issues.

**Solutions to Test**:
1. Custom SSL certificate handling (bypass modern TLS requirements)
2. WebView-based API calls (WebView handles SSL internally)

**Target API**: TeuxDeux API (see `teuxdeux.md` for complete documentation)

## Build Configuration

```bash
# Build the project
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Clean build
./gradlew clean

# Run tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Project Structure

- **Min SDK**: 19 (Android 4.4 KitKat)
- **Target SDK**: 36
- **Package**: `com.example.kitkat`
- **Build System**: Gradle with Kotlin DSL
- **Language**: Kotlin (JVM target 11)

## Key Files

- `teuxdeux.md` - Complete TeuxDeux API documentation including:
  - Authentication flow (WebView login to capture tokens)
  - Todo CRUD endpoints (GET, POST, PATCH, DELETE)
  - Required headers and authentication tokens
  - Example workspace ID: 444459

## Implementation Approach

### Authentication Strategy
Use WebView to handle login and capture cookies:
- Load `https://teuxdeux.com/login` in WebView
- Capture `_txdxtxdx_token` cookie after successful login
- Use token as Bearer token for API requests

### API Testing Priority
Focus on one endpoint first (e.g., GET todos):
```
GET https://teuxdeux.com/api/v4/workspaces/{workspace_id}/todos?since=YYYY-MM-DD&until=YYYY-MM-DD
Authorization: Bearer {_txdxtxdx_token}
```

### SSL/TLS Workarounds for KitKat

Two approaches to test:

**Option 1: Custom SSL Configuration**
- Implement custom TrustManager/SSLSocketFactory
- May need to support legacy cipher suites
- Use OkHttp with custom SSL configuration

**Option 2: WebView API Bridge**
- Use WebView's built-in SSL handling
- Inject JavaScript to make API calls
- Extract results via JavaScript bridge

## Android Manifest Requirements

Add permissions:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Enable cleartext traffic (for HTTP logging server on local network):
```xml
android:usesCleartextTraffic="true"
```

**Note:** `networkSecurityConfig` is not available on API 19 (added in API 24), so we use `usesCleartextTraffic` instead

## Debugging

For WebView debugging:
```kotlin
// Enable WebView debugging
WebView.setWebContentsDebuggingEnabled(true)

// Log tag for TeuxDeux debugging
Log.d("TeuxDeuxDebug", message)
```

Filter logcat:
```bash
adb logcat -s TeuxDeuxDebug:D
```

## Dependencies

Currently minimal. May need to add:
- OkHttp (for custom SSL handling)
- Gson/Moshi (for JSON parsing)
- EncryptedSharedPreferences backport (for secure token storage on KitKat)
