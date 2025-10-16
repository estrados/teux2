package com.example.kitkat

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

object LogHelper {
    private const val TAG = "KitKatSSL"
    private const val VERSION = "v1.0.8" // App version for debugging

    // Default server URL
    private const val DEFAULT_SERVER_URL = "http://192.168.1.25:8080"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Flag to control server logging
    @Volatile
    private var isDebugLoggingEnabled = true

    // Configurable server URL
    @Volatile
    private var serverUrl = DEFAULT_SERVER_URL

    fun setDebugLoggingEnabled(context: Context, enabled: Boolean) {
        isDebugLoggingEnabled = enabled
        Log.d(TAG, "Debug logging to server: ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    fun setServerUrl(context: Context, url: String) {
        serverUrl = url
        // Save to preferences
        val prefs = context.getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("server_log_url", url).apply()
        Log.d(TAG, "Server URL updated to: $url")
    }

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences("TeuxDeuxPrefs", Context.MODE_PRIVATE)
        serverUrl = prefs.getString("server_log_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        return serverUrl
    }

    data class LogData(
        val version: String,
        val method: String,
        val status: String,
        val message: String,
        val response_time: Long = 0,
        val response_body: String? = null
    )

    fun log(method: String, status: String, message: String, responseTime: Long = 0, responseBody: String? = null) {
        // Add version prefix to message
        val messageWithVersion = "[$VERSION] $message"

        // Always log to Logcat
        Log.d(TAG, "[$method] $status - $messageWithVersion (${responseTime}ms)")

        // Only send to server if debug logging is enabled
        if (!isDebugLoggingEnabled) {
            return
        }

        // Send to PHP server asynchronously
        Thread {
            try {
                val logData = LogData(VERSION, method, status, messageWithVersion, responseTime, responseBody)
                val json = gson.toJson(logData)

                val body = RequestBody.create(
                    MediaType.parse("application/json"),
                    json
                )

                val request = Request.Builder()
                    .url(serverUrl)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                response.body()?.close()

                Log.d(TAG, "Log sent to server: ${response.code()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send log to server: ${e.message}")
            }
        }.start()
    }

    fun logSuccess(method: String, message: String, responseTime: Long, responseBody: String? = null) {
        log(method, "SUCCESS", message, responseTime, responseBody)
    }

    fun logError(method: String, message: String, responseTime: Long = 0, responseBody: String? = null) {
        log(method, "ERROR", message, responseTime, responseBody)
    }

    fun logInfo(method: String, message: String) {
        log(method, "INFO", message, 0, null)
    }
}
