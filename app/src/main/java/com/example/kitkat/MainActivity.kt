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
    private lateinit var todoHelper: TodoHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize API helper
        apiHelper = ApiHelperFactory.create(this)
        todoApi = TodoApi(apiHelper)
        todoHelper = TodoHelper(todoApi)

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
            text = "Move Last Todo to First"
            setOnClickListener { moveLastToFirst() }
        })

        buttonLayout.addView(Button(this).apply {
            text = "Move First Todo to Tomorrow"
            setOnClickListener { moveFirstToTomorrow() }
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

    private fun listTodos() {
        appendLog("\n[LIST] Fetching todos...")

        todoHelper.listTodosToday(Config.WORKSPACE_ID) { success, statusCode, response, responseTime, firstId, lastId ->
            runOnUiThread {
                if (success) {
                    appendLog("✓ HTTP $statusCode in ${responseTime}ms")
                    appendLog("Response body:")
                    appendLog(response)
                    firstId?.let { appendLog("Saved first todo ID: $it") }
                    lastId?.let { appendLog("Saved last todo ID: $it") }
                } else {
                    appendLog("✗ HTTP $statusCode")
                    appendLog("Error: $response")
                }
            }
        }
    }

    private fun createTodo() {
        appendLog("\n[CREATE] Creating todo...")

        todoHelper.createTodoNow(Config.WORKSPACE_ID) { success, statusCode, response, responseTime ->
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
        appendLog("\n[UPDATE] Updating todo...")

        todoHelper.updateLatestTodo(Config.WORKSPACE_ID) { success, statusCode, response, responseTime, error ->
            runOnUiThread {
                if (error != null) {
                    appendLog("\n[UPDATE] Error: $error")
                } else if (success) {
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
        appendLog("\n[DELETE] Deleting todo...")

        todoHelper.deleteLatestTodo(Config.WORKSPACE_ID) { success, statusCode, response, responseTime, error ->
            runOnUiThread {
                if (error != null) {
                    appendLog("\n[DELETE] Error: $error")
                } else if (success) {
                    appendLog("✓ Deleted! HTTP $statusCode in ${responseTime}ms")
                    appendLog("Response body:")
                    appendLog(response)
                } else {
                    appendLog("✗ HTTP $statusCode")
                    appendLog("Error: $response")
                }
            }
        }
    }

    private fun toggleTodo() {
        appendLog("\n[TOGGLE] Toggling done status...")

        todoHelper.toggleLatestTodo(Config.WORKSPACE_ID, true) { success, statusCode, response, responseTime, error ->
            runOnUiThread {
                if (error != null) {
                    appendLog("\n[TOGGLE] Error: $error")
                } else if (success) {
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

    private fun moveLastToFirst() {
        appendLog("\n[REPOSITION] Moving last todo to first...")

        todoHelper.moveLastToFirst(Config.WORKSPACE_ID) { success, statusCode, response, responseTime, error ->
            runOnUiThread {
                if (error != null) {
                    appendLog("\n[REPOSITION] Error: $error")
                } else if (success) {
                    appendLog("✓ Repositioned! HTTP $statusCode in ${responseTime}ms")
                    appendLog("Response body:")
                    appendLog(response)
                } else {
                    appendLog("✗ HTTP $statusCode")
                    appendLog("Error: $response")
                }
            }
        }
    }

    private fun moveFirstToTomorrow() {
        appendLog("\n[REPOSITION] Moving first todo to tomorrow...")

        todoHelper.moveFirstToTomorrow(Config.WORKSPACE_ID) { success, statusCode, response, responseTime, error ->
            runOnUiThread {
                if (error != null) {
                    appendLog("\n[REPOSITION] Error: $error")
                } else if (success) {
                    appendLog("✓ Repositioned! HTTP $statusCode in ${responseTime}ms")
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
