# TeuxDeux API Documentation

This document contains information about TeuxDeux authentication and Todo CRUD operations for this project.

## Authentication

### Login Flow

1. Open WebView to `https://teuxdeux.com/login`
2. User logs in with credentials
3. After successful login, redirects to `https://teuxdeux.com/home`
4. Capture cookies and tokens from the WebView

### Authentication Tokens

#### Primary Access Token
**Cookie Name**: `_txdxtxdx_token`
**Purpose**: Main authentication token used as Bearer token in API requests
**Example**: `YOUR_ACCESS_TOKEN_HERE`

#### Refresh Token
**Cookie Name**: `refresh_token`
**Purpose**: Used to refresh the access token when it expires
**Example**: `YOUR_REFRESH_TOKEN_HERE`

#### Remember Token
**Cookie Name**: `remember_token`
**Purpose**: Persistent login token
**Format**: `{user_id}|{hash}`
**Example**: `127358|HASH_STRING_HERE`

#### Session Cookie
**Cookie Name**: `session`
**Purpose**: Flask session cookie (JWT-encoded)
**Example**: `SESSION_JWT_TOKEN_HERE`

## API Information

### Base URL
```
https://teuxdeux.com/api/v4/
```

### Authentication Header
All API requests require the Bearer token in the Authorization header:
```
Authorization: Bearer {_txdxtxdx_token}
```

### Workspace ID
The user's workspace ID can be extracted from API calls or the home URL.
**Example**: `444459`

## Todo CRUD Operations

### 1. List Todos (READ)

**Endpoint**: `GET /api/v4/workspaces/{workspace_id}/todos`

**Query Parameters**:
- `since` (required): Start date in `YYYY-MM-DD` format
- `until` (required): End date in `YYYY-MM-DD` format

**Headers**:
```
Authorization: Bearer {_txdxtxdx_token}
Accept: application/json
```

**Request Example**:
```bash
curl -X GET "https://teuxdeux.com/api/v4/workspaces/444459/todos?since=2025-10-14&until=2025-10-20" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE" \
  -H "Accept: application/json"
```

**Response Example**:
```json
{
  "todos": [
    {
      "id": 285023215,
      "uuid": "ae241135-6944-4edd-9d8d-3c9172bd9e50",
      "text": "Buy groceries",
      "done": false,
      "current_date": "2025-10-14",
      "position": 0,
      "workspace_id": 444459,
      "list_id": null,
      "recurring_todo_id": null,
      "recurring_todo": null,
      "has_details": false,
      "highlight_color": null,
      "highlight_identifier": null,
      "total_days_swept": 97,
      "created_at": "2025-07-04T04:57:45+0000",
      "updated_at": "2025-07-04T04:57:45+0000",
      "text_updated_at": null,
      "done_updated_at": null,
      "position_updated_at": null,
      "column_updated_at": null,
      "cid": null
    }
  ]
}
```

**Todo Object Fields**:
- `id` (integer): Unique todo ID
- `uuid` (string): UUID for the todo
- `text` (string): The todo text/description
- `done` (boolean): Completion status
- `current_date` (string): The date this todo belongs to (YYYY-MM-DD)
- `position` (integer): Display order position
- `workspace_id` (integer): Workspace ID
- `list_id` (integer|null): List ID if todo is in a list
- `recurring_todo_id` (integer|null): ID of recurring todo template
- `recurring_todo` (object|null): Recurring todo details
- `has_details` (boolean): Whether todo has additional details/notes
- `highlight_color` (string|null): Highlight color code
- `highlight_identifier` (string|null): Highlight identifier
- `total_days_swept` (integer): Number of days this todo has been carried forward
- `created_at` (string): ISO 8601 timestamp
- `updated_at` (string): ISO 8601 timestamp
- `text_updated_at` (string|null): Last text modification timestamp
- `done_updated_at` (string|null): Last completion status change timestamp
- `position_updated_at` (string|null): Last position change timestamp
- `column_updated_at` (string|null): Last column change timestamp
- `cid` (string|null): Client ID (used for optimistic updates)

### 2. Create Todo (CREATE)

**Endpoint**: `POST /api/v4/workspaces/{workspace_id}/todos`

