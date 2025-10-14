package com.example.kitkat

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var scrollView: ScrollView

    // Using XHR Proxy - supports all HTTP operations
    private lateinit var apiHelper: ApiHelper
    private lateinit var todoApi: TodoApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize API helper
        apiHelper = ApiHelperFactory.create(this)
        todoApi = TodoApi(apiHelper)

        // Main layout
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        val title = TextView(this).apply {
            text = "KitKat Todo App"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(title)

        // Buttons container (scrollable)
        val buttonScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f // Take up available space
            }
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Add all buttons to scrollable container
        buttonLayout.addView(Button(this).apply {
            text = "List Todos"
            setOnClickListener { listTodos() }
        })

        buttonLayout.addView(Button(this).apply {
            text = "Create Todo"
            setOnClickListener { createTodo() }
        })

        buttonLayout.addView(Button(this).apply {
            text = "Update Todo #1"
            setOnClickListener { updateTodo() }
        })

        buttonLayout.addView(Button(this).apply {
            text = "Delete Todo #1"
            setOnClickListener { deleteTodo() }
        })

        buttonLayout.addView(Button(this).apply {
            text = "Toggle Todo #1 Done"
            setOnClickListener { toggleTodo() }
        })

        buttonLayout.addView(Button(this).apply {
            text = "Clear Logs"
            setOnClickListener { logTextView.text = "" }
        })

        buttonScrollView.addView(buttonLayout)
        mainLayout.addView(buttonScrollView)

        // Logs section
        val logLabel = TextView(this).apply {
            text = "Logs:"
            textSize = 14f
            setPadding(0, 16, 0, 8)
        }
        mainLayout.addView(logLabel)

        // ScrollView with TextView for logs
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f // Take up available space
            }
        }

        logTextView = TextView(this).apply {
            text = "Logs will appear here...\n"
            textSize = 11f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        scrollView.addView(logTextView)
        mainLayout.addView(scrollView)

        setContentView(mainLayout)

        appendLog("App started")
        appendLog("Using XHR Proxy (supports all HTTP methods)")
        appendLog("Server: http://192.168.1.11:8080")
        LogHelper.logInfo("APP", "MainActivity started")
    }

    // Store latest todo ID from list (for update/delete/toggle operations)
    private var latestTodoId: Int? = null

    private fun listTodos() {
        appendLog("\n[LIST] Fetching todos...")
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        todoApi.listTodos(Config.WORKSPACE_ID, today, today) { success, statusCode, response, responseTime ->
            runOnUiThread {
                if (success) {
                    appendLog("✓ HTTP $statusCode in ${responseTime}ms")
                    appendLog("Response body:")
                    appendLog(response)

                    // Extract first todo ID for update/delete/toggle
                    try {
                        val regex = """"id":(\d+)""".toRegex()
                        val match = regex.find(response)
                        latestTodoId = match?.groupValues?.get(1)?.toIntOrNull()
                        latestTodoId?.let {
                            appendLog("Saved todo ID $it for operations")
                        }
                    } catch (e: Exception) {
                        appendLog("Could not extract todo ID")
                    }
                } else {
                    appendLog("✗ HTTP $statusCode")
                    appendLog("Error: $response")
                }
            }
        }
    }

    private fun createTodo() {
        val newTodoText = "Test todo created at ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
        appendLog("\n[CREATE] Creating: $newTodoText")
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        todoApi.createTodo(Config.WORKSPACE_ID, newTodoText, today) { success, statusCode, response, responseTime ->
            runOnUiThread {
                if (success) {
                    appendLog("✓ Created! HTTP $statusCode in ${responseTime}ms")
                    appendLog("Response body:")
                    appendLog(response)
                } else {
                    appendLog("✗ HTTP $statusCode")
                    appendLog("Error: $response")
                }
            }
        }
    }

    private fun updateTodo() {
        val todoId = latestTodoId
        if (todoId == null) {
            appendLog("\n[UPDATE] Error: No todo ID. Click 'List Todos' first")
            return
        }

        val updatedText = "Updated at ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
        appendLog("\n[UPDATE] Updating todo #$todoId: $updatedText")

        todoApi.updateTodo(Config.WORKSPACE_ID, todoId, updatedText) { success, statusCode, response, responseTime ->
            runOnUiThread {
                if (success) {
                    appendLog("✓ Updated! HTTP $statusCode in ${responseTime}ms")
                    appendLog("Response body:")
                    appendLog(response)
                } else {
                    appendLog("✗ HTTP $statusCode")
                    appendLog("Error: $response")
                }
            }
        }
    }

    private fun deleteTodo() {
        val todoId = latestTodoId
        if (todoId == null) {
            appendLog("\n[DELETE] Error: No todo ID. Click 'List Todos' first")
            return
        }

        appendLog("\n[DELETE] Deleting todo #$todoId")

        todoApi.deleteTodo(Config.WORKSPACE_ID, todoId) { success, statusCode, response, responseTime ->
            runOnUiThread {
                if (success) {
                    appendLog("✓ Deleted! HTTP $statusCode in ${responseTime}ms")
                    appendLog("Response body:")
                    appendLog(response)
                    latestTodoId = null // Clear since it's deleted
                } else {
                    appendLog("✗ HTTP $statusCode")
                    appendLog("Error: $response")
                }
            }
        }
    }

    private fun toggleTodo() {
        val todoId = latestTodoId
        if (todoId == null) {
            appendLog("\n[TOGGLE] Error: No todo ID. Click 'List Todos' first")
            return
        }

        appendLog("\n[TOGGLE] Toggling done status for todo #$todoId")

        todoApi.toggleTodoDone(Config.WORKSPACE_ID, todoId, true) { success, statusCode, response, responseTime ->
            runOnUiThread {
                if (success) {
                    appendLog("✓ Toggled! HTTP $statusCode in ${responseTime}ms")
                    appendLog("Response body:")
                    appendLog(response)
                } else {
                    appendLog("✗ HTTP $statusCode")
                    appendLog("Error: $response")
                }
            }
        }
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            logTextView.append("[$timestamp] $message\n")
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
}
