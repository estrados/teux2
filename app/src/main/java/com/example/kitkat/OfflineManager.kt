package com.example.kitkat

import android.content.Context
import org.json.JSONObject

/**
 * Manages offline operations and syncing with the server.
 * Coordinates between NetworkMonitor, DatabaseHelper, and ApiHelper.
 */
class OfflineManager(
    private val context: Context,
    private val apiHelper: ApiHelper,
    private val networkMonitor: NetworkMonitor,
    private val databaseHelper: DatabaseHelper
) {

    private var isSyncing = false
    private val syncListeners = mutableListOf<(Boolean, String?) -> Unit>()

    companion object {
        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
        const val OP_TOGGLE_DONE = "TOGGLE_DONE"
        const val OP_REPOSITION = "REPOSITION"

        const val MAX_RETRY_COUNT = 3
    }

    init {
        // Listen for network changes to trigger sync
        networkMonitor.addListener { isOnline ->
            if (isOnline && !isSyncing) {
                LogHelper.logInfo("OFFLINE", "Network online - starting sync")
                syncPendingOperations()
            }
        }
    }

    /**
     * Check if device is online
     */
    fun isOnline(): Boolean = networkMonitor.isOnline()

    /**
     * Add listener for sync completion
     */
    fun addSyncListener(listener: (Boolean, String?) -> Unit) {
        syncListeners.add(listener)
    }

    /**
     * Remove sync listener
     */
    fun removeSyncListener(listener: (Boolean, String?) -> Unit) {
        syncListeners.remove(listener)
    }

    /**
     * Sync all pending operations with the server
     */
    fun syncPendingOperations() {
        if (isSyncing) {
            LogHelper.logInfo("SYNC", "Sync already in progress")
            return
        }

        if (!isOnline()) {
            LogHelper.logInfo("SYNC", "Cannot sync - offline")
            notifySyncListeners(false, "Offline")
            return
        }

        val operations = databaseHelper.getPendingOperations()
        if (operations.isEmpty()) {
            LogHelper.logInfo("SYNC", "No pending operations to sync")
            notifySyncListeners(true, null)
            return
        }

        LogHelper.logInfo("SYNC", "===== SYNC STARTED: ${operations.size} operations =====")
        logOperationsTable(operations, "INITIAL STATE")
        isSyncing = true

        processOperationsSequentially(operations, 0)
    }

    /**
     * Process operations one by one in order
     */
    private fun processOperationsSequentially(operations: List<PendingOperation>, index: Int, reloadedAfterCreate: Boolean = false) {
        if (index >= operations.size) {
            // All done
            isSyncing = false
            LogHelper.logSuccess("SYNC", "===== SYNC COMPLETED: ${operations.size} operations processed =====", 0, null)
            notifySyncListeners(true, null)
            return
        }

        val operation = operations[index]
        val progress = "[${index + 1}/${operations.size}]"

        LogHelper.logInfo("SYNC", "$progress Processing operation #${operation.id} - Type: ${operation.type}")

        // Check if we should skip due to retry limit
        if (operation.retryCount >= MAX_RETRY_COUNT) {
            LogHelper.logError("SYNC", "$progress Operation #${operation.id} exceeded retry limit (${operation.retryCount}), skipping", 0, null)
            databaseHelper.removeOperation(operation.id)
            processOperationsSequentially(operations, index + 1, reloadedAfterCreate)
            return
        }

        // Execute the operation
        executeOperation(operation, index + 1, operations.size) { success, shouldReloadOperations ->
            if (success) {
                // Remove from queue
                LogHelper.logSuccess("SYNC", "$progress Operation #${operation.id} completed successfully", 0, null)
                databaseHelper.removeOperation(operation.id)

                // If this was a CREATE and we updated operation IDs, reload the operations list
                if (shouldReloadOperations && !reloadedAfterCreate) {
                    LogHelper.logInfo("SYNC", "Reloading operations after CREATE to get updated todo IDs")
                    val updatedOperations = databaseHelper.getPendingOperations()
                    logOperationsTable(updatedOperations, "AFTER RELOAD")
                    // Find the next operation in the updated list (skip already processed ones)
                    val nextIndex = updatedOperations.indexOfFirst { it.id > operation.id }
                    if (nextIndex >= 0) {
                        LogHelper.logInfo("SYNC_DEBUG", "Continuing with next operation at index $nextIndex (Op#${updatedOperations[nextIndex].id})")
                        processOperationsSequentially(updatedOperations, nextIndex, true)
                    } else {
                        // No more operations
                        isSyncing = false
                        LogHelper.logSuccess("SYNC", "===== SYNC COMPLETED: All operations processed =====", 0, null)
                        notifySyncListeners(true, null)
                    }
                } else {
                    processOperationsSequentially(operations, index + 1, reloadedAfterCreate)
                }
            } else {
                // Increment retry count
                databaseHelper.incrementRetryCount(operation.id)
                LogHelper.logError("SYNC", "$progress Operation #${operation.id} failed (retry ${operation.retryCount + 1}/${MAX_RETRY_COUNT})", 0, null)

                // Check if we're still online
                if (!isOnline()) {
                    LogHelper.logInfo("SYNC", "===== SYNC INTERRUPTED: Connection lost =====")
                    isSyncing = false
                    notifySyncListeners(false, "Connection lost")
                } else {
                    // Continue with next operation if still online
                    processOperationsSequentially(operations, index + 1, reloadedAfterCreate)
                }
            }
        }
    }

    /**
     * Execute a single operation
     * Callback parameters: (success: Boolean, shouldReloadOperations: Boolean)
     */
    private fun executeOperation(operation: PendingOperation, currentIndex: Int, totalOps: Int, callback: (Boolean, Boolean) -> Unit) {
        val progress = "[$currentIndex/$totalOps]"
        LogHelper.logInfo("SYNC", "$progress Executing ${operation.type} operation #${operation.id}")

        try {
            val payload = JSONObject(operation.payload)
            val workspaceId = payload.optInt("workspace_id", 444459)

            when (operation.type) {
                OP_CREATE -> {
                    val text = payload.getString("text")
                    val date = payload.getString("current_date")
                    val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos"
                    val headers = mapOf(
                        "Authorization" to Config.AUTH_TOKEN,
                        "Content-Type" to "application/json"
                    )

                    // Create request body WITHOUT workspace_id (it's in URL only)
                    val requestBody = """{"text":"$text","current_date":"$date"}"""

                    LogHelper.logInfo("SYNC", "$progress CREATE request - Todo: \"$text\" on $date")
                    LogHelper.logInfo("SYNC", "$progress Request URL: POST $url")
                    LogHelper.logInfo("SYNC_DEBUG", "$progress BEFORE CREATE - Op#${operation.id} has local_todo_id=${operation.localTodoId}")

                    apiHelper.request("POST", url, headers, requestBody) { success, statusCode, response, responseTime ->
                        LogHelper.logInfo("SYNC", "$progress CREATE response - Status: $statusCode, Time: ${responseTime}ms")
                        var shouldReload = false
                        if (success && operation.localTodoId != null) {
                            // Extract server ID from response and update local todo
                            try {
                                val responseJson = JSONObject(response)
                                // TeuxDeux wraps the todo in a "todo" object
                                val todoJson = if (responseJson.has("todo")) {
                                    responseJson.getJSONObject("todo")
                                } else {
                                    responseJson
                                }
                                val serverId = todoJson.getInt("id")
                                val localTodo = databaseHelper.getTodoByLocalId(operation.localTodoId)
                                if (localTodo != null) {
                                    LogHelper.logInfo("SYNC_DEBUG", "$progress BEFORE UPDATE - About to update operations with local_todo_id=${operation.localTodoId} to server_id=$serverId")

                                    // Log current state of pending operations
                                    val beforeUpdate = databaseHelper.getPendingOperations()
                                    logOperationsTable(beforeUpdate, "BEFORE DB UPDATE")

                                    // Update all pending operations that reference this local_todo_id to use the new server ID
                                    databaseHelper.updateOperationTodoIds(operation.localTodoId, serverId)

                                    // Log state after update
                                    val afterUpdate = databaseHelper.getPendingOperations()
                                    logOperationsTable(afterUpdate, "AFTER DB UPDATE")

                                    shouldReload = true // Signal that we need to reload operations

                                    // Delete the old local-only entry
                                    databaseHelper.deleteTodoByLocalId(operation.localTodoId)
                                    // Save with server ID (this prevents duplicates)
                                    databaseHelper.saveTodo(serverId, localTodo.text, localTodo.done, localTodo.date, 0)
                                    LogHelper.logSuccess("SYNC", "$progress Todo created with server ID: $serverId (replaced local ID: ${operation.localTodoId}, updated pending ops)", responseTime, response.take(300))
                                }
                            } catch (e: Exception) {
                                LogHelper.logError("SYNC", "$progress Failed to parse create response: ${e.message}", 0, response.take(300))
                            }
                        } else if (!success) {
                            LogHelper.logError("SYNC", "$progress CREATE failed", responseTime, response.take(200))
                        }
                        callback(success, shouldReload)
                    }
                }

                OP_UPDATE -> {
                    val todoId = operation.todoId ?: run {
                        LogHelper.logError("SYNC", "$progress UPDATE operation missing todo ID", 0, null)
                        callback(false, false)
                        return
                    }
                    val text = payload.getString("text")
                    val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId"
                    val headers = mapOf(
                        "Authorization" to Config.AUTH_TOKEN,
                        "Content-Type" to "application/json"
                    )

                    // Request body WITHOUT workspace_id
                    val requestBody = """{"text":"$text"}"""

                    LogHelper.logInfo("SYNC", "$progress UPDATE request - Todo #$todoId: \"$text\"")
                    LogHelper.logInfo("SYNC", "$progress Request URL: PATCH $url")

                    apiHelper.request("PATCH", url, headers, requestBody) { success, statusCode, response, responseTime ->
                        LogHelper.logInfo("SYNC", "$progress UPDATE response - Status: $statusCode, Time: ${responseTime}ms")
                        if (success) {
                            LogHelper.logSuccess("SYNC", "$progress Todo #$todoId updated on server", responseTime, response.take(200))
                        } else {
                            LogHelper.logError("SYNC", "$progress UPDATE failed for todo #$todoId", responseTime, response.take(200))
                        }
                        callback(success, false)
                    }
                }

                OP_DELETE -> {
                    val todoId = operation.todoId ?: run {
                        LogHelper.logError("SYNC", "$progress DELETE operation missing todo ID", 0, null)
                        callback(false, false)
                        return
                    }
                    val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId"
                    val headers = mapOf("Authorization" to Config.AUTH_TOKEN)

                    LogHelper.logInfo("SYNC", "$progress DELETE request - Todo #$todoId")
                    LogHelper.logInfo("SYNC", "$progress Request URL: DELETE $url")

                    apiHelper.request("DELETE", url, headers, null) { success, statusCode, response, responseTime ->
                        LogHelper.logInfo("SYNC", "$progress DELETE response - Status: $statusCode, Time: ${responseTime}ms")
                        if (success) {
                            LogHelper.logSuccess("SYNC", "$progress Todo #$todoId deleted from server", responseTime, response.take(200))
                        } else {
                            LogHelper.logError("SYNC", "$progress DELETE failed for todo #$todoId", responseTime, response.take(200))
                        }
                        callback(success, false)
                    }
                }

                OP_TOGGLE_DONE -> {
                    val todoId = operation.todoId ?: run {
                        LogHelper.logError("SYNC", "$progress TOGGLE_DONE operation missing todo ID", 0, null)
                        callback(false, false)
                        return
                    }
                    val done = payload.getBoolean("done")
                    val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId/state"
                    val headers = mapOf(
                        "Authorization" to Config.AUTH_TOKEN,
                        "Content-Type" to "application/json"
                    )

                    // Request body WITHOUT workspace_id
                    val requestBody = """{"done":$done}"""

                    LogHelper.logInfo("SYNC", "$progress TOGGLE_DONE request - Todo #$todoId to done=$done")
                    LogHelper.logInfo("SYNC", "$progress Request URL: POST $url")

                    apiHelper.request("POST", url, headers, requestBody) { success, statusCode, response, responseTime ->
                        LogHelper.logInfo("SYNC", "$progress TOGGLE_DONE response - Status: $statusCode, Time: ${responseTime}ms")
                        if (success) {
                            LogHelper.logSuccess("SYNC", "$progress Todo #$todoId toggled to done=$done on server", responseTime, response.take(200))
                        } else {
                            LogHelper.logError("SYNC", "$progress TOGGLE_DONE failed for todo #$todoId", responseTime, response.take(200))
                        }
                        callback(success, false)
                    }
                }

                OP_REPOSITION -> {
                    val todoId = operation.todoId ?: run {
                        LogHelper.logError("SYNC", "$progress REPOSITION operation missing todo ID", 0, null)
                        callback(false, false)
                        return
                    }
                    val targetDate = payload.getString("current_date")
                    val position = payload.getInt("position")
                    val url = "${Config.BASE_URL}/workspaces/$workspaceId/todos/$todoId/reposition"
                    val headers = mapOf(
                        "Authorization" to Config.AUTH_TOKEN,
                        "Content-Type" to "application/json"
                    )

                    // Request body WITHOUT workspace_id
                    val requestBody = """{"current_date":"$targetDate","position":$position}"""

                    LogHelper.logInfo("SYNC", "$progress REPOSITION request - Todo #$todoId to $targetDate at position $position")
                    LogHelper.logInfo("SYNC", "$progress Request URL: POST $url")

                    apiHelper.request("POST", url, headers, requestBody) { success, statusCode, response, responseTime ->
                        LogHelper.logInfo("SYNC", "$progress REPOSITION response - Status: $statusCode, Time: ${responseTime}ms")
                        if (success) {
                            LogHelper.logSuccess("SYNC", "$progress Todo #$todoId repositioned on server", responseTime, response.take(200))
                        } else {
                            LogHelper.logError("SYNC", "$progress REPOSITION failed for todo #$todoId", responseTime, response.take(200))
                        }
                        callback(success, false)
                    }
                }

                else -> {
                    LogHelper.logError("SYNC", "$progress Unknown operation type: ${operation.type}", 0, null)
                    callback(false, false)
                }
            }
        } catch (e: Exception) {
            LogHelper.logError("SYNC", "$progress Error executing operation: ${e.message}", 0, e.stackTraceToString())
            callback(false, false)
        }
    }

    /**
     * Notify all sync listeners
     */
    private fun notifySyncListeners(success: Boolean, error: String?) {
        syncListeners.forEach { it(success, error) }
    }

    /**
     * Get count of pending operations
     */
    fun getPendingOperationCount(): Int {
        return databaseHelper.getPendingOperationCount()
    }

    /**
     * Debug: Log the operations table content
     */
    private fun logOperationsTable(operations: List<PendingOperation>, label: String) {
        LogHelper.logInfo("SYNC_DEBUG", "========== OPERATIONS TABLE: $label ==========")
        operations.forEachIndexed { idx, op ->
            LogHelper.logInfo("SYNC_DEBUG", "  [$idx] Op#${op.id} ${op.type} | todo_id=${op.todoId} | local_todo_id=${op.localTodoId} | retry=${op.retryCount}")
            LogHelper.logInfo("SYNC_DEBUG", "       Payload: ${op.payload}")
        }
        LogHelper.logInfo("SYNC_DEBUG", "=".repeat(50))
    }
}
