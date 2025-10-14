package com.example.kitkat

import java.text.SimpleDateFormat
import java.util.*

/**
 * High-level Todo Helper that manages state and provides convenient CRUD operations
 */
class TodoHelper(private val todoApi: TodoApi) {

    // Cached todo IDs and date
    var firstTodoId: Int? = null
        private set
    var lastTodoId: Int? = null
        private set
    var latestTodoId: Int? = null
        private set
    var currentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        private set

    /**
     * List todos for today and cache IDs
     */
    fun listTodosToday(
        workspaceId: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long, firstId: Int?, lastId: Int?) -> Unit
    ) {
        currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        todoApi.listTodos(workspaceId, currentDate, currentDate) { success, statusCode, response, responseTime ->
            if (success) {
                // Extract first and last todo IDs
                try {
                    val regex = """"id":(\d+)""".toRegex()
                    val matches = regex.findAll(response).toList()

                    if (matches.isNotEmpty()) {
                        firstTodoId = matches.first().groupValues[1].toIntOrNull()
                        lastTodoId = matches.last().groupValues[1].toIntOrNull()
                        latestTodoId = firstTodoId
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }

            callback(success, statusCode, response, responseTime, firstTodoId, lastTodoId)
        }
    }

    /**
     * Create a new todo with auto-generated text
     */
    fun createTodoNow(
        workspaceId: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val text = "Test todo created at ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        todoApi.createTodo(workspaceId, text, date, callback)
    }

    /**
     * Update the first/latest todo
     */
    fun updateLatestTodo(
        workspaceId: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long, error: String?) -> Unit
    ) {
        val todoId = latestTodoId
        if (todoId == null) {
            callback(false, 0, "No todo ID available", 0, "Click 'List Todos' first")
            return
        }

        val text = "Updated at ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
        todoApi.updateTodo(workspaceId, todoId, text) { success, statusCode, response, responseTime ->
            callback(success, statusCode, response, responseTime, null)
        }
    }

    /**
     * Delete the first/latest todo
     */
    fun deleteLatestTodo(
        workspaceId: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long, error: String?) -> Unit
    ) {
        val todoId = latestTodoId
        if (todoId == null) {
            callback(false, 0, "No todo ID available", 0, "Click 'List Todos' first")
            return
        }

        todoApi.deleteTodo(workspaceId, todoId) { success, statusCode, response, responseTime ->
            if (success) {
                latestTodoId = null // Clear since deleted
            }
            callback(success, statusCode, response, responseTime, null)
        }
    }

    /**
     * Toggle the first/latest todo to done
     */
    fun toggleLatestTodo(
        workspaceId: Int,
        done: Boolean = true,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long, error: String?) -> Unit
    ) {
        val todoId = latestTodoId
        if (todoId == null) {
            callback(false, 0, "No todo ID available", 0, "Click 'List Todos' first")
            return
        }

        todoApi.toggleTodoDone(workspaceId, todoId, done) { success, statusCode, response, responseTime ->
            callback(success, statusCode, response, responseTime, null)
        }
    }

    /**
     * Move last todo to first position
     */
    fun moveLastToFirst(
        workspaceId: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long, error: String?) -> Unit
    ) {
        val todoId = lastTodoId
        if (todoId == null) {
            callback(false, 0, "No last todo ID", 0, "Click 'List Todos' first")
            return
        }

        todoApi.repositionTodo(workspaceId, todoId, currentDate, 0) { success, statusCode, response, responseTime ->
            callback(success, statusCode, response, responseTime, null)
        }
    }

    /**
     * Move first todo to tomorrow at position 0
     */
    fun moveFirstToTomorrow(
        workspaceId: Int,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long, error: String?) -> Unit
    ) {
        val todoId = firstTodoId
        if (todoId == null) {
            callback(false, 0, "No first todo ID", 0, "Click 'List Todos' first")
            return
        }

        // Calculate tomorrow
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        todoApi.repositionTodo(workspaceId, todoId, tomorrow, 0) { success, statusCode, response, responseTime ->
            callback(success, statusCode, response, responseTime, null)
        }
    }
}
