package com.example.kitkat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var apiHelper: ApiHelper
    private lateinit var todoApi: TodoApi
    private lateinit var listView: ListView
    private lateinit var adapter: TodoAdapter
    private lateinit var celebrationView: ImageView
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var offlineManager: OfflineManager

    private val todos = mutableListOf<TodoItem>()
    private var workspaceId: Int = 444459 // Default workspace ID
    private var currentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private var cachedTodosResponse: String = "" // Cache the full response for date navigation
    private var isSoundEnabled: Boolean = true
    private var isServerLogEnabled: Boolean = true
    private var isShowDoneTasks: Boolean = true // Show done tasks by default
    private var isAnimationEnabled: Boolean = true // Show celebration animations by default
    private var isSequenceEnabled: Boolean = false // Auto-assign sequential hours to new todos
    private val deletedTodosStack = ArrayDeque<TodoItem>(5) // Keep last 5 deleted todos
    private var lastActionTime: Long = 0 // Simple cooldown for all actions
    private var isOnline: Boolean = true // Track online/offline state

    data class TodoItem(
        val id: Int,
        val text: String,
        val done: Boolean,
        val date: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load debug logging preference
        val prefs = getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
        val debugLoggingEnabled = prefs.getBoolean("debug_logging", true)
        LogHelper.setDebugLoggingEnabled(this, debugLoggingEnabled)
        LogHelper.getServerUrl(this) // Load saved server URL
        isServerLogEnabled = debugLoggingEnabled

        // Load sound preference (default ON)
        isSoundEnabled = prefs.getBoolean("sound_enabled", true)

        // Load show done tasks preference (default ON)
        isShowDoneTasks = prefs.getBoolean("show_done_tasks", true)

        // Load animation preference (default ON)
        isAnimationEnabled = prefs.getBoolean("animation_enabled", true)

        // Load sequence preference (default OFF)
        isSequenceEnabled = prefs.getBoolean("sequence_enabled", false)

        // Initialize offline components
        databaseHelper = DatabaseHelper(this)
        networkMonitor = NetworkMonitor(this)
        apiHelper = ApiHelperFactory.create(this)
        offlineManager = OfflineManager(this, apiHelper, networkMonitor, databaseHelper)
        todoApi = TodoApi(apiHelper, databaseHelper, offlineManager)

        // Start monitoring network
        networkMonitor.startMonitoring()
        isOnline = networkMonitor.isOnline()

        // Listen for network state changes
        networkMonitor.addListener { online ->
            isOnline = online
            runOnUiThread {
                updateActionBarForNetworkState()
                if (online) {
                    android.widget.Toast.makeText(this, "Back online - syncing...", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this, "Offline mode", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Listen for sync completion
        offlineManager.addSyncListener { success, error ->
            runOnUiThread {
                if (success) {
                    android.widget.Toast.makeText(this, "Sync completed", android.widget.Toast.LENGTH_SHORT).show()
                    loadTodos() // Refresh after sync
                } else {
                    android.widget.Toast.makeText(this, "Sync failed: $error", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Setup ActionBar with date
        supportActionBar?.apply {
            title = formatDateForDisplay(currentDate)
        }

        // Update ActionBar color depending on date and network state
        updateActionBarForNetworkState()

        // Setup ListView
        listView = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(0xFFDDDDDD.toInt())
            dividerHeight = 1
        }
        adapter = TodoAdapter(this, todos, currentDate)
        listView.adapter = adapter

        // Handle todo item clicks (touch only, keyboard handled in dispatchKeyEvent)
        listView.setOnItemClickListener { _, _, position, _ ->
            val currentTime = System.currentTimeMillis()
            // Simple cooldown - prevent rapid-fire actions
            if (currentTime - lastActionTime < 300) return@setOnItemClickListener
            lastActionTime = currentTime

            val todo = todos[position]
            toggleTodoDone(todo)
        }

        // Handle long-press for context actions
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val currentTime = System.currentTimeMillis()
            //  Simple cooldown - prevent rapid-fire actions
            if (currentTime - lastActionTime < 300) return@setOnItemLongClickListener true
            lastActionTime = currentTime

            val todo = todos[position]
            showTodoItemMenu(todo)
            true
        }

        // Create celebration image view for animations
        // Get screen width and set image to 50% of screen
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val imageSize = (screenWidth * 0.5).toInt()

        celebrationView = ImageView(this).apply {
            visibility = View.INVISIBLE // Use INVISIBLE instead of GONE so view gets measured/laid out
            layoutParams = FrameLayout.LayoutParams(imageSize, imageSize, Gravity.CENTER)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.star) // Set initial image so it gets measured properly
            alpha = 0f // Start invisible
        }

        // Wrap in FrameLayout to overlay celebration on top of list
        val frameLayout = FrameLayout(this).apply {
            addView(listView)
            addView(celebrationView)
        }

        setContentView(frameLayout)

        // Load todos
        loadTodos()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Logout")
        val soundTitle = if (isSoundEnabled) "Sound: On" else "Sound: Off"
        menu.add(0, 2, 1, soundTitle)
        menu.add(0, 3, 2, "Refresh")
        val logTitle = if (isServerLogEnabled) "Server Log: On" else "Server Log: Off"
        menu.add(0, 4, 3, logTitle)
        val doneTodoTitle = if (isShowDoneTasks) "Hide Done" else "Show Done"
        menu.add(0, 6, 4, doneTodoTitle)
        val animationTitle = if (isAnimationEnabled) "Animation: On" else "Animation: Off"
        menu.add(0, 7, 5, animationTitle)
        val sequenceTitle = if (isSequenceEnabled) "Sequence: On" else "Sequence: Off"
        menu.add(0, 8, 6, sequenceTitle)
        val prefs = getSharedPreferences("kitkat_prefs", MODE_PRIVATE)
        val isKeyboardLoggingEnabled = prefs.getBoolean("keyboard_logging", true)
        val keyboardLogTitle = if (isKeyboardLoggingEnabled) "Keyboard Log: On" else "Keyboard Log: Off"
        menu.add(0, 13, 7, keyboardLogTitle)
        // Shift hour actions moved to action bar
        menu.add(0, 9, 8, "Shift hour +1 (all undone)")
        menu.add(0, 10, 9, "Shift hour -1 (all undone)")
        // Reset & Resync option
        menu.add(0, 12, 10, "Reset & Resync")
        // Show sync status if offline and has pending operations
        val pendingCount = offlineManager.getPendingOperationCount()
        if (pendingCount > 0) {
            menu.add(0, 11, 10, "Sync Now ($pendingCount pending)")
        }
        // Only show Undo if there are deleted todos
        if (deletedTodosStack.isNotEmpty()) {
            menu.add(0, 5, 11, "Undo")
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                logout()
                true
            }
            2 -> {
                toggleSound()
                true
            }
            3 -> {
                // Force refresh from API
                cachedTodosResponse = ""
                android.widget.Toast.makeText(this, "Refreshing...", android.widget.Toast.LENGTH_SHORT).show()
                loadTodos()
                true
            }
            4 -> {
                toggleServerLog()
                true
            }
            5 -> {
                undoLastDelete()
                true
            }
            9 -> {
                shiftAllUndoneHours(+1)
                true
            }
            10 -> {
                shiftAllUndoneHours(-1)
                true
            }
            6 -> {
                toggleShowDoneTasks()
                true
            }
            7 -> {
                toggleAnimation()
                true
            }
            8 -> {
                toggleSequence()
                true
            }
            11 -> {
                // Manual sync
                android.widget.Toast.makeText(this, "Starting sync...", android.widget.Toast.LENGTH_SHORT).show()
                offlineManager.syncPendingOperations()
                true
            }
            12 -> {
                // Reset & Resync
                resetAndResync()
                true
            }
            13 -> {
                toggleKeyboardLogging()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop network monitoring
        networkMonitor.stopMonitoring()
    }

    private fun resetAndResync() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Reset & Resync")
        builder.setMessage("This will clear all local data and reload from server. Continue?")
        builder.setPositiveButton("Reset") { dialog, _ ->
            LogHelper.logInfo("HOME", "User requested Reset & Resync")

            // Clear local database
            databaseHelper.clearAllTodos()
            databaseHelper.clearAllOperations()

            // Clear cached response
            cachedTodosResponse = ""

            // Clear todos list
            todos.clear()
            adapter.notifyDataSetChanged()

            // Refresh menu to remove "Sync Now" item
            invalidateOptionsMenu()

            // Show loading message
            android.widget.Toast.makeText(this, "Loading from server...", android.widget.Toast.LENGTH_SHORT).show()

            // Reload todos from server
            loadTodos()

            LogHelper.logInfo("HOME", "Reset complete - reloading from server")
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun toggleServerLog() {
        // If turning ON, show dialog to configure server host
        if (!isServerLogEnabled) {
            showServerHostDialog()
        } else {
            // Turning OFF
            isServerLogEnabled = false
            val prefs = getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("debug_logging", false).apply()
            LogHelper.setDebugLoggingEnabled(this, false)
            invalidateOptionsMenu()
            android.widget.Toast.makeText(this, "Server log: disabled", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showServerHostDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        // Title
        val title = android.widget.TextView(this).apply {
            text = "Server Log Configuration"
            textSize = 18f
            setPadding(0, 0, 0, 16)
            setTextColor(0xFF000000.toInt())
        }
        container.addView(title)

        // Description
        val description = android.widget.TextView(this).apply {
            text = "Enter server URL for logging (e.g., http://192.168.1.25:8080)"
            textSize = 12f
            setPadding(0, 0, 0, 16)
            setTextColor(0xFF666666.toInt())
        }
        container.addView(description)

        // Input field
        val input = android.widget.EditText(this).apply {
            hint = "http://192.168.1.25:8080"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            setText(LogHelper.getServerUrl(this@HomeActivity))
            setSelection(text.length)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Button container
        val buttonContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Cancel button
        val cancelButton = android.widget.Button(this).apply {
            text = "Cancel"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setOnClickListener {
                dialog.dismiss()
            }
        }

        // Save button
        val saveButton = android.widget.Button(this).apply {
            text = "Enable"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setOnClickListener {
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    LogHelper.setServerUrl(this@HomeActivity, url)

                    // Enable server logging
                    isServerLogEnabled = true
                    val prefs = getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("debug_logging", true).apply()
                    LogHelper.setDebugLoggingEnabled(this@HomeActivity, true)
                    invalidateOptionsMenu()

                    LogHelper.logInfo("HOME", "Server logging enabled with URL: $url")
                    android.widget.Toast.makeText(this@HomeActivity, "Server log enabled", android.widget.Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    android.widget.Toast.makeText(this@HomeActivity, "Please enter a valid URL", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonContainer.addView(cancelButton)
        buttonContainer.addView(saveButton)

        container.addView(input)
        container.addView(buttonContainer)
        dialog.setContentView(container)

        dialog.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(android.view.Gravity.CENTER)
        }

        dialog.show()
        input.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleSound() {
        isSoundEnabled = !isSoundEnabled
        val prefs = getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("sound_enabled", isSoundEnabled)
            apply()
        }
        invalidateOptionsMenu()
        val status = if (isSoundEnabled) "ON" else "OFF"
        LogHelper.logInfo("HOME", "Sound $status")
        android.widget.Toast.makeText(this, "Sound: $status", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun toggleKeyboardLogging() {
        val prefs = getSharedPreferences("kitkat_prefs", MODE_PRIVATE)
        val currentValue = prefs.getBoolean("keyboard_logging", true)
        val newValue = !currentValue
        prefs.edit().apply {
            putBoolean("keyboard_logging", newValue)
            apply()
        }
        invalidateOptionsMenu()
        val status = if (newValue) "ON" else "OFF"
        LogHelper.logInfo("HOME", "Keyboard logging $status")
        android.widget.Toast.makeText(this, "Keyboard Log: $status", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun toggleShowDoneTasks() {
        isShowDoneTasks = !isShowDoneTasks
        val prefs = getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("show_done_tasks", isShowDoneTasks)
            apply()
        }
        invalidateOptionsMenu()
        val status = if (isShowDoneTasks) "SHOWN" else "HIDDEN"
        LogHelper.logInfo("HOME", "Done tasks $status")
        android.widget.Toast.makeText(this, "Done tasks: $status", android.widget.Toast.LENGTH_SHORT).show()
        // Re-parse cached response to update display
        if (cachedTodosResponse.isNotEmpty()) {
            parseTodosFromResponse(cachedTodosResponse)
        }
    }

    private fun toggleAnimation() {
        isAnimationEnabled = !isAnimationEnabled
        val prefs = getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("animation_enabled", isAnimationEnabled)
            apply()
        }
        invalidateOptionsMenu()
        val status = if (isAnimationEnabled) "ON" else "OFF"
        LogHelper.logInfo("HOME", "Celebration animations $status")
        android.widget.Toast.makeText(this, "Animation: $status", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun toggleSequence() {
        isSequenceEnabled = !isSequenceEnabled
        val prefs = getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("sequence_enabled", isSequenceEnabled)
            apply()
        }
        invalidateOptionsMenu()
        val status = if (isSequenceEnabled) "ON" else "OFF"
        LogHelper.logInfo("HOME", "Sequential hour assignment $status")
        android.widget.Toast.makeText(this, "Sequence: $status", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        LogHelper.logInfo("HOME", "User logging out, clearing tokens")
        android.widget.Toast.makeText(this, "Logging out...", android.widget.Toast.LENGTH_SHORT).show()

        // Clear saved tokens
        val prefs = getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("auth_token")
            remove("refresh_token")
            apply()
        }

        // Redirect to login screen
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Consume MENU key completely to prevent opening options menu
        if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == 301) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                LogHelper.logInfo("KEYBOARD", "Key: ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode})")
                showAddTodoDialog()
            }
            return true // Consume both DOWN and UP events
        }

        // Handle Enter key - just toggle done
        if (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (event.action == KeyEvent.ACTION_UP) {
                val currentTime = System.currentTimeMillis()
                // Simple cooldown - prevent rapid-fire actions
                if (currentTime - lastActionTime < 300) return true
                lastActionTime = currentTime

                val position = listView.selectedItemPosition
                if (position >= 0 && position < todos.size) {
                    val todo = todos[position]
                    toggleTodoDone(todo)
                }
                return true
            }
            return true // Consume DOWN and other actions
        }

        // Handle DEL key - show context menu
        if (event.keyCode == KeyEvent.KEYCODE_DEL) {
            if (event.action == KeyEvent.ACTION_UP) {
                val currentTime = System.currentTimeMillis()
                // Simple cooldown - prevent rapid-fire actions
                if (currentTime - lastActionTime < 300) return true
                lastActionTime = currentTime

                val position = listView.selectedItemPosition
                if (position >= 0 && position < todos.size) {
                    val todo = todos[position]
                    showTodoItemMenu(todo)
                }
                return true
            }
            return true // Consume DOWN too
        }

        // Log all key events to server (if enabled) - both DOWN and UP
        val prefs = getSharedPreferences("kitkat_prefs", MODE_PRIVATE)
        val isKeyboardLoggingEnabled = prefs.getBoolean("keyboard_logging", true)

        if (isKeyboardLoggingEnabled) {
            val actionName = when (event.action) {
                KeyEvent.ACTION_DOWN -> "DOWN"
                KeyEvent.ACTION_UP -> "UP"
                else -> "ACTION_${event.action}"
            }
            val keyInfo = buildString {
                append("$actionName: ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode})")
                append(", Char: '${event.unicodeChar.toChar()}'")
                append(", Unicode: ${event.unicodeChar}")
                append(", Repeat: ${event.repeatCount}")
            }
            LogHelper.logInfo("KEYBOARD", keyInfo)
        }

        // Ignore key-repeat for navigation keys to prevent double navigation
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0 &&
                (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                return true
            }

            // Handle date navigation with arrow keys
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    navigateToPreviousDay()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    navigateToNextDay()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun navigateToPreviousDay() {
        LogHelper.logInfo("HOME", "Navigating to previous day (H-1)")
        changeDate(-1)
    }

    private fun navigateToNextDay() {
        LogHelper.logInfo("HOME", "Navigating to next day (H+1)")
        changeDate(1)
    }

    private fun changeDate(dayOffset: Int) {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val currentDateObj = format.parse(currentDate) ?: Date()
            val calendar = Calendar.getInstance()
            calendar.time = currentDateObj
            calendar.add(Calendar.DAY_OF_YEAR, dayOffset)

            currentDate = format.format(calendar.time)

            // Recreate adapter with new date
            adapter = TodoAdapter(this, todos, currentDate)
            listView.adapter = adapter

            // Update ActionBar title
            supportActionBar?.title = formatDateForDisplay(currentDate)

            // Update ActionBar color depending on date and network state
            updateActionBarForNetworkState()

            LogHelper.logInfo("HOME", "Date changed to $currentDate")

            // Re-parse cached response for new date
            if (cachedTodosResponse.isNotEmpty()) {
                parseTodosFromResponse(cachedTodosResponse)
            } else {
                // If no cache, reload
                loadTodos()
            }
        } catch (e: Exception) {
            LogHelper.logError("HOME", "Error changing date: ${e.message}", 0, e.stackTraceToString())
        }
    }

    private fun updateActionBarColorForCurrentDate() {
        val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val isToday = currentDate == todayString
        val color = if (isToday) 0xFF800000.toInt() else 0xFF333333.toInt()
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
    }

    private fun updateActionBarForNetworkState() {
        val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val isToday = currentDate == todayString

        val color = if (!isOnline) {
            // Black for offline mode
            0xFF000000.toInt()
        } else if (isToday) {
            // Maroon for today when online
            0xFF800000.toInt()
        } else {
            // Dark grey for other dates when online
            0xFF333333.toInt()
        }

        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))

        // Update title to show offline indicator
        val dateTitle = formatDateForDisplay(currentDate)
        val titleWithStatus = if (!isOnline) {
            val pendingCount = offlineManager.getPendingOperationCount()
            if (pendingCount > 0) {
                "$dateTitle • Offline ($pendingCount)"
            } else {
                "$dateTitle • Offline"
            }
        } else {
            dateTitle
        }
        supportActionBar?.title = titleWithStatus
    }

    private fun getThemePrimaryColor(): Int {
        val typedValue = TypedValue()
        val theme = theme
        val attr = androidx.appcompat.R.attr.colorPrimary
        return if (theme.resolveAttribute(attr, typedValue, true)) typedValue.data else 0xFF000000.toInt()
    }

    private fun formatDateForDisplay(dateString: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = inputFormat.parse(dateString) ?: Date()

        val calendar = Calendar.getInstance()
        calendar.time = date
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val suffix = getDayOrdinalSuffix(dayOfMonth)

        val dayShort = SimpleDateFormat("EEE", Locale.US).format(date)
        val monthShort = SimpleDateFormat("MMM", Locale.US).format(date)
        return "$dayShort ${dayOfMonth}${suffix} $monthShort"
    }

    private fun getDayOrdinalSuffix(day: Int): String {
        if (day in 11..13) return "th"
        return when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }

    private fun extractHourCode(text: String): Int? {
        // Match @0 to @24 anywhere in the text
        val regex = """@(\d+)""".toRegex()
        val match = regex.find(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        return if (hour in 0..24) hour else null
    }

    private fun replaceNowShortcuts(text: String): String {
        // Replace @now or @n with current hour
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return text.replace("""@now\b""".toRegex(RegexOption.IGNORE_CASE), "@$currentHour")
                   .replace("""@n\b""".toRegex(RegexOption.IGNORE_CASE), "@$currentHour")
    }

    private fun loadTodos() {
        // Calculate date range: H-3 to H+3
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -3)
        val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 6) // +6 days from H-3 = H+3
        val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        // Step 1: Load from local database immediately (instant feedback)
        loadTodosFromLocalDatabase(startDate, endDate)

        // Step 2: Show loading emoji in title
        updateActionBarWithLoading(true)

        // Step 3: Fetch from server in background
        LogHelper.logInfo("HOME", "Syncing todos from server: $startDate to $endDate")
        todoApi.listTodos(workspaceId, startDate, endDate) { success, statusCode, response, responseTime ->
            if (success) {
                cachedTodosResponse = response // Cache for date navigation
                parseTodosFromResponse(response)
                LogHelper.logSuccess("HOME", "Todos synced successfully", responseTime, "Count: ${todos.size}")
            } else {
                LogHelper.logError("HOME", "Failed to sync todos", responseTime, response.take(500))
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Failed to sync with server", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            // Step 4: Remove loading emoji
            runOnUiThread {
                updateActionBarWithLoading(false)
            }
        }
    }

    private fun loadTodosFromLocalDatabase(startDate: String, endDate: String) {
        // Load cached todos from database for instant display
        val localTodos = databaseHelper.getTodosInRange(startDate, endDate)

        if (localTodos.isNotEmpty()) {
            todos.clear()
            for (localTodo in localTodos) {
                // Only show todos for current date
                if (localTodo.date == currentDate) {
                    if (isShowDoneTasks || !localTodo.done) {
                        // Use serverId if available, otherwise use localId
                        val id = localTodo.serverId ?: localTodo.localId.toInt()
                        todos.add(TodoItem(id, localTodo.text, localTodo.done, localTodo.date))
                    }
                }
            }

            // Sort same as server data
            todos.sortWith(compareBy(
                { it.done },
                { extractHourCode(it.text) ?: 25 },
                { it.text }
            ))

            runOnUiThread {
                adapter.notifyDataSetChanged()
                LogHelper.logInfo("HOME", "Loaded ${todos.size} todos from local cache")
            }
        }
    }

    private fun updateActionBarWithLoading(isLoading: Boolean) {
        runOnUiThread {
            val loadingEmoji = "⏳ " // Hourglass emoji
            val baseTitle = formatDateForDisplay(currentDate)

            supportActionBar?.title = if (isLoading) {
                "$loadingEmoji$baseTitle"
            } else {
                baseTitle
            }
        }
    }

    private fun parseTodosFromResponse(response: String) {
        try {
            // Parse JSON response to extract todos for current date only
            todos.clear()

            // Split by todo objects (each starts with {"cid")
            val todoObjects = response.split("""{"cid"""").drop(1) // Drop first empty element

            for (todoObj in todoObjects) {
                try {
                    // Extract fields
                    val idMatch = """"id":(\d+)""".toRegex().find(todoObj)
                    val dateMatch = """"current_date":"([^"]+)"""".toRegex().find(todoObj)
                    val doneMatch = """"done":(true|false)""".toRegex().find(todoObj)
                    val textMatch = """"text":"([^"]*?)"""".toRegex().find(todoObj)

                    if (idMatch != null && dateMatch != null && doneMatch != null && textMatch != null) {
                        val id = idMatch.groupValues[1].toInt()
                        val date = dateMatch.groupValues[1]
                        val done = doneMatch.groupValues[1] == "true"
                        val text = textMatch.groupValues[1]

                        // Only add todos for current date, and filter done tasks if needed
                        if (date == currentDate) {
                            if (isShowDoneTasks || !done) {
                                todos.add(TodoItem(id, text, done, currentDate))
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogHelper.logError("HOME", "Error parsing todo object: ${e.message}", 0, null)
                }
            }

            // Sort: undone (hour-coded, then non-coded), then done (hour-coded, then non-coded)
            todos.sortWith(compareBy(
                { it.done }, // Done items last
                { extractHourCode(it.text) ?: 25 }, // Hour code for both done and undone (25 = no code)
                { it.text } // Alphabetical as tiebreaker
            ))

            runOnUiThread {
                adapter.notifyDataSetChanged()
                LogHelper.logInfo("HOME", "Parsed ${todos.size} todos for $currentDate")
            }
        } catch (e: Exception) {
            LogHelper.logError("HOME", "Error parsing todos: ${e.message}", 0, e.stackTraceToString())
        }
    }

    private fun toggleTodoDone(todo: TodoItem) {
        val newDoneState = !todo.done

        todoApi.toggleTodoDone(workspaceId, todo.id, newDoneState) { success, statusCode, response, responseTime ->
            if (success) {
                // Update local state
                val index = todos.indexOfFirst { it.id == todo.id }
                if (index != -1) {
                    runOnUiThread {
                        // Update item state
                        todos[index] = todo.copy(done = newDoneState)
                        // Re-sort: undone (hour-coded, then non-coded), then done (hour-coded, then non-coded)
                        todos.sortWith(compareBy(
                            { it.done },
                            { extractHourCode(it.text) ?: 25 },
                            { it.text }
                        ))
                        // Rebuild the ListView
                        adapter.notifyDataSetChanged()

                        // Play sound and show animation if marking as done
                        if (newDoneState) {
                            val allDone = todos.isNotEmpty() && todos.all { it.done }
                            playDoneSound(allDone)

                            // Show celebration emoji animation
                            if (allDone) {
                                showAllDoneCelebration()
                            } else {
                                showCelebration()
                            }
                        }
                    }
                }
                LogHelper.logSuccess("HOME", "Todo toggled", responseTime, "ID: ${todo.id}, done: $newDoneState")
            } else {
                LogHelper.logError("HOME", "Failed to toggle todo", responseTime, response.take(500))
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Failed to update todo", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun playDoneSound(allDone: Boolean) {
        if (!isSoundEnabled) return

        val fileName = if (allDone) "smb_stage_clear" else "smb_coin"
        LogHelper.logInfo("SOUND", "playDoneSound called - allDone=$allDone, file=$fileName")

        // Try res/raw first
        val resId = resources.getIdentifier(fileName, "raw", packageName)
        if (resId != 0) {
            val mp = android.media.MediaPlayer.create(this, resId)
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
            return
        }

        // Fallback to assets: sound/<fileName>.wav or <fileName>.wav
        try {
            val afd = try {
                assets.openFd("sound/${fileName}.wav")
            } catch (_: Exception) {
                assets.openFd("${fileName}.wav")
            }
            val mp = android.media.MediaPlayer()
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setOnCompletionListener { it.release() }
            mp.prepare()
            mp.start()
        } catch (_: Exception) {
            // Ignore if asset not found or playback fails
        }
    }

    private fun showCelebration() {
        if (!isAnimationEnabled) return

        LogHelper.logInfo("ANIMATION", "showCelebration() called - star image")
        runOnUiThread {
            try {
                // Show star/coin image
                celebrationView.setImageResource(R.drawable.star)
                celebrationView.bringToFront()
                celebrationView.visibility = View.VISIBLE
                celebrationView.alpha = 0f
                celebrationView.scaleX = 0f
                celebrationView.scaleY = 0f

                LogHelper.logInfo("ANIMATION", "Starting star animation - view size: ${celebrationView.width}x${celebrationView.height}")

                // Use ViewPropertyAnimator for better KitKat compatibility
                celebrationView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(600)
                    .setInterpolator(BounceInterpolator())
                    .withEndAction {
                        LogHelper.logInfo("ANIMATION", "Star bounce complete, holding...")
                        // Hold for a moment, then fade out
                        celebrationView.postDelayed({
                            celebrationView.animate()
                                .alpha(0f)
                                .setDuration(400)
                                .setInterpolator(null)
                                .withEndAction {
                                    celebrationView.visibility = View.INVISIBLE
                                    celebrationView.scaleX = 0f
                                    celebrationView.scaleY = 0f
                                    LogHelper.logInfo("ANIMATION", "Star animation ended")
                                }
                                .start()
                        }, 800)
                    }
                    .start()

                LogHelper.logInfo("ANIMATION", "Star animation started via ViewPropertyAnimator")
            } catch (e: Exception) {
                LogHelper.logError("ANIMATION", "Failed to show celebration: ${e.message}", 0, e.stackTraceToString())
            }
        }
    }

    private fun showAllDoneCelebration() {
        if (!isAnimationEnabled) return

        LogHelper.logInfo("ANIMATION", "showAllDoneCelebration() called - level up image")
        runOnUiThread {
            try {
                // Show level up image
                celebrationView.setImageResource(R.drawable.levelup)
                celebrationView.bringToFront()
                celebrationView.visibility = View.VISIBLE
                celebrationView.alpha = 0f
                celebrationView.scaleX = 0f
                celebrationView.scaleY = 0f

                LogHelper.logInfo("ANIMATION", "Starting level up animation - view size: ${celebrationView.width}x${celebrationView.height}")

                // Use ViewPropertyAnimator for better KitKat compatibility (larger scale for "level up")
                celebrationView.animate()
                    .alpha(1f)
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(800)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        LogHelper.logInfo("ANIMATION", "Level up scale complete, holding...")
                        // Hold for a moment, then fade out
                        celebrationView.postDelayed({
                            celebrationView.animate()
                                .alpha(0f)
                                .setDuration(500)
                                .setInterpolator(null)
                                .withEndAction {
                                    celebrationView.visibility = View.INVISIBLE
                                    celebrationView.scaleX = 0f
                                    celebrationView.scaleY = 0f
                                    LogHelper.logInfo("ANIMATION", "Level up animation ended")
                                }
                                .start()
                        }, 1000)
                    }
                    .start()

                LogHelper.logInfo("ANIMATION", "Level up animation started via ViewPropertyAnimator")
            } catch (e: Exception) {
                LogHelper.logError("ANIMATION", "Failed to show all done celebration: ${e.message}", 0, e.stackTraceToString())
            }
        }
    }

    private fun showAddTodoDialog() {
        runOnUiThread {
            val dialog = android.app.Dialog(this)
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            dialog.setCanceledOnTouchOutside(true)

            // Container styling
            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                setBackgroundColor(0xFFFFFFFF.toInt())
            }

            // Input field
            val input = android.widget.EditText(this).apply {
                hint = "Add a todo"
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                isSingleLine = true
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Submit on Enter (IME action Done)
            input.setOnEditorActionListener { v, actionId, event ->
                val isEnterKey = event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN
                val isImeDone = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                if (isEnterKey || isImeDone) {
                    val text = input.text.toString().trim()
                    if (text.isNotEmpty()) {
                        createNewTodo(text)
                        lastActionTime = System.currentTimeMillis()
                        dialog.dismiss()
                    } else {
                        android.widget.Toast.makeText(this, "Todo text cannot be empty", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    true
                } else {
                    false
                }
            }

            // Also submit on D-pad center key
            input.setOnKeyListener { _, keyCode, keyEvent ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER && keyEvent.action == android.view.KeyEvent.ACTION_UP) {
                    val text = input.text.toString().trim()
                    if (text.isNotEmpty()) {
                        createNewTodo(text)
                        lastActionTime = System.currentTimeMillis()
                        dialog.dismiss()
                    } else {
                        android.widget.Toast.makeText(this, "Todo text cannot be empty", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    true
                } else {
                    false
                }
            }

            // Watch for newline being added to text (handles phones that don't trigger IME action)
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (s != null && s.isNotEmpty() && s.last() == '\n') {
                        val text = s.toString().trim()
                        if (text.isNotEmpty()) {
                            createNewTodo(text)
                            lastActionTime = System.currentTimeMillis()
                            dialog.dismiss()
                        } else {
                            android.widget.Toast.makeText(this@HomeActivity, "Todo text cannot be empty", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })

            container.addView(input)
            dialog.setContentView(container)

            // Position at bottom and size to width
            dialog.window?.apply {
                setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(android.view.Gravity.BOTTOM)
                clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }

            // Handle Back key to close (cancel) and consume Enter to prevent leaking
            dialog.setOnKeyListener { d, keyCode, keyEvent ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK && keyEvent.action == android.view.KeyEvent.ACTION_UP) {
                    d.dismiss()
                    true
                } else if (keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                    // Consume all Enter/DPAD_CENTER events to prevent leaking to main activity
                    true
                } else {
                    false
                }
            }

            dialog.show()

            // Focus and show keyboard
            input.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showTodoItemMenu(todo: TodoItem) {
        val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val isToday = currentDate == todayString

        val options = mutableListOf("Prioritize", "Edit", "Set to Now", "Remove Hour")
        if (isToday) {
            options.add("Move to Tomorrow")
        } else {
            options.add("Move to Today")
        }
        options.add("Delete")

        val builder = android.app.AlertDialog.Builder(this)
        builder.setItems(options.toTypedArray()) { dialog, which ->
            when (options[which]) {
                "Delete" -> deleteTodo(todo)
                "Edit" -> showEditTodoDialog(todo)
                "Prioritize" -> togglePrioritize(todo)
                "Set to Now" -> setToNow(todo)
                "Remove Hour" -> removeHour(todo)
                "Shift hour +1 (all undone)" -> shiftAllUndoneHours(+1)
                "Shift hour -1 (all undone)" -> shiftAllUndoneHours(-1)
                "Move to Tomorrow" -> moveTodoToDate(todo, getDateOffsetString(1))
                "Move to Today" -> moveTodoToDate(todo, todayString)
            }
            dialog.dismiss()
        }
        builder.show()
    }

    // Shift hour tags for all undone todos on current date by the provided delta (+1 or -1)
    private fun shiftAllUndoneHours(delta: Int) {
        // Build list of updates first (local), only for undone todos that have hour tags
        val itemsToUpdate = todos.filter { !it.done }
            .mapNotNull { item ->
                val hour = extractHourCode(item.text)
                if (hour != null) Pair(item, hour) else null
            }

        if (itemsToUpdate.isEmpty()) {
            runOnUiThread {
                android.widget.Toast.makeText(this, "No hour-tagged undone todos", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Sequentially process updates to keep network simple and avoid rate bursts
        fun clampHour(h: Int): Int = when {
            h < 0 -> 0
            h > 24 -> 24
            else -> h
        }

        var index = 0
        var successCount = 0
        var failCount = 0

        fun processNext() {
            if (index >= itemsToUpdate.size) {
                // Done; refresh UI
                cachedTodosResponse = ""
                loadTodos()
                val msg = "Shifted: $successCount, Failed: $failCount"
                runOnUiThread {
                    android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
                return
            }

            val (item, hour) = itemsToUpdate[index]
            index++

            val newHour = clampHour(hour + delta)
            if (newHour == hour) {
                processNext()
                return
            }

            // Remove existing hour tag and rebuild text with new hour at the front
            val regex = """@(\d+)\s*""".toRegex()
            val textWithoutHour = item.text.replace(regex, "").trim()
            val newText = "@${newHour} ${textWithoutHour}".trim()

            todoApi.updateTodo(workspaceId, item.id, newText) { success, statusCode, response, responseTime ->
                if (success) {
                    successCount++
                } else {
                    failCount++
                }
                processNext()
            }
        }

        processNext()
    }

    private fun moveTodoToDate(todo: TodoItem, targetDate: String) {
        todoApi.repositionTodo(workspaceId, todo.id, targetDate, 0) { success, statusCode, response, responseTime ->
            if (success) {
                // Clear cache and reload to ensure database changes are reflected
                cachedTodosResponse = ""
                loadTodos() // Reload from database (offline) or server (online)
                LogHelper.logSuccess("HOME", "Todo moved to $targetDate", responseTime, "ID: ${todo.id}")
            } else {
                LogHelper.logError("HOME", "Failed to move todo", responseTime, response.take(500))
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Failed to move todo", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setToNow(todo: TodoItem) {
        val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Remove existing hour tag from text
        val regex = """@(\d+)\s*""".toRegex()
        val textWithoutHour = todo.text.replace(regex, "").trim()

        // Add current hour as prefix
        val newText = "@$currentHour $textWithoutHour"

        LogHelper.logInfo("HOME", "Setting to now: '$newText' (hour: $currentHour)")

        // If not on today's date, move to today first, then update text
        if (todo.date != todayString) {
            // Move to today, then update text
            todoApi.repositionTodo(workspaceId, todo.id, todayString, 0) { success, statusCode, response, responseTime ->
                if (success) {
                    // Now update the text with current hour
                    todoApi.updateTodo(workspaceId, todo.id, newText) { updateSuccess, updateStatusCode, updateResponse, updateResponseTime ->
                        if (updateSuccess) {
                            cachedTodosResponse = ""
                            runOnUiThread {
                                todos.removeAll { it.id == todo.id }
                                adapter.notifyDataSetChanged()
                                android.widget.Toast.makeText(this, "Set to now: @$currentHour", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            LogHelper.logSuccess("HOME", "Todo set to now", updateResponseTime, "Text: $newText")
                        } else {
                            LogHelper.logError("HOME", "Failed to update text after move", updateResponseTime, updateResponse.take(500))
                        }
                    }
                } else {
                    LogHelper.logError("HOME", "Failed to move to today", responseTime, response.take(500))
                    runOnUiThread {
                        android.widget.Toast.makeText(this, "Failed to set to now", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            // Already on today, just update text
            todoApi.updateTodo(workspaceId, todo.id, newText) { success, statusCode, response, responseTime ->
                if (success) {
                    cachedTodosResponse = ""
                    loadTodos()
                    runOnUiThread {
                        android.widget.Toast.makeText(this, "Set to now: @$currentHour", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    LogHelper.logSuccess("HOME", "Todo set to now", responseTime, "Text: $newText")
                } else {
                    LogHelper.logError("HOME", "Failed to update todo", responseTime, response.take(500))
                    runOnUiThread {
                        android.widget.Toast.makeText(this, "Failed to set to now", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun removeHour(todo: TodoItem) {
        // Remove existing hour tag from text
        val regex = """@(\d+)\s*""".toRegex()
        val textWithoutHour = todo.text.replace(regex, "").trim()

        // Check if there was actually an hour tag to remove
        if (textWithoutHour == todo.text.trim()) {
            runOnUiThread {
                android.widget.Toast.makeText(this, "No hour tag to remove", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        LogHelper.logInfo("HOME", "Removing hour tag: '$textWithoutHour'")

        todoApi.updateTodo(workspaceId, todo.id, textWithoutHour) { success, statusCode, response, responseTime ->
            if (success) {
                cachedTodosResponse = ""
                loadTodos()
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Hour removed", android.widget.Toast.LENGTH_SHORT).show()
                }
                LogHelper.logSuccess("HOME", "Hour tag removed", responseTime, "Text: $textWithoutHour")
            } else {
                LogHelper.logError("HOME", "Failed to remove hour", responseTime, response.take(500))
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Failed to remove hour", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getDateOffsetString(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    private fun showEditTodoDialog(todo: TodoItem) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val input = android.widget.EditText(this).apply {
            hint = "Edit todo"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setText(todo.text)
            setSelection(text.length)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        input.setOnEditorActionListener { _, actionId, event ->
            val isEnterKey = event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN
            val isImeDone = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            if (isEnterKey || isImeDone) {
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) {
                    lastActionTime = System.currentTimeMillis()
                    updateTodoText(todo, newText) { dialog.dismiss() }
                } else {
                    android.widget.Toast.makeText(this, "Todo text cannot be empty", android.widget.Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }

        // Also submit on D-pad center key
        input.setOnKeyListener { _, keyCode, keyEvent ->
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER && keyEvent.action == android.view.KeyEvent.ACTION_UP) {
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty()) {
                    lastActionTime = System.currentTimeMillis()
                    updateTodoText(todo, newText) { dialog.dismiss() }
                } else {
                    android.widget.Toast.makeText(this, "Todo text cannot be empty", android.widget.Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }

        // Watch for newline being added to text (handles phones that don't trigger IME action)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s != null && s.isNotEmpty() && s.last() == '\n') {
                    val newText = s.toString().trim()
                    if (newText.isNotEmpty()) {
                        lastActionTime = System.currentTimeMillis()
                        updateTodoText(todo, newText) { dialog.dismiss() }
                    } else {
                        android.widget.Toast.makeText(this@HomeActivity, "Todo text cannot be empty", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        container.addView(input)
        dialog.setContentView(container)

        dialog.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(android.view.Gravity.BOTTOM)
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        dialog.setOnKeyListener { d, keyCode, keyEvent ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && keyEvent.action == android.view.KeyEvent.ACTION_UP) {
                d.dismiss()
                true
            } else if (keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                // Consume all Enter/DPAD_CENTER events to prevent leaking to main activity
                true
            } else {
                false
            }
        }

        dialog.show()
        input.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateTodoText(todo: TodoItem, newText: String, onDone: () -> Unit = {}) {
        // Replace @now and @n with current hour
        val finalText = replaceNowShortcuts(newText)

        todoApi.updateTodo(workspaceId, todo.id, finalText) { success, statusCode, response, responseTime ->
            if (success) {
                val index = todos.indexOfFirst { it.id == todo.id }
                if (index != -1) {
                    runOnUiThread {
                        todos[index] = todo.copy(text = finalText)
                        adapter.notifyDataSetChanged()
                    }
                }
                LogHelper.logSuccess("HOME", "Todo updated", responseTime, "ID: ${todo.id}")
            } else {
                LogHelper.logError("HOME", "Failed to update todo", responseTime, response.take(500))
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Failed to update todo", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            onDone()
        }
    }

    private fun deleteTodo(todo: TodoItem) {
        // Save to deleted stack before deleting (max 5)
        deletedTodosStack.addFirst(todo)
        if (deletedTodosStack.size > 5) {
            deletedTodosStack.removeLast()
        }
        invalidateOptionsMenu() // Update menu to show/hide Undo

        todoApi.deleteTodo(workspaceId, todo.id) { success, statusCode, response, responseTime ->
            if (success) {
                runOnUiThread {
                    todos.removeAll { it.id == todo.id }
                    adapter.notifyDataSetChanged()
                }
                LogHelper.logSuccess("HOME", "Todo deleted", responseTime, "ID: ${todo.id}")
            } else {
                LogHelper.logError("HOME", "Failed to delete todo", responseTime, response.take(500))
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Failed to delete todo", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun undoLastDelete() {
        if (deletedTodosStack.isEmpty()) return

        val todo = deletedTodosStack.removeFirst()
        invalidateOptionsMenu() // Update menu to show/hide Undo

        // Re-create the todo with the same text and date
        todoApi.createTodo(workspaceId, todo.text, todo.date) { success, statusCode, response, responseTime ->
            if (success) {
                LogHelper.logSuccess("HOME", "Todo restored", responseTime, "Text: ${todo.text}")
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Todo restored", android.widget.Toast.LENGTH_SHORT).show()
                }
                // Reload todos to show the restored one
                loadTodos()
            } else {
                LogHelper.logError("HOME", "Failed to restore todo", responseTime, response.take(500))
                // Put it back in the stack if restore failed
                deletedTodosStack.addFirst(todo)
                invalidateOptionsMenu()
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Failed to restore todo", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun togglePrioritize(todo: TodoItem) {
        val rocket = "🚀 "
        val isPrioritized = todo.text.startsWith(rocket) || todo.text.startsWith("🚀")
        val newText = if (isPrioritized) todo.text.removePrefix(rocket).removePrefix("🚀") else rocket + todo.text

        // First, update text to add/remove rocket
        todoApi.updateTodo(workspaceId, todo.id, newText) { success, statusCode, response, responseTime ->
            if (success) {
                val index = todos.indexOfFirst { it.id == todo.id }
                if (index != -1) {
                    runOnUiThread {
                        todos[index] = todo.copy(text = newText)
                        adapter.notifyDataSetChanged()
                    }
                }
                LogHelper.logSuccess("HOME", "Todo prioritized toggled", responseTime, "ID: ${todo.id}")

                // If we just prioritized (added rocket), move to position 0
                if (!isPrioritized) {
                    todoApi.repositionTodo(workspaceId, todo.id, currentDate, 0) { moveSuccess, _, _, moveTime ->
                        if (moveSuccess) {
                            runOnUiThread {
                                val fromIdx = todos.indexOfFirst { it.id == todo.id }
                                if (fromIdx != -1) {
                                    val item = todos.removeAt(fromIdx)
                                    // Insert respecting undone-first grouping
                                    if (item.done) {
                                        val firstDoneIndex = todos.indexOfFirst { it.done }
                                        val insertIndex = if (firstDoneIndex == -1) todos.size else firstDoneIndex
                                        todos.add(insertIndex, item)
                                    } else {
                                        todos.add(0, item)
                                    }
                                    adapter.notifyDataSetChanged()
                                }
                            }
                            LogHelper.logSuccess("HOME", "Todo moved to top", moveTime, "ID: ${todo.id}")
                        } else {
                            LogHelper.logError("HOME", "Failed to reposition todo", moveTime)
                        }
                    }
                }
            } else {
                LogHelper.logError("HOME", "Failed to toggle prioritize", responseTime, response.take(500))
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Failed to update todo", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createNewTodo(text: String) {
        // Replace @now and @n with current hour
        var finalText = replaceNowShortcuts(text)

        // If sequence mode is enabled, auto-assign hour tag
        if (isSequenceEnabled) {
            val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            // Only auto-assign hours if creating on today's date
            if (currentDate == todayString) {
                // Find all undone todos with hour tags
                val undoneTodosWithHours = todos.filter { !it.done && extractHourCode(it.text) != null }

                val hourToAssign = if (undoneTodosWithHours.isEmpty()) {
                    // No undone hour-tagged todos → use current hour
                    Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                } else {
                    // Find max hour from undone todos and add 1
                    val maxHour = undoneTodosWithHours.mapNotNull { extractHourCode(it.text) }.maxOrNull() ?: 0
                    maxHour + 1
                }

                // Prepend hour tag to text
                finalText = "@$hourToAssign $text"
                LogHelper.logInfo("HOME", "Sequence mode: auto-assigned hour @$hourToAssign")
            }
        }

        LogHelper.logInfo("HOME", "Creating new todo: $finalText")

        todoApi.createTodo(workspaceId, finalText, currentDate) { success, statusCode, response, responseTime ->
            if (success) {
                LogHelper.logSuccess("HOME", "Todo created successfully", responseTime, "Text: $finalText")
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Todo added", android.widget.Toast.LENGTH_SHORT).show()
                }
                // Reload todos to show the new one
                loadTodos()
            } else {
                LogHelper.logError("HOME", "Failed to create todo", responseTime, response.take(500))
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Failed to add todo", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Custom adapter for todo items
    private class TodoAdapter(
        context: Context,
        private val todos: List<TodoItem>,
        private val currentDate: String
    ) : ArrayAdapter<TodoItem>(context, 0, todos) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val todo = getItem(position)!!

            val textView = (convertView as? TextView) ?: TextView(context).apply {
                textSize = 14f
                setPadding(16, 8, 16, 8) // Compact: reduced from (32, 24, 32, 24)
            }

            textView.text = if (todo.done) {
                "✓ ${todo.text}"
            } else {
                todo.text
            }

            // Strike through if done
            textView.paintFlags = if (todo.done) {
                textView.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                textView.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            // Check if this is current hour task on today's date
            val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val isToday = currentDate == todayString
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val hourCode = extractHourCode(todo.text)
            val isCurrentHourTask = isToday && !todo.done && hourCode == currentHour
            val hasHourCode = !todo.done && hourCode != null

            if (isCurrentHourTask) {
                // Yellow background with state-aware variations for current hour task
                val stateListDrawable = android.graphics.drawable.StateListDrawable()
                stateListDrawable.addState(
                    intArrayOf(android.R.attr.state_focused),
                    android.graphics.drawable.ColorDrawable(0xFFFFDD88.toInt()) // Darker yellow when focused
                )
                stateListDrawable.addState(
                    intArrayOf(android.R.attr.state_selected),
                    android.graphics.drawable.ColorDrawable(0xFFFFDD88.toInt()) // Darker yellow when selected
                )
                stateListDrawable.addState(
                    intArrayOf(android.R.attr.state_pressed),
                    android.graphics.drawable.ColorDrawable(0xFFFFDD88.toInt()) // Darker yellow when pressed
                )
                stateListDrawable.addState(
                    intArrayOf(),
                    android.graphics.drawable.ColorDrawable(0xFFFFFFCC.toInt()) // Light yellow default
                )
                textView.background = stateListDrawable
                textView.setTextColor(0xFF800000.toInt()) // Maroon
            } else if (hasHourCode) {
                // Light green background for assigned hour tasks (not current hour)
                val stateListDrawable = android.graphics.drawable.StateListDrawable()
                stateListDrawable.addState(
                    intArrayOf(android.R.attr.state_focused),
                    android.graphics.drawable.ColorDrawable(0xFFD0E8D0.toInt()) // Darker green when focused
                )
                stateListDrawable.addState(
                    intArrayOf(android.R.attr.state_selected),
                    android.graphics.drawable.ColorDrawable(0xFFD0E8D0.toInt()) // Darker green when selected
                )
                stateListDrawable.addState(
                    intArrayOf(android.R.attr.state_pressed),
                    android.graphics.drawable.ColorDrawable(0xFFD0E8D0.toInt()) // Darker green when pressed
                )
                stateListDrawable.addState(
                    intArrayOf(),
                    android.graphics.drawable.ColorDrawable(0xFFE8F5E9.toInt()) // Light green default
                )
                textView.background = stateListDrawable
                textView.setTextColor(0xFF333333.toInt()) // Darker gray text
            } else {
                // Default styling with state-aware variations
                val stateListDrawable = android.graphics.drawable.StateListDrawable()
                stateListDrawable.addState(
                    intArrayOf(android.R.attr.state_focused),
                    android.graphics.drawable.ColorDrawable(0xFFDDDDDD.toInt()) // Light gray when focused
                )
                stateListDrawable.addState(
                    intArrayOf(android.R.attr.state_selected),
                    android.graphics.drawable.ColorDrawable(0xFFDDDDDD.toInt()) // Light gray when selected
                )
                stateListDrawable.addState(
                    intArrayOf(android.R.attr.state_pressed),
                    android.graphics.drawable.ColorDrawable(0xFFDDDDDD.toInt()) // Light gray when pressed
                )
                stateListDrawable.addState(
                    intArrayOf(),
                    android.graphics.drawable.ColorDrawable(0x00000000.toInt()) // Transparent default
                )
                textView.background = stateListDrawable
                textView.setTextColor(if (todo.done) 0xFF999999.toInt() else 0xFF000000.toInt())
            }

            return textView
        }

        private fun extractHourCode(text: String): Int? {
            val regex = """@(\d+)""".toRegex()
            val match = regex.find(text) ?: return null
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            return if (hour in 0..24) hour else null
        }
    }
}
