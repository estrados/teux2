package com.example.kitkat

import android.content.Context

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
        return XhrProxyApiHelper(context)
    }
}

/**
 * Common Todo API operations
 */
class TodoApi(private val apiHelper: ApiHelper) {

    fun listTodos(
        workspaceId: Int,
        since: String,
        until: String,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos?since=$since&until=$until"
        val headers = mapOf("Authorization" to Config.AUTH_TOKEN)

        LogHelper.logInfo("TodoAPI", "Listing todos: $since to $until")
        apiHelper.request("GET", url, headers, null, callback)
    }

    fun createTodo(
        workspaceId: Int,
        text: String,
        currentDate: String,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos"
        val headers = mapOf(
            "Authorization" to Config.AUTH_TOKEN,
            "Content-Type" to "application/json"
        )
        val body = """{"text":"$text","current_date":"$currentDate"}"""

        LogHelper.logInfo("TodoAPI", "Creating todo: $text")
        apiHelper.request("POST", url, headers, body, callback)
    }

    fun updateTodo(
        workspaceId: Int,
        todoId: Int,
        text: String,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId"
        val headers = mapOf(
            "Authorization" to Config.AUTH_TOKEN,
            "Content-Type" to "application/json"
        )
        val body = """{"text":"$text"}"""

        LogHelper.logInfo("TodoAPI", "Updating todo #$todoId: $text")
        apiHelper.request("PATCH", url, headers, body, callback)
    }

    fun deleteTodo(
        workspaceId: Int,
        todoId: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
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
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId/state"
        val headers = mapOf(
            "Authorization" to Config.AUTH_TOKEN,
            "Content-Type" to "application/json"
        )
        val body = """{"done":$done}"""

        LogHelper.logInfo("TodoAPI", "Toggle todo #$todoId done=$done")
        apiHelper.request("POST", url, headers, body, callback)
    }

    fun repositionTodo(
        workspaceId: Int,
        todoId: Int,
        currentDate: String,
        position: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId/reposition"
        val headers = mapOf(
            "Authorization" to Config.AUTH_TOKEN,
            "Content-Type" to "application/json"
        )
        val body = """{"current_date":"$currentDate","position":$position}"""

        LogHelper.logInfo("TodoAPI", "Reposition todo #$todoId to position $position on $currentDate")
        apiHelper.request("POST", url, headers, body, callback)
    }
}
