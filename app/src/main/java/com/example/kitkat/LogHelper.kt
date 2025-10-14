package com.example.kitkat

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
    private const val SERVER_URL = "http://192.168.1.11:8080"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class LogData(
        val method: String,
        val status: String,
        val message: String,
        val response_time: Long = 0
    )

    fun log(method: String, status: String, message: String, responseTime: Long = 0) {
        // Log to Logcat
        Log.d(TAG, "[$method] $status - $message (${responseTime}ms)")

        // Send to PHP server asynchronously
        Thread {
            try {
                val logData = LogData(method, status, message, responseTime)
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

    fun logSuccess(method: String, message: String, responseTime: Long) {
        log(method, "SUCCESS", message, responseTime)
    }

    fun logError(method: String, message: String, responseTime: Long = 0) {
        log(method, "ERROR", message, responseTime)
    }

    fun logInfo(method: String, message: String) {
        log(method, "INFO", message, 0)
    }
}
