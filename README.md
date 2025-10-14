# KitKat Todo App

Full CRUD Todo application for Android KitKat (API 19) that bypasses SSL/TLS limitations using WebView.

## Problem Solved

**Challenge:** KitKat only supports TLS 1.0, but modern APIs require TLS 1.2+
**Solution:** Use WebView's SSL engine (updated via Google Play Services) to make HTTPS requests

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

### 3. Use the App

The app provides full CRUD operations:

- **List Todos** - Fetch todos for today
- **Create Todo** - Create a new todo item
- **Update Todo** - Update existing todo text
- **Delete Todo** - Delete a todo item
- **Toggle Done** - Mark todo as complete/incomplete
- **Switch Method** - Toggle between Method 1 (Direct) and Method 3 (XHR)

## Architecture

### Unified API Interface (`ApiHelper.kt`)

```kotlin
interface ApiHelper {
    fun request(
        method: String,      // GET, POST, PATCH, DELETE
        url: String,
        headers: Map<String, String>,
        body: String?,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    )
}
```

### Two Implementations

**Method 1: Direct Load (`DirectLoadApiHelper.kt`)**
- Loads API URL directly in WebView with custom headers
- ✅ Simple and fast
- ❌ **Only supports GET** (WebView limitation)
- Speed: ~2000ms

**Method 3: XHR Proxy (`XhrProxyApiHelper.kt`)** ⭐ **RECOMMENDED**
- Uses WebView with XMLHttpRequest in JavaScript
- ✅ **Supports all HTTP methods** (GET, POST, PATCH, DELETE)
- ✅ Can send request body
- ✅ CORS-compatible (uses base URL)
- ✅ **Fastest** (~800ms)
- Speed: ~800ms

### TodoApi Wrapper (`ApiHelper.kt`)

High-level API for common operations:
```kotlin
val todoApi = TodoApi(apiHelper)

todoApi.listTodos(workspaceId, since, until) { ... }
todoApi.createTodo(workspaceId, text, date) { ... }
todoApi.updateTodo(workspaceId, todoId, text) { ... }
todoApi.deleteTodo(workspaceId, todoId) { ... }
todoApi.toggleTodoDone(workspaceId, todoId, done) { ... }
```

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

## Project Structure

```
kitkat/
├── app/src/main/java/com/example/kitkat/
│   ├── MainActivity.kt              # Main UI with CRUD buttons
│   ├── Config.kt                    # API configuration and auth token
│   ├── ApiHelper.kt                 # Unified API interface + TodoApi wrapper
│   ├── DirectLoadApiHelper.kt      # Method 1: Direct URL load (GET only)
│   ├── XhrProxyApiHelper.kt        # Method 3: XHR proxy (all methods)
│   └── LogHelper.kt                # Sends logs to PHP server
└── server/
    ├── index.php                   # Logging server
    └── logs.txt                    # Log file (auto-generated)
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

## Test Results on KitKat

| Method | Status | Speed | Supports |
|--------|--------|-------|----------|
| **Method 1 (Direct)** | ✅ Works | ~2000ms | GET only |
| **Method 3 (XHR)** | ✅ Works | **~800ms** ⚡ | All HTTP methods |
| Method 2 (OkHttp) | ❌ Fails | N/A | TLS incompatible (removed) |

**Both WebView methods successfully bypass KitKat's TLS 1.0 limitation!**

## Usage Example

```kotlin
// Create API helper
val apiHelper = ApiHelperFactory.create(context, ApiHelperFactory.Method.XHR_PROXY)
val todoApi = TodoApi(apiHelper)

// List todos
todoApi.listTodos(444459, "2025-10-14", "2025-10-14") { success, statusCode, response, time ->
    if (success) {
        println("Got todos: $response")
    }
}

// Create todo
todoApi.createTodo(444459, "Buy milk", "2025-10-14") { success, statusCode, response, time ->
    if (success) {
        println("Created: $response")
    }
}
```
