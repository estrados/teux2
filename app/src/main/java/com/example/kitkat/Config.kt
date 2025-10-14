package com.example.kitkat

/**
 * Configuration for API testing
 *
 * IMPORTANT: This is for testing only!
 * In production, store tokens securely using EncryptedSharedPreferences
 */
object Config {
    // TeuxDeux API Authorization token
    const val AUTH_TOKEN = "Bearer F7RB2S17at9HXbVv4K2v0yomuO2fX0wSNJ654flF0m"

    // API Base URL
    const val BASE_URL = "https://teuxdeux.com/api/v4"

    // Workspace ID
    const val WORKSPACE_ID = 444459

    // Test URLs (for compatibility)
    const val API_URL = "$BASE_URL/workspaces/$WORKSPACE_ID/todos?since=2025-10-14&until=2025-10-20"
}
