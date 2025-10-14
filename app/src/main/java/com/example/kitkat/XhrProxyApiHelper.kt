package com.example.kitkat

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import com.google.gson.Gson

/**
 * Method 3: WebView XHR Proxy
 * Supports all HTTP methods (GET, POST, PATCH, DELETE) with headers and body
 */
class XhrProxyApiHelper(private val context: Context) : ApiHelper {

    companion object {
        private const val TAG = "XhrProxyAPI"
    }

    private val gson = Gson()

    @SuppressLint("SetJavaScriptEnabled")
    override fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        callback: (success: Boolean, statusCode: Int, response: String, responseTime: Long) -> Unit
    ) {
        val startTime = System.currentTimeMillis()

        // Check network connectivity
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        val isConnected = activeNetwork?.isConnectedOrConnecting == true

        LogHelper.logInfo("XhrProxy", "$method $url (Network: ${if (isConnected) "Connected" else "Disconnected"})")

        if (!isConnected) {
            LogHelper.logError("XhrProxy", "No network connection available", 0)
            callback(false, 0, "No network connection", 0)
            return
        }

        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                }

                webView.addJavascriptInterface(object : Any() {
                    @JavascriptInterface
                    fun onSuccess(data: String, statusCode: Int) {
                        val responseTime = System.currentTimeMillis() - startTime
                        Log.d(TAG, "Success! HTTP $statusCode")
                        LogHelper.logSuccess("XhrProxy", "HTTP $statusCode, ${data.length} chars", responseTime, data)
                        callback(true, statusCode, data, responseTime)
                    }

                    @JavascriptInterface
                    fun onError(error: String, statusCode: Int) {
                        val responseTime = System.currentTimeMillis() - startTime
                        Log.e(TAG, "Error: HTTP $statusCode")
                        LogHelper.logError("XhrProxy", "HTTP $statusCode: ${error.take(100)}", responseTime, error)
                        callback(false, statusCode, error, responseTime)
                    }

                    @JavascriptInterface
                    fun onException(error: String) {
                        val responseTime = System.currentTimeMillis() - startTime
                        Log.e(TAG, "Exception: $error")
                        LogHelper.logError("XhrProxy", "Exception: $error", responseTime)
                        callback(false, 0, error, responseTime)
                    }
                }, "Android")

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, pageUrl, favicon)
                        LogHelper.logInfo("XhrProxy", "WebView page started: $pageUrl")
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        LogHelper.logInfo("XhrProxy", "WebView page finished, executing XHR for: $url")

                        // Build headers as JavaScript object
                        val headersJs = headers.entries.joinToString(",") {
                            "'${it.key}': '${it.value}'"
                        }

                        // Escape body for JavaScript
                        val bodyJs = body?.let {
                            "'${it.replace("'", "\\'")}'"
                        } ?: "null"

                        val js = """
                            (function() {
                                var xhr = new XMLHttpRequest();
                                xhr.open('$method', '$url', true);

                                // Set headers
                                var headers = {$headersJs};
                                for (var key in headers) {
                                    xhr.setRequestHeader(key, headers[key]);
                                }

                                xhr.onload = function() {
                                    if (xhr.status >= 200 && xhr.status < 300) {
                                        Android.onSuccess(xhr.responseText, xhr.status);
                                    } else {
                                        Android.onError(xhr.responseText || xhr.statusText, xhr.status);
                                    }
                                };

                                xhr.onerror = function() {
                                    var errorMsg = 'Network error - Status: ' + xhr.status + ', ReadyState: ' + xhr.readyState + ', URL: $url';
                                    Android.onException(errorMsg);
                                };

                                xhr.ontimeout = function() {
                                    Android.onException('Request timeout after 30s - URL: $url');
                                };

                                xhr.timeout = 30000;

                                try {
                                    xhr.send($bodyJs);
                                } catch (e) {
                                    Android.onException('XHR send failed: ' + e.message);
                                }
                            })();
                        """.trimIndent()

                        view?.evaluateJavascript(js, null)
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        LogHelper.logInfo("XhrProxy", "SSL Error: ${error?.toString()}, proceeding...")
                        handler?.proceed()
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        val responseTime = System.currentTimeMillis() - startTime
                        LogHelper.logError("XhrProxy", "Error $errorCode: $description", responseTime)
                    }
                }

                webView.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d(TAG, "JS Console: ${consoleMessage?.message()}")
                        return true
                    }
                }

                // Load with base URL to avoid CORS
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>API Proxy</title></head>
                    <body>Loading...</body>
                    </html>
                """.trimIndent()

                webView.loadDataWithBaseURL("https://teuxdeux.com/", html, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                val responseTime = System.currentTimeMillis() - startTime
                LogHelper.logError("XhrProxy", "Exception: ${e.message}", responseTime)
                callback(false, 0, e.message ?: "Unknown error", responseTime)
            }
        }
    }
}
