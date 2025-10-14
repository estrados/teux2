# KitKat SSL/HTTPS Test Project

Testing SSL/HTTPS workarounds for Android KitKat (API 19) devices that can't make modern API calls due to SSL/TLS compatibility issues.

## Quick Start

### 1. Start the PHP Logging Server

```bash
cd server
php -S 192.168.1.11:8080
```

Then open browser to `http://192.168.1.11:8080` to view logs in real-time (auto-refreshes every 3 seconds).

### 2. Build and Run the Android App

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Or open in Android Studio and run on your KitKat device.

### 3. Test the Methods

The app has 3 buttons to test different SSL/HTTPS approaches:

1. **Method 1: WebView API** - Uses WebView's JavaScript fetch API
2. **Method 2: Custom SSL (OkHttp)** - Custom TrustManager with OkHttp
3. **Method 3: WebView Proxy** - WebView as SSL proxy using XMLHttpRequest

## Implementation Details

### Method 1: WebView API (`WebViewApiHelper.kt`)
- Loads `about:blank` in WebView
- Injects JavaScript to make `fetch()` call to TeuxDeux API
- WebView handles SSL/TLS internally
- Returns results via JavaScript bridge

**Pros:**
- WebView handles SSL better on old devices
- No custom SSL configuration needed

**Cons:**
- Requires JavaScript enabled
- Subject to CORS restrictions (depends on API)

### Method 2: Custom SSL (`CustomSslHelper.kt`)
- Uses OkHttp 3.12.13 (last version supporting API 19)
- Creates custom `TrustManager` that accepts all certificates
- Bypasses hostname verification for testing

**Pros:**
- Direct HTTP client approach
- Full control over SSL configuration

**Cons:**
- May not work if server requires modern TLS
- Not secure (accepts all certificates for testing)

### Method 3: WebView Proxy (`WebViewProxyHelper.kt`)
- Creates HTML page with embedded JavaScript
- Uses `XMLHttpRequest` to make API calls
- WebView acts as SSL proxy

**Pros:**
- Alternative to fetch API
- More compatible with older WebView versions

**Cons:**
- Still subject to same-origin policy
- Requires WebView JavaScript support

## PHP Logging Server

Located in `server/index.php`:

- **POST /**: Receive logs from Android app (JSON format)
- **GET /**: View logs in HTML (auto-refresh)
- **GET /?view=logs**: View raw log file
- **GET /?clear=1**: Clear all logs

Logs are stored in `server/logs.txt`.

## Test API

Using TeuxDeux API endpoint:
```
GET https://teuxdeux.com/api/v4/workspaces/444459/todos?since=2025-10-14&until=2025-10-20
```

**Note:** This endpoint requires authentication. For real testing, you would:
1. Login via WebView to capture auth token
2. Add `Authorization: Bearer {token}` header to requests

See `teuxdeux.md` for complete API documentation.

## Architecture

```
kitkat/
├── app/src/main/java/com/example/kitkat/
│   ├── MainActivity.kt           # Main UI with test buttons
│   ├── LogHelper.kt             # Sends logs to PHP server
│   ├── WebViewApiHelper.kt      # Method 1: WebView fetch API
│   ├── CustomSslHelper.kt       # Method 2: OkHttp custom SSL
│   └── WebViewProxyHelper.kt    # Method 3: WebView XMLHttpRequest
└── server/
    ├── index.php                # Logging server
    └── logs.txt                 # Log file (auto-generated)
```

## Gradle Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Clean build
./gradlew clean

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## Logcat Monitoring

```bash
# Filter by app tag
adb logcat -s KitKatSSL:D

# Filter by specific method
adb logcat | grep WebViewApiHelper
adb logcat | grep CustomSslHelper
adb logcat | grep WebViewProxyHelper
```

## Notes

- Server IP is hardcoded to `192.168.1.11:8080` in `LogHelper.kt`
- To change server IP, update:
  - `LogHelper.kt` - SERVER_URL constant (change to any 192.168.1.* IP)
  - `server/start.sh` - PHP server bind address
- All SSL/TLS bypass methods are for TESTING ONLY - not secure for production
- App uses `usesCleartextTraffic="true"` to allow HTTP to any local server IP
- OkHttp 3.12.13 is the last version supporting Android API 19

## Expected Results

On a KitKat device with SSL issues:

- **Method 1 (WebView)**: Likely to work ✓ (WebView has better SSL support)
- **Method 2 (Custom SSL)**: May work depending on server TLS requirements
- **Method 3 (WebView Proxy)**: Similar to Method 1, likely to work ✓

Check both the app UI logs and the PHP server logs to see detailed results.