**Headers**:
```
Authorization: Bearer {_txdxtxdx_token}
Content-Type: application/json
Accept: application/json
```

**Request Body Parameters**:
- `text` (required, string): The todo text/description
- `current_date` (required, string): Date in `YYYY-MM-DD` format
- `position` (optional, integer): Position in the list (defaults to end)
- `list_id` (optional, integer): List ID to add the todo to

**Request Example**:
```bash
curl -X POST "https://teuxdeux.com/api/v4/workspaces/444459/todos" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"text":"Buy milk","current_date":"2025-10-14"}'
```

**Response Example**:
```json
{
  "todo": {
    "id": 298256684,
    "uuid": "71e5fc74-dfa8-4d54-b2cd-d7970f27bbe6",
    "text": "Buy milk",
    "done": false,
    "current_date": "2025-10-14",
    "position": 9,
    "workspace_id": 444459,
    "list_id": null,
    "list": null,
    "recurring_todo_id": null,
    "recurring_todo": null,
    "has_details": false,
    "highlight_color": null,
    "highlight_identifier": null,
    "total_days_swept": 0,
    "created_at": "2025-10-14T00:20:29+0000",
    "updated_at": "2025-10-14T00:20:29+0000",
    "text_updated_at": null,
    "done_updated_at": null,
    "position_updated_at": null,
    "column_updated_at": null,
    "cid": null
  }
}
```

### 3. Update Todo (UPDATE)

**Endpoint**: `PATCH /api/v4/workspaces/{workspace_id}/todos/{todo_id}`

**Headers**:
```
Authorization: Bearer {_txdxtxdx_token}
Content-Type: application/json
Accept: application/json
```

**Request Body Parameters** (all optional, send only fields to update):
- `text` (string): Update the todo text
- `position` (integer): Change the position
- `current_date` (string): Move to different date (YYYY-MM-DD)
- `list_id` (integer|null): Move to a different list or remove from list

**Note**: The `done` field cannot be updated via PATCH. Completion is handled separately (endpoint TBD).

**Request Example**:
```bash
curl -X PATCH "https://teuxdeux.com/api/v4/workspaces/444459/todos/298256684" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"text":"Buy organic milk"}'
```

**Response Example**:
```json
{
  "todo": {
    "id": 298256684,
    "uuid": "71e5fc74-dfa8-4d54-b2cd-d7970f27bbe6",
    "text": "Buy organic milk",
    "done": false,
    "current_date": "2025-10-14",
    "position": 9,
    "workspace_id": 444459,
    "list_id": null,
    "recurring_todo_id": null,
    "recurring_todo": null,
    "has_details": false,
    "highlight_color": null,
    "highlight_identifier": null,
    "total_days_swept": 0,
    "created_at": "2025-10-14T00:20:29+0000",
    "updated_at": "2025-10-14T00:20:29+0000",
    "text_updated_at": null,
    "done_updated_at": null,
    "position_updated_at": null,
    "column_updated_at": null,
    "cid": null
  }
}
```

### 4. Delete Todo (DELETE)

**Endpoint**: `DELETE /api/v4/workspaces/{workspace_id}/todos/{todo_id}`

**Headers**:
```
Authorization: Bearer {_txdxtxdx_token}
Accept: application/json
```

**Request Example**:
```bash
curl -X DELETE "https://teuxdeux.com/api/v4/workspaces/444459/todos/298256684" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE" \
  -H "Accept: application/json"
```

**Response**:
- Status: `204 No Content` (empty response body on success)

### 5. Reposition Todo

**Endpoint**: `POST /api/v4/workspaces/{workspace_id}/todos/{todo_id}/reposition`

**Purpose**: Change the position (order) of a todo within a specific date. This is different from the PATCH endpoint - use this for drag-and-drop reordering.

**Headers**:
```
Authorization: Bearer {_txdxtxdx_token}
Content-Type: application/json
Accept: application/json
```

**Request Body Parameters**:
- `current_date` (required, string): The date where the todo should be positioned (YYYY-MM-DD)
- `position` (required, integer): The new position (0 = first, 1 = second, etc.)

