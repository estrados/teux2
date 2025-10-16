package com.example.kitkat

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite database helper for offline storage.
 * Stores todos locally and queues operations for sync.
 * Compatible with API 19 (KitKat).
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "teuxdeux_offline.db"
        private const val DATABASE_VERSION = 1

        // Todos table
        const val TABLE_TODOS = "todos"
        const val COL_TODO_ID = "id"
        const val COL_TODO_TEXT = "text"
        const val COL_TODO_DONE = "done"
        const val COL_TODO_DATE = "date"
        const val COL_TODO_SYNC_STATUS = "sync_status" // 0=synced, 1=pending
        const val COL_TODO_LOCAL_ID = "local_id" // Auto-increment primary key

        // Pending operations table
        const val TABLE_OPERATIONS = "pending_operations"
        const val COL_OP_ID = "id"
        const val COL_OP_TYPE = "operation_type" // CREATE, UPDATE, DELETE, TOGGLE_DONE, REPOSITION
        const val COL_OP_TODO_ID = "todo_id" // Server ID (null for CREATE)
        const val COL_OP_LOCAL_TODO_ID = "local_todo_id" // Local ID reference
        const val COL_OP_PAYLOAD = "payload" // JSON payload
        const val COL_OP_TIMESTAMP = "timestamp"
        const val COL_OP_RETRY_COUNT = "retry_count"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create todos table
        val createTodosTable = """
            CREATE TABLE $TABLE_TODOS (
                $COL_TODO_LOCAL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TODO_ID INTEGER,
                $COL_TODO_TEXT TEXT NOT NULL,
                $COL_TODO_DONE INTEGER NOT NULL DEFAULT 0,
                $COL_TODO_DATE TEXT NOT NULL,
                $COL_TODO_SYNC_STATUS INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        // Create pending operations table
        val createOperationsTable = """
            CREATE TABLE $TABLE_OPERATIONS (
                $COL_OP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_OP_TYPE TEXT NOT NULL,
                $COL_OP_TODO_ID INTEGER,
                $COL_OP_LOCAL_TODO_ID INTEGER,
                $COL_OP_PAYLOAD TEXT NOT NULL,
                $COL_OP_TIMESTAMP INTEGER NOT NULL,
                $COL_OP_RETRY_COUNT INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        db.execSQL(createTodosTable)
        db.execSQL(createOperationsTable)

        LogHelper.logInfo("DATABASE", "Database created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For now, just drop and recreate (in production, would migrate data)
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TODOS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_OPERATIONS")
        onCreate(db)
    }

    // ==================== TODO OPERATIONS ====================

    /**
     * Insert or update a todo in local database
     * @param id Server ID (null for offline-created todos)
     * @param text Todo text
     * @param done Done status
     * @param date Current date
     * @param syncStatus Sync status (0=synced, 1=pending)
     * @param updateLocalId If provided, update this local_id row (for offline-created todos)
     */
    fun saveTodo(id: Int?, text: String, done: Boolean, date: String, syncStatus: Int = 0, updateLocalId: Long? = null): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            if (id != null) put(COL_TODO_ID, id)
            put(COL_TODO_TEXT, text)
            put(COL_TODO_DONE, if (done) 1 else 0)
            put(COL_TODO_DATE, date)
            put(COL_TODO_SYNC_STATUS, syncStatus)
        }

        // Check if todo with this server ID already exists
        val localId = if (id != null) {
            val existing = getTodoByServerId(id)
            if (existing != null) {
                // Update existing by server ID
                db.update(TABLE_TODOS, values, "$COL_TODO_ID = ?", arrayOf(id.toString()))
                existing.localId
            } else {
                // Insert new
                db.insert(TABLE_TODOS, null, values)
            }
        } else if (updateLocalId != null) {
            // Update existing by local ID (for offline-created todos)
            db.update(TABLE_TODOS, values, "$COL_TODO_LOCAL_ID = ?", arrayOf(updateLocalId.toString()))
            updateLocalId
        } else {
            // Insert new todo without server ID (offline creation)
            db.insert(TABLE_TODOS, null, values)
        }

        return localId
    }

    /**
     * Get todo by server ID
     */
    fun getTodoByServerId(serverId: Int): LocalTodo? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TODOS,
            null,
            "$COL_TODO_ID = ?",
            arrayOf(serverId.toString()),
            null, null, null
        )

        return cursor.use {
            if (it.moveToFirst()) cursorToTodo(it) else null
        }
    }

    /**
     * Get todo by local ID
     */
    fun getTodoByLocalId(localId: Long): LocalTodo? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TODOS,
            null,
            "$COL_TODO_LOCAL_ID = ?",
            arrayOf(localId.toString()),
            null, null, null
        )

        return cursor.use {
            if (it.moveToFirst()) cursorToTodo(it) else null
        }
    }

    /**
     * Get todo by ID (tries server_id first, then local_id as fallback)
     */
    fun getTodoById(id: Int): LocalTodo? {
        // First try server_id
        val byServerId = getTodoByServerId(id)
        if (byServerId != null) return byServerId

        // Fallback to local_id
        return getTodoByLocalId(id.toLong())
    }

    /**
     * Get all todos for a specific date
     */
    fun getTodosByDate(date: String): List<LocalTodo> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TODOS,
            null,
            "$COL_TODO_DATE = ?",
            arrayOf(date),
            null, null,
            "$COL_TODO_DONE ASC, $COL_TODO_TEXT ASC"
        )

        val todos = mutableListOf<LocalTodo>()
        cursor.use {
            while (it.moveToNext()) {
                todos.add(cursorToTodo(it))
            }
        }
        return todos
    }

    /**
     * Get all todos in a date range
     */
    fun getTodosInRange(startDate: String, endDate: String): List<LocalTodo> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TODOS,
            null,
            "$COL_TODO_DATE >= ? AND $COL_TODO_DATE <= ?",
            arrayOf(startDate, endDate),
            null, null,
            "$COL_TODO_DATE ASC, $COL_TODO_DONE ASC, $COL_TODO_TEXT ASC"
        )

        val todos = mutableListOf<LocalTodo>()
        cursor.use {
            while (it.moveToNext()) {
                todos.add(cursorToTodo(it))
            }
        }
        return todos
    }

    /**
     * Delete todo by server ID
     */
    fun deleteTodoByServerId(serverId: Int): Int {
        val db = writableDatabase
        return db.delete(TABLE_TODOS, "$COL_TODO_ID = ?", arrayOf(serverId.toString()))
    }

    /**
     * Delete todo by local ID
     */
    fun deleteTodoByLocalId(localId: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_TODOS, "$COL_TODO_LOCAL_ID = ?", arrayOf(localId.toString()))
    }

    /**
     * Clear all todos
     */
    fun clearAllTodos() {
        val db = writableDatabase
        db.delete(TABLE_TODOS, null, null)
        LogHelper.logInfo("DATABASE", "All todos cleared")
    }

    // ==================== OPERATION QUEUE ====================

    /**
     * Queue an operation for sync
     */
    fun queueOperation(type: String, todoId: Int?, localTodoId: Long?, payload: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_OP_TYPE, type)
            if (todoId != null) put(COL_OP_TODO_ID, todoId)
            if (localTodoId != null) put(COL_OP_LOCAL_TODO_ID, localTodoId)
            put(COL_OP_PAYLOAD, payload)
            put(COL_OP_TIMESTAMP, System.currentTimeMillis())
            put(COL_OP_RETRY_COUNT, 0)
        }

        val id = db.insert(TABLE_OPERATIONS, null, values)
        LogHelper.logInfo("DATABASE", "Operation queued: $type (ID: $id) | todo_id=$todoId | local_todo_id=$localTodoId")
        LogHelper.logInfo("DATABASE", "  Payload: $payload")
        return id
    }

    /**
     * Get all pending operations ordered by timestamp
     */
    fun getPendingOperations(): List<PendingOperation> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_OPERATIONS,
            null,
            null, null, null, null,
            "$COL_OP_TIMESTAMP ASC"
        )

        val operations = mutableListOf<PendingOperation>()
        cursor.use {
            while (it.moveToNext()) {
                operations.add(cursorToOperation(it))
            }
        }
        return operations
    }

    /**
     * Remove an operation from queue
     */
    fun removeOperation(operationId: Long): Int {
        val db = writableDatabase
        val deleted = db.delete(TABLE_OPERATIONS, "$COL_OP_ID = ?", arrayOf(operationId.toString()))
        if (deleted > 0) {
            LogHelper.logInfo("DATABASE", "Operation removed: $operationId")
        }
        return deleted
    }

    /**
     * Increment retry count for an operation
     */
    fun incrementRetryCount(operationId: Long) {
        val db = writableDatabase
        db.execSQL("UPDATE $TABLE_OPERATIONS SET $COL_OP_RETRY_COUNT = $COL_OP_RETRY_COUNT + 1 WHERE $COL_OP_ID = ?", arrayOf(operationId.toString()))
    }

    /**
     * Clear all pending operations
     */
    fun clearAllOperations() {
        val db = writableDatabase
        db.delete(TABLE_OPERATIONS, null, null)
        LogHelper.logInfo("DATABASE", "All operations cleared")
    }

    /**
     * Get count of pending operations
     */
    fun getPendingOperationCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_OPERATIONS", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /**
     * Update todo_id in all pending operations that reference a specific local_todo_id
     * This is called after a CREATE succeeds to update subsequent operations
     */
    fun updateOperationTodoIds(localTodoId: Long, newServerId: Int) {
        val db = writableDatabase

        // First, log what we're about to update
        val beforeCursor = db.rawQuery(
            "SELECT $COL_OP_ID, $COL_OP_TYPE, $COL_OP_TODO_ID, $COL_OP_LOCAL_TODO_ID FROM $TABLE_OPERATIONS WHERE $COL_OP_LOCAL_TODO_ID = ?",
            arrayOf(localTodoId.toString())
        )
        LogHelper.logInfo("DATABASE", "Operations to update (WHERE local_todo_id=$localTodoId):")
        beforeCursor.use {
            while (it.moveToNext()) {
                val opId = it.getLong(0)
                val opType = it.getString(1)
                val todoId = if (it.isNull(2)) "NULL" else it.getInt(2).toString()
                val localId = if (it.isNull(3)) "NULL" else it.getLong(3).toString()
                LogHelper.logInfo("DATABASE", "  Op#$opId $opType | todo_id=$todoId | local_todo_id=$localId")
            }
        }

        // Execute the update
        db.execSQL(
            "UPDATE $TABLE_OPERATIONS SET $COL_OP_TODO_ID = ? WHERE $COL_OP_LOCAL_TODO_ID = ? AND ($COL_OP_TODO_ID IS NULL OR $COL_OP_TODO_ID = 0)",
            arrayOf(newServerId.toString(), localTodoId.toString())
        )

        // Log what changed
        val afterCursor = db.rawQuery(
            "SELECT $COL_OP_ID, $COL_OP_TYPE, $COL_OP_TODO_ID, $COL_OP_LOCAL_TODO_ID FROM $TABLE_OPERATIONS WHERE $COL_OP_LOCAL_TODO_ID = ?",
            arrayOf(localTodoId.toString())
        )
        LogHelper.logInfo("DATABASE", "Operations after update (set todo_id=$newServerId):")
        afterCursor.use {
            while (it.moveToNext()) {
                val opId = it.getLong(0)
                val opType = it.getString(1)
                val todoId = if (it.isNull(2)) "NULL" else it.getInt(2).toString()
                val localId = if (it.isNull(3)) "NULL" else it.getLong(3).toString()
                LogHelper.logInfo("DATABASE", "  Op#$opId $opType | todo_id=$todoId | local_todo_id=$localId")
            }
        }

        LogHelper.logInfo("DATABASE", "Updated pending operations: local_id $localTodoId -> server_id $newServerId")
    }

    // ==================== HELPER METHODS ====================

    private fun cursorToTodo(cursor: Cursor): LocalTodo {
        return LocalTodo(
            localId = cursor.getLong(cursor.getColumnIndex(COL_TODO_LOCAL_ID)),
            serverId = if (cursor.isNull(cursor.getColumnIndex(COL_TODO_ID))) null else cursor.getInt(cursor.getColumnIndex(COL_TODO_ID)),
            text = cursor.getString(cursor.getColumnIndex(COL_TODO_TEXT)),
            done = cursor.getInt(cursor.getColumnIndex(COL_TODO_DONE)) == 1,
            date = cursor.getString(cursor.getColumnIndex(COL_TODO_DATE)),
            syncStatus = cursor.getInt(cursor.getColumnIndex(COL_TODO_SYNC_STATUS))
        )
    }

    private fun cursorToOperation(cursor: Cursor): PendingOperation {
        return PendingOperation(
            id = cursor.getLong(cursor.getColumnIndex(COL_OP_ID)),
            type = cursor.getString(cursor.getColumnIndex(COL_OP_TYPE)),
            todoId = if (cursor.isNull(cursor.getColumnIndex(COL_OP_TODO_ID))) null else cursor.getInt(cursor.getColumnIndex(COL_OP_TODO_ID)),
            localTodoId = if (cursor.isNull(cursor.getColumnIndex(COL_OP_LOCAL_TODO_ID))) null else cursor.getLong(cursor.getColumnIndex(COL_OP_LOCAL_TODO_ID)),
            payload = cursor.getString(cursor.getColumnIndex(COL_OP_PAYLOAD)),
            timestamp = cursor.getLong(cursor.getColumnIndex(COL_OP_TIMESTAMP)),
            retryCount = cursor.getInt(cursor.getColumnIndex(COL_OP_RETRY_COUNT))
        )
    }
}

// ==================== DATA CLASSES ====================

data class LocalTodo(
    val localId: Long,
    val serverId: Int?,
    val text: String,
    val done: Boolean,
    val date: String,
    val syncStatus: Int
)

data class PendingOperation(
    val id: Long,
    val type: String,
    val todoId: Int?,
    val localTodoId: Long?,
    val payload: String,
    val timestamp: Long,
    val retryCount: Int
)
