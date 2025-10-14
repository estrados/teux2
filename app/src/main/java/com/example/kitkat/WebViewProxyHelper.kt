package com.example.kitkat

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*

/**
 * Uses WebView to handle HTTPS/SSL and acts as a proxy for API calls.
 * This approach leverages WebView's built-in SSL handling which works better on old devices.
 */
class WebViewProxyHelper(private val context: Context) {

    companion object {
        private const val TAG = "WebViewProxyHelper"
        private const val API_URL = "https://teuxdeux.com/api/v4/workspaces/444459/todos?since=2025-10-14&until=2025-10-20"
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun testApi(callback: (success: Boolean, message: String, responseTime: Long) -> Unit) {
        val startTime = System.currentTimeMillis()

        LogHelper.logInfo("WebViewProxy", "Step 1: Starting WebView Proxy test")

        Handler(Looper.getMainLooper()).post {
            try {
                LogHelper.logInfo("WebViewProxy", "Step 2: Creating WebView")
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    // mixedContentMode not available on API 19, skip it
                }
                LogHelper.logInfo("WebViewProxy", "Step 3: WebView created successfully")

                LogHelper.logInfo("WebViewProxy", "Step 4: Adding JavaScript interface")
                // Add JavaScript interface to receive the result
                webView.addJavascriptInterface(object : Any() {
                    @JavascriptInterface
                    fun onSuccess(data: String, statusCode: Int) {
                        val responseTime = System.currentTimeMillis() - startTime
                        Log.d(TAG, "Success! HTTP $statusCode, Data length: ${data.length}")
                        LogHelper.logSuccess("WebViewProxy", "Step 8: HTTP $statusCode, Body: ${data.take(150)}...", responseTime)
                        callback(true, "HTTP $statusCode, ${data.length} chars", responseTime)
                    }

                    @JavascriptInterface
                    fun onError(error: String, statusCode: Int) {
                        val responseTime = System.currentTimeMillis() - startTime
                        Log.e(TAG, "Error: HTTP $statusCode - $error")
                        LogHelper.logError("WebViewProxy", "Step 8: HTTP $statusCode: ${error.take(150)}", responseTime)
                        callback(false, "HTTP $statusCode: $error", responseTime)
                    }

                    @JavascriptInterface
                    fun onException(error: String) {
                        val responseTime = System.currentTimeMillis() - startTime
                        Log.e(TAG, "Exception: $error")
                        LogHelper.logError("WebViewProxy", "Step 7/8: Exception: $error", responseTime)
                        callback(false, "Exception: $error", responseTime)
                    }
                }, "Android")

                LogHelper.logInfo("WebViewProxy", "Step 5: Setting up WebViewClient")
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        LogHelper.logInfo("WebViewProxy", "Step 6: Page loaded, executing XMLHttpRequest")

                        // Create a more robust fetch implementation
                        val js = """
                        (function() {
                            console.log('Starting API request via WebView proxy...');

                            console.log('Step 7: Creating XMLHttpRequest');
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', '$API_URL', true);
                            xhr.setRequestHeader('Accept', 'application/json');

                            xhr.onload = function() {
                                console.log('Step 8: XHR onload, status: ' + xhr.status);
                                console.log('Response preview: ' + xhr.responseText.substring(0, 100));
                                if (xhr.status >= 200 && xhr.status < 300) {
                                    Android.onSuccess(xhr.responseText, xhr.status);
                                } else {
                                    Android.onError(xhr.responseText || xhr.statusText, xhr.status);
                                }
                            };

                            xhr.onerror = function() {
                                console.log('XHR onerror');
                                Android.onException('Network error or CORS issue');
                            };

                            xhr.ontimeout = function() {
                                console.log('XHR timeout');
                                Android.onException('Request timeout');
                            };

                            xhr.timeout = 30000; // 30 second timeout

                            try {
                                console.log('Sending XHR request...');
                                xhr.send();
                            } catch (e) {
                                console.log('XHR send exception: ' + e.message);
                                Android.onException('XHR send failed: ' + e.message);
                            }
                        })();
                    """.trimIndent()

                        webView.evaluateJavascript(js, null)
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        Log.w(TAG, "SSL Error received: ${error?.toString()}")
                        LogHelper.logInfo("WebViewProxy", "SSL Error encountered: ${error?.toString()}, proceeding anyway")
                        // For testing purposes, proceed despite SSL errors
                        // In production, you should handle this more carefully
                        handler?.proceed()
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        val responseTime = System.currentTimeMillis() - startTime
                        val errorMsg = "WebView Error $errorCode: $description"
                        Log.e(TAG, errorMsg)
                        LogHelper.logError("WebViewProxy", "Step X: $errorMsg", responseTime)
                        callback(false, errorMsg, responseTime)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            val responseTime = System.currentTimeMillis() - startTime
                            val errorMsg = "Resource Error: ${error?.description}"
                            Log.e(TAG, errorMsg)
                            LogHelper.logError("WebViewProxy", "Step X: $errorMsg", responseTime)
                        }
                    }
                }

