package com.example.kitkat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo

/**
 * Monitors network connectivity and notifies listeners of changes.
 * Compatible with API 19 (KitKat).
 */
class NetworkMonitor(private val context: Context) {

    private var isCurrentlyOnline: Boolean = false
    private val listeners = mutableListOf<(Boolean) -> Unit>()
    private var receiver: BroadcastReceiver? = null

    /**
     * Check if device is currently online
     */
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }

    /**
     * Start monitoring network changes
     */
    fun startMonitoring() {
        isCurrentlyOnline = isOnline()

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val wasOnline = isCurrentlyOnline
                isCurrentlyOnline = isOnline()

                // Only notify if state changed
                if (wasOnline != isCurrentlyOnline) {
                    LogHelper.logInfo("NETWORK", "Network state changed: ${if (isCurrentlyOnline) "ONLINE" else "OFFLINE"}")
                    notifyListeners(isCurrentlyOnline)
                }
            }
        }

        // Register for connectivity changes (API 19 compatible)
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        context.registerReceiver(receiver, filter)

        LogHelper.logInfo("NETWORK", "NetworkMonitor started - Current state: ${if (isCurrentlyOnline) "ONLINE" else "OFFLINE"}")
    }

    /**
     * Stop monitoring network changes
     */
    fun stopMonitoring() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                LogHelper.logError("NETWORK", "Error unregistering receiver: ${e.message}", 0, null)
            }
        }
        receiver = null
        LogHelper.logInfo("NETWORK", "NetworkMonitor stopped")
    }

    /**
     * Add a listener for network state changes
     */
    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Remove a listener
     */
    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Notify all listeners of network state change
     */
    private fun notifyListeners(isOnline: Boolean) {
        listeners.forEach { it(isOnline) }
    }
}
