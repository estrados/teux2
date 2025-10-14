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

    // Change this to your server IP (can be any 192.168.1.* address)
    // Then update server/start.sh with the same IP
    private const val SERVER_URL = "http://192.168.1.25:8080"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Flag to control server logging
    @Volatile
    private var isDebugLoggingEnabled = true

    fun setDebugLoggingEnabled(context: Context, enabled: Boolean) {
        isDebugLoggingEnabled = enabled
        Log.d(TAG, "Debug logging to server: ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    data class LogData(
        val method: String,
        val status: String,
        val message: String,
        val response_time: Long = 0,
        val response_body: String? = null
    )

    fun log(method: String, status: String, message: String, responseTime: Long = 0, responseBody: String? = null) {
        // Always log to Logcat
        Log.d(TAG, "[$method] $status - $message (${responseTime}ms)")

        // Only send to server if debug logging is enabled
        if (!isDebugLoggingEnabled) {
            return
        }

        // Send to PHP server asynchronously
        Thread {
            try {
                val logData = LogData(method, status, message, responseTime, responseBody)
                val json = gson.toJson(logData)

                val body = RequestBody.create(
                    MediaType.parse("application/json"),
                    json
                )

                val request = Request.Builder()
                    .url(SERVER_URL)
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