                webView.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        val msg = consoleMessage?.message() ?: ""
                        Log.d(TAG, "Console: $msg")
                        LogHelper.logInfo("WebViewProxy", "JS Console: $msg")
                        return true
                    }
                }

                LogHelper.logInfo("WebViewProxy", "Step 6: Loading with base URL to avoid CORS")
                // Load with base URL set to teuxdeux.com to avoid CORS issues
                // This makes the origin "https://teuxdeux.com" instead of "null"
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
                LogHelper.logError("WebViewProxy", "Exception during setup: ${e.message}", responseTime)
                callback(false, "Setup exception: ${e.message}", responseTime)
            }
        }
    }

    /**
     * Alternative: Load the HTML file with embedded fetch script
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun testApiWithHtmlProxy(callback: (success: Boolean, message: String, responseTime: Long) -> Unit) {
        val startTime = System.currentTimeMillis()

        // Create HTML content with embedded JavaScript
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>API Proxy</title>
            </head>
            <body>
                <h3>WebView API Proxy</h3>
                <div id="status">Loading...</div>
                <script>
                    (async function() {
                        try {
                            const response = await fetch('$API_URL', {
                                method: 'GET',
                                headers: {
                                    'Accept': 'application/json'
                                }
                            });

                            const data = await response.text();

                            if (response.ok) {
                                document.getElementById('status').textContent = 'Success!';
                                Android.onSuccess(data, response.status);
                            } else {
                                document.getElementById('status').textContent = 'Error: ' + response.status;
                                Android.onError(data, response.status);
                            }
                        } catch (error) {
                            document.getElementById('status').textContent = 'Exception: ' + error.message;
                            Android.onException(error.toString());
                        }
                    })();
                </script>
            </body>
            </html>
        """.trimIndent()

        Handler(Looper.getMainLooper()).post {
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
            }

            webView.addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun onSuccess(data: String, statusCode: Int) {
                    val responseTime = System.currentTimeMillis() - startTime
                    LogHelper.logSuccess("WebViewProxy-HTML", "HTTP $statusCode", responseTime)
                    callback(true, "HTTP $statusCode (HTML method)", responseTime)
                }

                @JavascriptInterface
                fun onError(error: String, statusCode: Int) {
                    val responseTime = System.currentTimeMillis() - startTime
                    LogHelper.logError("WebViewProxy-HTML", "HTTP $statusCode", responseTime)
                    callback(false, "HTTP $statusCode", responseTime)
                }

                @JavascriptInterface
                fun onException(error: String) {
                    val responseTime = System.currentTimeMillis() - startTime
                    LogHelper.logError("WebViewProxy-HTML", error, responseTime)
                    callback(false, error, responseTime)
                }
            }, "Android")

            webView.webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }

            webView.loadDataWithBaseURL("https://teuxdeux.com/", htmlContent, "text/html", "UTF-8", null)
        }
    }
}
