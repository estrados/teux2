package com.example.kitkat

import android.util.Log
import okhttp3.*
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class CustomSslHelper {

    companion object {
        private const val TAG = "CustomSslHelper"
        private const val API_URL = "https://teuxdeux.com/api/v4/workspaces/444459/todos?since=2025-10-14&until=2025-10-20"
    }

    fun testApi(callback: (success: Boolean, message: String, responseTime: Long) -> Unit) {
        val startTime = System.currentTimeMillis()

        LogHelper.logInfo("CustomSSL", "Step 1: Starting Custom SSL test")

        try {
            LogHelper.logInfo("CustomSSL", "Step 2: Creating TrustManager (accept all certs)")
            // Create custom TrustManager that accepts all certificates
            // WARNING: This is for testing only! Not secure for production!
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            LogHelper.logInfo("CustomSSL", "Step 3: Initializing SSL context with TLS")
            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory

            LogHelper.logInfo("CustomSSL", "Step 4: Building OkHttp client")
            // Build OkHttp client with custom SSL
            val client = OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true } // Accept all hostnames
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()

            LogHelper.logInfo("CustomSSL", "Step 5: Building HTTP request")
            // Build request
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "KitKat-SSL-Test/1.0")
                .build()

            // Execute request
            Log.d(TAG, "Making request to: $API_URL")
            LogHelper.logInfo("CustomSSL", "Step 6: Executing API request to $API_URL")

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    val responseTime = System.currentTimeMillis() - startTime
                    val errorMsg = "Request failed: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    LogHelper.logError("CustomSSL", "Step 7: Connection failed - ${e.javaClass.simpleName}: ${e.message}", responseTime)
                    callback(false, errorMsg, responseTime)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseTime = System.currentTimeMillis() - startTime
                    LogHelper.logInfo("CustomSSL", "Step 7: Response received, HTTP ${response.code()}")

                    try {
                        val body = response.body()?.string() ?: ""
                        val code = response.code()

                        if (response.isSuccessful) {
                            Log.d(TAG, "Success! HTTP $code, Body length: ${body.length}")
                            LogHelper.logSuccess("CustomSSL", "Step 8: HTTP $code, Body: ${body.take(150)}...", responseTime)
                            callback(true, "HTTP $code, received ${body.length} chars", responseTime)
                        } else {
                            val errorMsg = "HTTP $code: ${body.take(200)}"
                            Log.e(TAG, errorMsg)
                            LogHelper.logError("CustomSSL", "Step 8: HTTP $code, Body: ${body.take(150)}", responseTime)
                            callback(false, errorMsg, responseTime)
                        }
                    } catch (e: Exception) {
                        val errorMsg = "Error reading response: ${e.message}"
                        Log.e(TAG, errorMsg, e)
                        LogHelper.logError("CustomSSL", "Step 8: Exception reading body: ${e.message}", responseTime)
                        callback(false, errorMsg, responseTime)
                    }
                }
            })

        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            val errorMsg = "Exception: ${e.message}"
            Log.e(TAG, errorMsg, e)
            LogHelper.logError("CustomSSL", errorMsg, responseTime)
            callback(false, errorMsg, responseTime)
        }
    }

    /**
     * Alternative approach: Try with specific TLS versions for KitKat compatibility
     */
    fun testApiWithLegacyTls(callback: (success: Boolean, message: String, responseTime: Long) -> Unit) {
        val startTime = System.currentTimeMillis()

        try {
            // Create SSL context with TLS 1.0 (supported on KitKat)
            val sslContext = SSLContext.getInstance("TLSv1")
            sslContext.init(null, null, null)

            val client = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    val responseTime = System.currentTimeMillis() - startTime
                    LogHelper.logError("CustomSSL-TLS1.0", "Failed: ${e.message}", responseTime)
                    callback(false, "TLS 1.0 failed: ${e.message}", responseTime)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseTime = System.currentTimeMillis() - startTime
                    val body = response.body()?.string() ?: ""

                    if (response.isSuccessful) {
                        LogHelper.logSuccess("CustomSSL-TLS1.0", "HTTP ${response.code()}", responseTime)
                        callback(true, "TLS 1.0 worked! HTTP ${response.code()}", responseTime)
                    } else {
                        LogHelper.logError("CustomSSL-TLS1.0", "HTTP ${response.code()}", responseTime)
                        callback(false, "HTTP ${response.code()}: ${body.take(100)}", responseTime)
                    }
                }
            })
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            LogHelper.logError("CustomSSL-TLS1.0", "Exception: ${e.message}", responseTime)
            callback(false, "Exception: ${e.message}", responseTime)
        }
    }
}
