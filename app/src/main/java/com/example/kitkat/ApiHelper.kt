package com.example.kitkat

import android.content.Context
import org.json.JSONObject

/**
 * Unified API Helper that works on KitKat by using WebView to bypass SSL/TLS limitations
 */
interface ApiHelper {
    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    )
}

/**
 * Factory to create API helpers
 */
object ApiHelperFactory {
    fun create(context: Context): ApiHelper {
        // Use WebView XHR proxy on KitKat (API 19) where TLS is problematic; otherwise use OkHttp
        return if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.KITKAT) {
            XhrProxyApiHelper(context)
        } else {
            OkHttpApiHelper(context)
        }
    }
}

/**
 * Common Todo API operations with offline support
 */
class TodoApi(
    private val apiHelper: ApiHelper,
    private val databaseHelper: DatabaseHelper? = null,
    private val offlineManager: OfflineManager? = null
) {

    fun listTodos(
        workspaceId: Int,
        since: String,
        until: String,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        // If offline, return cached todos from database
        if (offlineManager?.isOnline() == false && databaseHelper != null) {
            LogHelper.logInfo("TodoAPI", "Loading todos from local database (offline)")
            val localTodos = databaseHelper.getTodosInRange(since, until)
            val jsonResponse = buildJsonResponse(localTodos)
            callback(true, 200, jsonResponse, 0)
            return
        }

        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos?since=$since&until=$until"
        val headers = mapOf("Authorization" to Config.AUTH_TOKEN)

        LogHelper.logInfo("TodoAPI", "Listing todos: $since to $until")
        apiHelper.request("GET", url, headers, null) { success, statusCode, response, responseTime ->
            // Cache response in database if online and successful
            if (success && databaseHelper != null) {
                cacheServerTodos(response)
            }
            callback(success, statusCode, response, responseTime)
        }
    }

    fun createTodo(
        workspaceId: Int,
        text: String,
        currentDate: String,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val body = """{"text":"$text","current_date":"$currentDate"}"""

        // Save to local database immediately
        val localId = databaseHelper?.saveTodo(null, text, false, currentDate, 1)

        // If offline, queue operation and return success
        if (offlineManager?.isOnline() == false) {
            LogHelper.logInfo("TodoAPI", "Creating todo offline: $text")
            databaseHelper?.queueOperation(OfflineManager.OP_CREATE, null, localId, body)
            // Use localId as the ID (it represents the local database ID for offline-created todos)
            val fakeResponse = """{"id":${localId?.toInt() ?: 0},"text":"$text","current_date":"$currentDate","done":false}"""
            callback(true, 201, fakeResponse, 0)
            return
        }

        // Online - execute API call
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos"
        val headers = mapOf(
            "Authorization" to Config.AUTH_TOKEN,
            "Content-Type" to "application/json"
        )

        LogHelper.logInfo("TodoAPI", "Creating todo: $text")
        apiHelper.request("POST", url, headers, body) { success, statusCode, response, responseTime ->
            if (success && localId != null) {
                // Update local database with server ID
                try {
                    val responseJson = JSONObject(response)
                    // TeuxDeux wraps the todo in a "todo" object
                    val todoJson = if (responseJson.has("todo")) {
                        responseJson.getJSONObject("todo")
                    } else {
                        responseJson
                    }
                    val serverId = todoJson.getInt("id")

                    // Update any pending operations that reference this local ID
                    if (offlineManager != null) {
                        databaseHelper?.updateOperationTodoIds(localId, serverId)
                    }

                    // Delete old local-only entry to prevent duplicates
                    databaseHelper?.deleteTodoByLocalId(localId)
                    // Save with server ID
                    databaseHelper?.saveTodo(serverId, text, false, currentDate, 0)
                    LogHelper.logInfo("TodoAPI", "Created todo with server ID: $serverId (replaced local ID: $localId, updated pending ops)")
                } catch (e: Exception) {
                    LogHelper.logError("TodoAPI", "Failed to parse create response: ${e.message}", 0, null)
                }
            }
            callback(success, statusCode, response, responseTime)
        }
    }

    fun updateTodo(
        workspaceId: Int,
        todoId: Int,
        text: String,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val body = """{"text":"$text"}"""

        // Update local database immediately (use getTodoById to handle both server and local IDs)
        LogHelper.logInfo("TodoAPI", "updateTodo called with todoId=$todoId")
        val localTodo = databaseHelper?.getTodoById(todoId)
        LogHelper.logInfo("TodoAPI", "getTodoById($todoId) returned: ${if (localTodo != null) "localId=${localTodo.localId}, serverId=${localTodo.serverId}" else "NULL"}")

        if (localTodo != null) {
            // Pass localId to prevent creating duplicates when updating offline-created todos
            databaseHelper.saveTodo(localTodo.serverId, text, localTodo.done, localTodo.date, 1, localTodo.localId)
        }

        // If offline, queue operation and return success
        if (offlineManager?.isOnline() == false) {
            LogHelper.logInfo("TodoAPI", "Updating todo offline: #$todoId | local_todo_id=${localTodo?.localId} | server_id=${localTodo?.serverId}")
            LogHelper.logInfo("TodoAPI", "DEBUG: todoId passed to updateTodo = $todoId")
            LogHelper.logInfo("TodoAPI", "DEBUG: localTodo is ${if (localTodo == null) "NULL" else "NOT NULL (localId=${localTodo.localId}, serverId=${localTodo.serverId})"}")

            // Add debug info to payload for later analysis
            val debugPayload = """{"text":"$text","_debug_todoId":$todoId,"_debug_localTodoFound":${localTodo != null},"_debug_localId":${localTodo?.localId},"_debug_serverId":${localTodo?.serverId}}"""
            databaseHelper?.queueOperation(OfflineManager.OP_UPDATE, localTodo?.serverId ?: 0, localTodo?.localId, debugPayload)
            callback(true, 200, """{"id":$todoId,"text":"$text"}""", 0)
            return
        }

        // Online - execute API call
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId"
        val headers = mapOf(
            "Authorization" to Config.AUTH_TOKEN,
            "Content-Type" to "application/json"
        )

        LogHelper.logInfo("TodoAPI", "Updating todo #$todoId: $text")
        apiHelper.request("PATCH", url, headers, body) { success, statusCode, response, responseTime ->
            if (success) {
                databaseHelper?.saveTodo(todoId, text, localTodo?.done ?: false, localTodo?.date ?: "", 0)
            }
            callback(success, statusCode, response, responseTime)
        }
    }

    fun deleteTodo(
        workspaceId: Int,
        todoId: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        // Get local todo before deleting (to get local_id, handle both server and local IDs)
        val localTodo = databaseHelper?.getTodoById(todoId)

        // Store workspace_id for queue operation
        val queuePayload = """{"workspace_id":$workspaceId,"todo_id":$todoId}"""

        // Delete from local database immediately
        if (localTodo?.serverId != null) {
            databaseHelper?.deleteTodoByServerId(localTodo.serverId)
        } else {
            databaseHelper?.deleteTodoByLocalId(localTodo?.localId ?: 0)
        }

        // If offline, queue operation and return success
        if (offlineManager?.isOnline() == false) {
            LogHelper.logInfo("TodoAPI", "Deleting todo offline: #$todoId")
            databaseHelper?.queueOperation(OfflineManager.OP_DELETE, localTodo?.serverId ?: 0, localTodo?.localId, queuePayload)
            callback(true, 200, """{"id":$todoId}""", 0)
            return
        }

        // Online - execute API call
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId"
        val headers = mapOf("Authorization" to Config.AUTH_TOKEN)

        LogHelper.logInfo("TodoAPI", "Deleting todo #$todoId")
        apiHelper.request("DELETE", url, headers, null, callback)
    }

    fun toggleTodoDone(
        workspaceId: Int,
        todoId: Int,
        done: Boolean,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val body = """{"done":$done}"""
        val queuePayload = """{"workspace_id":$workspaceId,"done":$done}"""

        // Update local database immediately (use getTodoById to handle both server and local IDs)
        LogHelper.logInfo("TodoAPI", "toggleTodoDone called with todoId=$todoId")
        val localTodo = databaseHelper?.getTodoById(todoId)
        LogHelper.logInfo("TodoAPI", "getTodoById($todoId) returned: ${if (localTodo != null) "localId=${localTodo.localId}, serverId=${localTodo.serverId}" else "NULL"}")

        if (localTodo != null) {
            // Pass localId to prevent creating duplicates when updating offline-created todos
            databaseHelper.saveTodo(localTodo.serverId, localTodo.text, done, localTodo.date, 1, localTodo.localId)
        }

        // If offline, queue operation and return success
        if (offlineManager?.isOnline() == false) {
            LogHelper.logInfo("TodoAPI", "Toggling todo offline: #$todoId | local_todo_id=${localTodo?.localId} | server_id=${localTodo?.serverId}")
            LogHelper.logInfo("TodoAPI", "DEBUG: todoId passed to toggleTodoDone = $todoId")
            LogHelper.logInfo("TodoAPI", "DEBUG: localTodo is ${if (localTodo == null) "NULL" else "NOT NULL (localId=${localTodo.localId}, serverId=${localTodo.serverId})"}")

            // Add debug info to payload
            val debugPayload = """{"workspace_id":$workspaceId,"done":$done,"_debug_todoId":$todoId,"_debug_localTodoFound":${localTodo != null},"_debug_localId":${localTodo?.localId},"_debug_serverId":${localTodo?.serverId}}"""
            databaseHelper?.queueOperation(OfflineManager.OP_TOGGLE_DONE, localTodo?.serverId ?: 0, localTodo?.localId, debugPayload)
            callback(true, 200, """{"id":$todoId,"done":$done}""", 0)
            return
        }

        // Online - execute API call
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId/state"
        val headers = mapOf(
            "Authorization" to Config.AUTH_TOKEN,
            "Content-Type" to "application/json"
        )

        LogHelper.logInfo("TodoAPI", "Toggle todo #$todoId done=$done")
        apiHelper.request("POST", url, headers, body) { success, statusCode, response, responseTime ->
            if (success) {
                databaseHelper?.saveTodo(todoId, localTodo?.text ?: "", done, localTodo?.date ?: "", 0)
            }
            callback(success, statusCode, response, responseTime)
        }
    }

    fun repositionTodo(
        workspaceId: Int,
        todoId: Int,
        currentDate: String,
        position: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val body = """{"current_date":"$currentDate","position":$position}"""
        val queuePayload = """{"workspace_id":$workspaceId,"current_date":"$currentDate","position":$position}"""

        // Update local database immediately (use getTodoById to handle both server and local IDs)
        LogHelper.logInfo("TodoAPI", "repositionTodo called with todoId=$todoId")
        val localTodo = databaseHelper?.getTodoById(todoId)
        LogHelper.logInfo("TodoAPI", "getTodoById($todoId) returned: ${if (localTodo != null) "localId=${localTodo.localId}, serverId=${localTodo.serverId}" else "NULL"}")

        if (localTodo != null) {
            // Pass localId to prevent creating duplicates when updating offline-created todos
            databaseHelper.saveTodo(localTodo.serverId, localTodo.text, localTodo.done, currentDate, 1, localTodo.localId)
        }

        // If offline, queue operation and return success
        if (offlineManager?.isOnline() == false) {
            LogHelper.logInfo("TodoAPI", "Repositioning todo offline: #$todoId | local_todo_id=${localTodo?.localId} | server_id=${localTodo?.serverId}")
            LogHelper.logInfo("TodoAPI", "DEBUG: todoId passed to repositionTodo = $todoId")
            LogHelper.logInfo("TodoAPI", "DEBUG: localTodo is ${if (localTodo == null) "NULL" else "NOT NULL (localId=${localTodo.localId}, serverId=${localTodo.serverId})"}")

            // Add debug info to payload
            val debugPayload = """{"workspace_id":$workspaceId,"current_date":"$currentDate","position":$position,"_debug_todoId":$todoId,"_debug_localTodoFound":${localTodo != null},"_debug_localId":${localTodo?.localId},"_debug_serverId":${localTodo?.serverId}}"""
            databaseHelper?.queueOperation(OfflineManager.OP_REPOSITION, localTodo?.serverId ?: 0, localTodo?.localId, debugPayload)
            callback(true, 200, """{"id":$todoId}""", 0)
            return
        }

        // Online - execute API call
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId/reposition"
        val headers = mapOf(
            "Authorization" to Config.AUTH_TOKEN,
            "Content-Type" to "application/json"
        )

        LogHelper.logInfo("TodoAPI", "Reposition todo #$todoId to position $position on $currentDate")
        apiHelper.request("POST", url, headers, body) { success, statusCode, response, responseTime ->
            if (success) {
                databaseHelper?.saveTodo(todoId, localTodo?.text ?: "", localTodo?.done ?: false, currentDate, 0)
            }
            callback(success, statusCode, response, responseTime)
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Cache todos from server response in local database
     */
    private fun cacheServerTodos(response: String) {
        try {
            // Parse JSON response to extract todos
            val todoObjects = response.split("""{"cid"""").drop(1)

            for (todoObj in todoObjects) {
                try {
                    val idMatch = """"id":(\d+)""".toRegex().find(todoObj)
                    val dateMatch = """"current_date":"([^"]+)"""".toRegex().find(todoObj)
                    val doneMatch = """"done":(true|false)""".toRegex().find(todoObj)
                    val textMatch = """"text":"([^"]*?)"""".toRegex().find(todoObj)

                    if (idMatch != null && dateMatch != null && doneMatch != null && textMatch != null) {
                        val id = idMatch.groupValues[1].toInt()
                        val date = dateMatch.groupValues[1]
                        val done = doneMatch.groupValues[1] == "true"
                        val text = textMatch.groupValues[1]

                        databaseHelper?.saveTodo(id, text, done, date, 0)
                    }
                } catch (e: Exception) {
                    LogHelper.logError("TodoAPI", "Error parsing todo: ${e.message}", 0, null)
                }
            }
        } catch (e: Exception) {
            LogHelper.logError("TodoAPI", "Error caching todos: ${e.message}", 0, null)
        }
    }

    /**
     * Build JSON response from local todos
     */
    private fun buildJsonResponse(todos: List<LocalTodo>): String {
        val todoJsons = todos.map { todo ->
            // Use serverId if available, otherwise use localId for offline-created todos
            val id = todo.serverId ?: todo.localId.toInt()
            """{"cid":"local_${todo.localId}","id":$id,"text":"${todo.text}","done":${todo.done},"current_date":"${todo.date}"}"""
        }
        return "[${todoJsons.joinToString(",")}]"
    }
}
