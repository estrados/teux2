package com.example.kitkat

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create UI programmatically (no XML layout needed)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        val title = TextView(this).apply {
            text = "KitKat SSL/HTTPS Test"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        // Method 1 Button - WebView API
        val btnWebView = Button(this).apply {
            text = "Method 1: WebView API"
            setOnClickListener {
                appendLog("Testing Method 1: WebView API...")
                testWebViewApi()
            }
        }
        layout.addView(btnWebView)

        // Method 2 Button - Custom SSL
        val btnCustomSsl = Button(this).apply {
            text = "Method 2: Custom SSL (OkHttp)"
            setOnClickListener {
                appendLog("Testing Method 2: Custom SSL...")
                testCustomSsl()
            }
        }
        layout.addView(btnCustomSsl)

        // Method 3 Button - WebView Proxy
        val btnWebViewProxy = Button(this).apply {
            text = "Method 3: WebView Proxy"
            setOnClickListener {
                appendLog("Testing Method 3: WebView as SSL Proxy...")
                testWebViewProxy()
            }
        }
        layout.addView(btnWebViewProxy)

        // Clear Button
        val btnClear = Button(this).apply {
            text = "Clear Logs"
            setOnClickListener {
                logTextView.text = ""
            }
        }
        layout.addView(btnClear)

        // ScrollView with TextView for logs
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = 32
            }
        }

        logTextView = TextView(this).apply {
            text = "Logs will appear here...\n"
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        scrollView.addView(logTextView)
        layout.addView(scrollView)

        setContentView(layout)

        // Initial log
        appendLog("App started. Server: http://192.168.1.11:8080")
        LogHelper.logInfo("APP", "MainActivity started")
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
            logTextView.append("[$timestamp] $message\n")
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun testWebViewApi() {
        try {
            val helper = WebViewApiHelper(this)
            helper.testApi { success, message, responseTime ->
                appendLog(if (success) "✓ SUCCESS: $message" else "✗ ERROR: $message")
            }
        } catch (e: Exception) {
            appendLog("✗ Exception: ${e.message}")
            LogHelper.logError("WebView", "Exception: ${e.message ?: "Unknown error"}")
        }
    }

    private fun testCustomSsl() {
        Thread {
            try {
                val helper = CustomSslHelper()
                helper.testApi { success, message, responseTime ->
                    appendLog(if (success) "✓ SUCCESS: $message" else "✗ ERROR: $message")
                }
            } catch (e: Exception) {
                appendLog("✗ Exception: ${e.message}")
                LogHelper.logError("CustomSSL", "Exception: ${e.message}")
            }
        }.start()
    }

    private fun testWebViewProxy() {
        try {
            val helper = WebViewProxyHelper(this)
            helper.testApi { success, message, responseTime ->
                appendLog(if (success) "✓ SUCCESS: $message" else "✗ ERROR: $message")
            }
        } catch (e: Exception) {
            appendLog("✗ Exception: ${e.message}")
            LogHelper.logError("WebViewProxy", "Exception: ${e.message ?: "Unknown error"}")
        }
    }
}