**Request Example**:
```bash
curl -X POST "https://teuxdeux.com/api/v4/workspaces/444459/todos/297782583/reposition" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data-raw '{"current_date":"2025-10-14","position":0}'
```

**Response Example**:
```json
{
  "todo": {
    "id": 297782583,
    "uuid": "66757e19-3e60-43cb-88e0-3bba9d015400",
    "text": "airasia",
    "done": false,
    "current_date": "2025-10-14",
    "position": 0,
    "workspace_id": 444459,
    "list_id": null,
    "recurring_todo_id": null,
    "recurring_todo": null,
    "has_details": false,
    "highlight_color": null,
    "highlight_identifier": null,
    "total_days_swept": 4,
    "created_at": "2025-10-09T22:43:28+0000",
    "updated_at": "2025-10-09T22:43:28+0000",
    "text_updated_at": null,
    "done_updated_at": null,
    "position_updated_at": null,
    "column_updated_at": null,
    "cid": null
  }
}
```

**Note**: This endpoint can also be used to move a todo to a different date by changing the `current_date` value.

### 6. Complete/Uncomplete Todo (Toggle State)

**Endpoint**: `POST /api/v4/workspaces/{workspace_id}/todos/{todo_id}/state`

**Purpose**: Toggle the completion status of a todo (mark as done or not done).

**Headers**:
```
Authorization: Bearer {_txdxtxdx_token}
Content-Type: application/json
Accept: application/json
```

**Request Body Parameters**:
- `done` (required, boolean): `true` to mark as complete, `false` to mark as incomplete

**Request Example - Mark as Done**:
```bash
curl -X POST "https://teuxdeux.com/api/v4/workspaces/444459/todos/297782581/state" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data-raw '{"done":true}'
```

**Request Example - Mark as Not Done**:
```bash
curl -X POST "https://teuxdeux.com/api/v4/workspaces/444459/todos/297782581/state" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data-raw '{"done":false}'
```

**Response Example**:
```json
{
  "todo": {
    "id": 297782581,
    "uuid": "66757e19-3e60-43cb-88e0-3bba9d015400",
    "text": "Task description",
    "done": true,
    "current_date": "2025-10-14",
    "position": 0,
    "workspace_id": 444459,
    "list_id": null,
    "recurring_todo_id": null,
    "recurring_todo": null,
    "has_details": false,
    "highlight_color": null,
    "highlight_identifier": null,
    "total_days_swept": 4,
    "created_at": "2025-10-09T22:43:28+0000",
    "updated_at": "2025-10-14T07:50:15+0000",
    "text_updated_at": null,
    "done_updated_at": "2025-10-14T07:50:15+0000",
    "position_updated_at": null,
    "column_updated_at": null,
    "cid": null
  }
}
```

**Note**: This is the correct endpoint for toggling todo completion. The PATCH endpoint does not support updating the `done` field.

## Implementation Notes

### Android WebView Cookie Capture

The app uses `WebViewActivity.kt` to capture authentication tokens:

1. **WebViewClient callbacks**:
   - `onPageStarted()`: Captures cookies when page starts loading
   - `onPageFinished()`: Captures cookies when page finishes loading
   - `shouldInterceptRequest()`: Logs all API calls and request headers

2. **Cookie Manager**:
   ```kotlin
   val cookieManager = CookieManager.getInstance()
   val cookies = cookieManager.getCookie(url)
   ```

3. **Logcat Tag**: `TeuxDeuxDebug`
   - Filter logs: `adb logcat -s TeuxDeuxDebug:D`

### Token Storage

For Android app implementation, store tokens securely using:
- **EncryptedSharedPreferences** for sensitive tokens (_txdxtxdx_token, refresh_token)
- Regular SharedPreferences for non-sensitive data

### Token Refresh

When the access token expires, use the `refresh_token` to obtain a new access token. (Endpoint TBD - monitor network traffic or check API documentation)

## Notes

- The API uses REST with JSON responses
- All endpoints require authentication via Bearer token
- Workspace ID is required for most data operations
- The app redirects from `/login` to `/home` after successful authentication
- User ID can be extracted from the remember_token (first part before the pipe)
- See `teux.additional.md` for additional API endpoints (day labels, notifications, focus modes, etc.)
