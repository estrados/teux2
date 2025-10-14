package com.example.kitkat

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*

class WebViewApiHelper(private val context: Context) {

    companion object {
        private const val TAG = "WebViewApiHelper"
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun testApi(callback: (success: Boolean, message: String, responseTime: Long) -> Unit) {
        val startTime = System.currentTimeMillis()

        LogHelper.logInfo("WebView", "Step 1: Starting WebView (direct URL load)")

        Handler(Looper.getMainLooper()).post {
            try {
                LogHelper.logInfo("WebView", "Step 2: Creating WebView")
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true // Enable to extract content
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                }
                LogHelper.logInfo("WebView", "Step 3: WebView created successfully")

                LogHelper.logInfo("WebView", "Step 4: Setting up WebViewClient")
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val responseTime = System.currentTimeMillis() - startTime
                        LogHelper.logInfo("WebView", "Step 6: Page finished loading!")

                        // Extract page content (the JSON response)
                        view?.evaluateJavascript("document.body.innerText") { result ->
                            val content = result?.replace("\"", "")?.replace("\\n", "\n") ?: ""
                            if (content.isNotEmpty() && content != "null") {
                                Log.d(TAG, "Success! Got JSON: $content")
                                LogHelper.logSuccess("WebView", "Step 7: JSON received: ${content.take(200)}...", responseTime)
                                callback(true, "Got ${content.length} chars JSON", responseTime)
                            } else {
                                LogHelper.logError("WebView", "Step 7: Empty or null response", responseTime)
                                callback(false, "Empty response", responseTime)
                            }
                        }
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        Log.w(TAG, "SSL Error: ${error?.toString()}")
                        LogHelper.logInfo("WebView", "SSL Error: ${error?.toString()}, proceeding...")
                        // For testing, proceed despite SSL errors
                        handler?.proceed()
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        val responseTime = System.currentTimeMillis() - startTime
                        val errorMsg = "Error $errorCode: $description"
                        Log.e(TAG, errorMsg)
                        LogHelper.logError("WebView", "Step X: $errorMsg", responseTime)
                        callback(false, errorMsg, responseTime)
                    }
                }

                LogHelper.logInfo("WebView", "Step 5: Loading API URL with Authorization header")
                // Load URL with custom headers
                val headers = mapOf("Authorization" to Config.AUTH_TOKEN)
                webView.loadUrl(Config.API_URL, headers)
            } catch (e: Exception) {
                val responseTime = System.currentTimeMillis() - startTime
                LogHelper.logError("WebView", "Exception during setup: ${e.message}", responseTime)
                callback(false, "Setup exception: ${e.message}", responseTime)
            }
        }
    }

    /**
     * Alternative approach: Load TeuxDeux login page and capture cookies
     * This would be used in production to get the auth token
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun loginAndCaptureToken(callback: (token: String?) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    // Check if we're on the home page (login successful)
                    if (url?.contains("/home") == true) {
                        val cookieManager = CookieManager.getInstance()
                        val cookies = cookieManager.getCookie(url)

                        // Parse cookies to extract _txdxtxdx_token
                        cookies?.split(";")?.forEach { cookie ->
                            val parts = cookie.trim().split("=")
                            if (parts.size == 2 && parts[0] == "_txdxtxdx_token") {
                                val token = parts[1]
                                Log.d(TAG, "Token captured: ${token.take(20)}...")
                                LogHelper.logInfo("WebView", "Auth token captured successfully")
                                callback(token)
                                return
                            }
                        }
                    }
                }
            }

            webView.loadUrl("https://teuxdeux.com/login")
        }
    }
}
