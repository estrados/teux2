package com.example.kitkat

import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Reusable API helpers
    private lateinit var apiHelper: ApiHelper

    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var debugLoggingCheckbox: CheckBox

    companion object {
        private const val PREFS_NAME = "TeuxDeuxPrefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEBUG_LOGGING = "debug_logging"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize API helper
        apiHelper = ApiHelperFactory.create(this)

        // ScrollView wrapper
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Form layout - compact spacing
        val formLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Username input
        formLayout.addView(TextView(this).apply {
            text = "Username"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })

        usernameInput = EditText(this).apply {
            hint = "Enter your username"
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        formLayout.addView(usernameInput)

        // Password input
        formLayout.addView(TextView(this).apply {
            text = "Password"
            textSize = 14f
            setPadding(0, 16, 0, 8)
        })

        passwordInput = EditText(this).apply {
            hint = "Enter your password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        formLayout.addView(passwordInput)

        // Debug logging checkbox
        debugLoggingCheckbox = CheckBox(this).apply {
            text = "Enable debug logging to server"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            setOnCheckedChangeListener { _, isChecked ->
                saveDebugLoggingPreference(isChecked)
            }
        }
        formLayout.addView(debugLoggingCheckbox)

        // Login button
        loginButton = Button(this).apply {
            text = "Login"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            setOnClickListener { performLogin() }
        }
        formLayout.addView(loginButton)

        scrollView.addView(formLayout)
        setContentView(scrollView)

        // Load saved credentials
        loadSavedCredentials()

        LogHelper.logInfo("APP", "MainActivity started - Login screen")
    }

    private fun loadSavedCredentials() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUsername = prefs.getString(KEY_USERNAME, "estrada")
        val savedPassword = prefs.getString(KEY_PASSWORD, "temporary2")
        val savedAuthToken = prefs.getString(KEY_AUTH_TOKEN, "")
        val debugLoggingEnabled = prefs.getBoolean(KEY_DEBUG_LOGGING, true) // Default: enabled

        usernameInput.setText(savedUsername)
        passwordInput.setText(savedPassword)
        debugLoggingCheckbox.isChecked = debugLoggingEnabled

        // Update LogHelper with current preference
        LogHelper.setDebugLoggingEnabled(this, debugLoggingEnabled)
    }

    private fun saveDebugLoggingPreference(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_DEBUG_LOGGING, enabled)
            apply()
        }
        LogHelper.setDebugLoggingEnabled(this, enabled)
    }

    private fun saveCredentials(username: String, password: String, authToken: String?, refreshToken: String?) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            authToken?.let { putString(KEY_AUTH_TOKEN, it) }
            refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
            apply()
        }
    }

    private fun performLogin() {
        LogHelper.logInfo("LOGIN-STEP", "1. performLogin() called")

        // Disable login button during login
        loginButton.isEnabled = false
        loginButton.text = "Logging in..."

        try {
            LogHelper.logInfo("LOGIN-STEP", "2. Getting username/password from inputs")
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            LogHelper.logInfo("LOGIN-STEP", "3. Got credentials - username: $username")

            if (username.isEmpty() || password.isEmpty()) {
                LogHelper.logError("LOGIN-STEP", "4. Empty credentials", 0, null)
                loginButton.isEnabled = true
                loginButton.text = "Login"
                android.widget.Toast.makeText(this, "Username and password required", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            LogHelper.logInfo("LOGIN-STEP", "5. Clearing cookies (KitKat sync method)")

            // Use deprecated but KitKat-compatible synchronous method
            @Suppress("DEPRECATION")
            val cookieManager = CookieManager.getInstance()
            @Suppress("DEPRECATION")
            cookieManager.removeAllCookie()

            LogHelper.logInfo("LOGIN-STEP", "6. Cookies cleared, fetching CSRF token")

            val url = "https://teuxdeux.com/login"
            LogHelper.logInfo("LOGIN-STEP", "7. Calling apiHelper.request for URL: $url")

            apiHelper.request("GET", url, emptyMap(), null) { csrfSuccess, statusCode, response, responseTime ->
                LogHelper.logInfo("LOGIN-STEP", "8. CSRF request callback - success: $csrfSuccess, status: $statusCode")

                runOnUiThread {
                    LogHelper.logInfo("LOGIN-STEP", "9. In runOnUiThread after CSRF request")

                    try {
                        // Check if we're already logged in (redirected to home or got app page)
                        val isAlreadyLoggedIn = response.contains("app-root") || response.contains("feature--todo_details")

                        if (isAlreadyLoggedIn) {
                            LogHelper.logInfo("LOGIN-STEP", "10. Already logged in, extracting existing tokens")

                            val cookies = CookieManager.getInstance().getCookie("https://teuxdeux.com")
                            LogHelper.logInfo("LOGIN-STEP", "11. Got cookies: ${cookies?.take(100)}")

                            if (cookies != null) {
                                val cookieMap = parseCookies(cookies)

                                val authToken = cookieMap["_txdxtxdx_token"]
                                val refreshToken = cookieMap["refresh_token"]

                                LogHelper.logInfo("LOGIN-STEP", "12. Parsed - auth: ${authToken?.take(20)}, refresh: ${refreshToken?.take(20)}")

                                LogHelper.logInfo("LOGIN-STEP", "13. Saving credentials to SharedPreferences")
                                saveCredentials(username, password, authToken, refreshToken)

                                val cookiesSummary = buildString {
                                    append("Auth Token: ${authToken ?: "N/A"}\n")
                                    append("Refresh Token: ${refreshToken ?: "N/A"}\n")
                                }
                                LogHelper.logSuccess("LOGIN", "Already logged in - tokens saved", responseTime, cookiesSummary)
                                LogHelper.logInfo("LOGIN-STEP", "14. Login flow completed (reused existing session)")

                                // Redirect to home
                                onLoginSuccess()
                            } else {
                                LogHelper.logError("LOGIN-STEP", "15. No cookies found", responseTime, response.take(500))
                                showError("Login failed: No cookies")
                            }
                            return@runOnUiThread
                        }

                        if (csrfSuccess) {
                            LogHelper.logInfo("LOGIN-STEP", "16. CSRF page loaded successfully")

                            LogHelper.logInfo("LOGIN-STEP", "17. Parsing CSRF token from response")
                            val csrfToken = parseCsrfToken(response)

                            if (csrfToken != null) {
                                LogHelper.logInfo("LOGIN-STEP", "18. CSRF token extracted: ${csrfToken.take(20)}...")
                                LogHelper.logSuccess("LOGIN", "CSRF token extracted", responseTime, csrfToken.take(50))

                                LogHelper.logInfo("LOGIN-STEP", "19. About to call loginWithCsrf")
                                loginWithCsrf(username, password, csrfToken)
                            } else {
                                LogHelper.logError("LOGIN-STEP", "20. CSRF token parsing failed", responseTime, response.take(500))
                                showError("Failed to extract CSRF token")
                            }
                        } else {
                            LogHelper.logError("LOGIN-STEP", "21. CSRF request failed - status: $statusCode", responseTime, response.take(500))
                            showError("Failed to get CSRF token: HTTP $statusCode")
                        }
                    } catch (e: Exception) {
                        LogHelper.logError("LOGIN-STEP", "22. Exception in CSRF callback: ${e.message}", responseTime, e.stackTraceToString())
                        showError("Error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            LogHelper.logError("LOGIN-STEP", "99. Exception in performLogin main try: ${e.message}", 0, e.stackTraceToString())
            showError("Error starting login: ${e.message}")
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            loginButton.isEnabled = true
            loginButton.text = "Login"
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun onLoginSuccess() {
        runOnUiThread {
            LogHelper.logInfo("LOGIN", "Redirecting to HomeActivity")

            // Start HomeActivity
            val intent = android.content.Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish() // Close login activity
        }
    }

    private fun loginWithCsrf(username: String, password: String, csrfToken: String) {
        LogHelper.logInfo("LOGIN-STEP", "30. loginWithCsrf() called with csrf: ${csrfToken.take(20)}...")

        try {
            LogHelper.logInfo("LOGIN-STEP", "31. Building POST request")
            val url = "https://teuxdeux.com/login"
            val headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Origin" to "https://teuxdeux.com",
                "Referer" to "https://teuxdeux.com/login"
            )
            val body = "csrf_token=${csrfToken}&username=${username}&password=${password}"
            LogHelper.logInfo("LOGIN-STEP", "32. Request built, calling apiHelper.request POST")

            apiHelper.request("POST", url, headers, body) { success, statusCode, response, responseTime ->
                LogHelper.logInfo("LOGIN-STEP", "33. POST callback - success: $success, status: $statusCode")

                runOnUiThread {
                    LogHelper.logInfo("LOGIN-STEP", "34. In runOnUiThread after POST")

                    try {
                        if (success || statusCode == 302) {
                            LogHelper.logInfo("LOGIN-STEP", "35. Login succeeded, extracting cookies")

                            val cookies = CookieManager.getInstance().getCookie("https://teuxdeux.com")
                            LogHelper.logInfo("LOGIN-STEP", "36. Got cookies: ${cookies?.take(100)}")

                            if (cookies != null) {
                                LogHelper.logInfo("LOGIN-STEP", "37. Parsing cookies")
                                val cookieMap = parseCookies(cookies)

                                val authToken = cookieMap["_txdxtxdx_token"]
                                val refreshToken = cookieMap["refresh_token"]
                                val rememberToken = cookieMap["remember_token"]

                                LogHelper.logInfo("LOGIN-STEP", "38. Parsed - auth: ${authToken?.take(20)}, refresh: ${refreshToken?.take(20)}")

                                LogHelper.logInfo("LOGIN-STEP", "39. Saving credentials to SharedPreferences")
                                saveCredentials(username, password, authToken, refreshToken)

                                val cookiesSummary = buildString {
                                    append("Auth Token: ${authToken ?: "N/A"}\n")
                                    append("Refresh Token: ${refreshToken ?: "N/A"}\n")
                                    append("Remember Token: ${rememberToken ?: "N/A"}")
                                }
                                LogHelper.logSuccess("LOGIN", "Login successful - tokens saved", responseTime, cookiesSummary)
                                LogHelper.logInfo("LOGIN-STEP", "40. Login flow completed successfully")

                                // Redirect to home
                                onLoginSuccess()
                            } else {
                                LogHelper.logError("LOGIN-STEP", "41. No cookies in response", responseTime, response.take(500))
                                showError("Login failed: No cookies received")
                            }
                        } else {
                            LogHelper.logError("LOGIN-STEP", "42. Login failed - status: $statusCode", responseTime, response.take(500))
                            showError("Login failed: HTTP $statusCode")
                        }
                    } catch (e: Exception) {
                        LogHelper.logError("LOGIN-STEP", "43. Exception in POST callback: ${e.message}", responseTime, e.stackTraceToString())
                        showError("Error in login response: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            LogHelper.logError("LOGIN-STEP", "44. Exception in loginWithCsrf: ${e.message}", 0, e.stackTraceToString())
            showError("Error sending login request: ${e.message}")
        }
    }

    private fun parseCsrfToken(html: String): String? {
        // Parse: <input type="hidden" name="csrf_token" value="...">
        val regex = """<input[^>]*name="csrf_token"[^>]*value="([^"]+)"""".toRegex()
        val match = regex.find(html)
        return match?.groupValues?.get(1)
    }

    private fun parseCookies(cookieString: String): Map<String, String> {
        return cookieString.split("; ").mapNotNull { cookie ->
            val parts = cookie.split("=", limit = 2)
            if (parts.size == 2) {
                parts[0] to parts[1]
            } else null
        }.toMap()
    }
}
